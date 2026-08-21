package com.minion.scaffold.feature.qrscan.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.common.dispatcher.DefaultDispatcher
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.navigation.QrCreateRoute
import com.minion.scaffold.core.navigation.QrScanRoute
import com.minion.scaffold.core.navigation.UrlCreateRoute
import com.minion.scaffold.core.navigation.VCardCreateRoute
import com.minion.scaffold.core.navigation.WifiCreateRoute
import com.minion.scaffold.core.navigation.ScanPurpose
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.feature.qrscan.data.ImageBarcodeDecoder
import com.minion.scaffold.feature.qrscan.data.ImageDecodeResult
import com.minion.scaffold.feature.qrscan.domain.DecodeScannedPayloadUseCase
import com.minion.scaffold.feature.qrscan.domain.ScanResult
import com.minion.scaffold.feature.qrscan.domain.ScannedContent
import com.minion.scaffold.feature.qrscan.domain.format
import com.minion.scaffold.feature.qrscan.domain.compare.CompareScannedContentUseCase
import com.minion.scaffold.feature.qrscan.domain.compare.DiffPayloadCharactersUseCase
import com.minion.scaffold.feature.qrscan.domain.compare.QrComparison
import com.minion.scaffold.feature.qrscan.domain.export.ExportPaymentJsonUseCase
import com.minion.scaffold.feature.qrscan.domain.export.PaymentJsonExport
import com.minion.scaffold.feature.qrscan.domain.export.PaymentSchemaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
internal class QrScanViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val decodeScannedPayload: DecodeScannedPayloadUseCase,
    private val imageBarcodeDecoder: ImageBarcodeDecoder,
    private val compareScannedContent: CompareScannedContentUseCase,
    private val diffPayloadCharacters: DiffPayloadCharactersUseCase,
    private val exportPaymentJson: ExportPaymentJsonUseCase,
    private val schemaRepository: PaymentSchemaRepository,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : MviViewModel<QrScanState, QrScanIntent, QrScanEffect>(QrScanState()) {

    /**
     * Why this screen was opened, read from its own route.
     *
     * The scanner behaves identically in both purposes right up to the moment a payload decodes;
     * only what happens next differs. Reading it here rather than taking it as a screen parameter
     * keeps the decision in the one place that knows a decoded succeeded.
     */
    private val purpose = savedStateHandle
        .get<ScanPurpose>(QrScanRoute.ARG_PURPOSE)
        ?: ScanPurpose.Inspect

    init {
        // A `MutableStateFlow` survives rotation but not process death, and the failure screen is
        // now somewhere a user sits for minutes and switches away from to compare payloads —
        // exactly when Android reclaims the process. Restored the same way `:feature:ocr` keeps its
        // extracted text.
        savedStateHandle.get<String>(KEY_PAYLOAD)
            ?.takeIf { it.isNotEmpty() }
            ?.let { restored -> reduce { copy(manualPayload = restored) } }

        restoreComparison()

        // Watched rather than read once: the settings screen sits in front of this one, and coming
        // back from it with a different schema must change what the share sheet says it will send.
        schemaRepository.activeSchema
            .onEach { schema ->
                reduce { copy(schemaSource = schema.source, schemaLabel = schema.label) }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: QrScanIntent) {
        when (intent) {
            is QrScanIntent.ManualPayloadChanged -> rememberPayload(intent.payload)
            is QrScanIntent.PayloadSubmitted -> decode(intent.payload)
            is QrScanIntent.ImagePicked -> decodeImage(intent.uri)

            QrScanIntent.Cleared -> {
                rememberPayload("")
                reduce { copy(content = QrScanState.ContentState.Idle) }
            }

            // Deliberately keeps the payload — see the intent's own note.
            QrScanIntent.Dismissed -> reduce {
                copy(content = QrScanState.ContentState.Idle)
            }

            is QrScanIntent.PermissionResult -> reduce {
                copy(cameraPermission = intent.toPermissionState())
            }

            is QrScanIntent.ModeChanged -> reduce { copy(mode = intent.mode) }

            // Torch state survives a rotation because it is a thing the user switched on, not a
            // property of the camera session — coming back to a dark viewfinder after turning the
            // phone would read as the torch breaking.
            QrScanIntent.TorchToggled -> reduce { copy(torchEnabled = !torchEnabled) }

            QrScanIntent.AppSettingsRequested -> viewModelScope.launch {
                emitEffect(QrScanEffect.OpenAppSettings)
            }

            QrScanIntent.EditRequested -> withContent {
                QrScanEffect.EditPayload(it.editRoute())
            }

            QrScanIntent.OpenLinkRequested -> scannedContent
                .let { it as? ScannedContent.Web }
                ?.let { web -> emit(QrScanEffect.OpenLink(web.url)) }

            QrScanIntent.AddContactRequested -> scannedContent
                .let { it as? ScannedContent.Contact }
                ?.let { contact -> emit(QrScanEffect.AddContact(contact.card)) }

            QrScanIntent.CopyReportRequested -> withContent(QrScanEffect::CopyReport)

            is QrScanIntent.CopyValueRequested -> viewModelScope.launch {
                emitEffect(QrScanEffect.CopyText(intent.text))
            }

            QrScanIntent.ShareReportRequested -> withContent(QrScanEffect::ShareReport)

            QrScanIntent.ShareJsonRequested -> shareJson()

            QrScanIntent.CompareRequested -> pinBaseline()
            QrScanIntent.CompareCancelled -> cancelComparison()
            QrScanIntent.CompareRescanRequested -> rearmForCandidate()
            QrScanIntent.CompareSwapped -> swapComparison()
            QrScanIntent.RawDiffRequested -> computeRawDiff()

            QrScanIntent.CopyComparisonRequested ->
                withComparison(QrScanEffect::CopyComparison)

            QrScanIntent.ShareComparisonRequested ->
                withComparison(QrScanEffect::ShareComparison)
        }
    }

    /**
     * Decodes [payload], unless the screen is already busy or done.
     *
     * The guard is what stops a live camera from re-decoding the same QR on every frame once it
     * has succeeded — the analyzer runs at the preview's frame rate and would otherwise push the
     * same payload thirty times a second. Clearing is the deliberate act that reopens the screen
     * to input.
     *
     * Synchronous, and not dispatched: parsing is one pass over a few hundred characters, so
     * hopping threads would cost more than the work.
     */
    private fun decode(payload: String) {
        if (currentState.content.isBusyOrDone) return

        val baseline = currentState.baseline
        if (baseline != null) {
            compareAgainst(baseline, payload)
        } else {
            applyContent(contentFor(payload))
        }
    }

    /**
     * Reads a second code against the pinned one.
     *
     * Anything that is not the same format, and anything that does not read at all, is a rejection
     * rather than a result: the camera stays bound and the baseline stays pinned, so the next move
     * is simply to point at a different code. Dropping into the failure screen here would silently
     * abandon a baseline the user may have walked across a building to capture.
     */
    private fun compareAgainst(baseline: ScannedContent, payload: String) {
        when (val result = decodeScannedPayload(payload)) {
            is ScanResult.Recognised -> {
                val comparison = compareScannedContent(baseline, result.content)
                if (comparison == null) {
                    emit(
                        QrScanEffect.CompareRejected(
                            CompareRejection.FormatMismatch(
                                expected = baseline.format,
                                found = result.content.format,
                            ),
                        ),
                    )
                } else {
                    applyComparison(comparison)
                }
            }

            is ScanResult.Malformed, ScanResult.Unrecognised ->
                emit(QrScanEffect.CompareRejected(CompareRejection.Unreadable))
        }
    }

    /**
     * Shows the outcome, and forwards it when the scanner was opened to feed the editor.
     *
     * The Success state is set either way rather than replaced by some "forwarded" state: it is
     * what stops the analyzer, it is what the re-decode guard reads, and it means coming back
     * from the editor lands on the report that was scanned — with its own edit action — instead
     * of a screen with nothing on it.
     */
    private fun applyContent(content: QrScanState.ContentState) {
        reduce { copy(content = content) }

        if (purpose == ScanPurpose.Edit && content is QrScanState.ContentState.Success) {
            viewModelScope.launch {
                emitEffect(QrScanEffect.EditPayload(content.content.editRoute()))
            }
        }
    }

    /**
     * Searches a picked image for a QR code, then decodes whatever it finds.
     *
     * Two stages, and the screen has to distinguish them: an image with no QR in it is a different
     * message from an image whose QR is not a valid EMV payload.
     */
    private fun decodeImage(uri: Uri) {
        if (currentState.content.isBusyOrDone) return

        reduce { copy(content = QrScanState.ContentState.Decoding) }

        viewModelScope.launch {
            val result = imageBarcodeDecoder.decode(uri)

            // The user can dismiss a decoded with the back button while it is in flight. Without
            // this the result would land afterward and reopen a report they just closed.
            if (currentState.content !is QrScanState.ContentState.Decoding) return@launch

            val baseline = currentState.baseline

            when (result) {
                // Not routed through `decode`: that refuses to run while the state is Decoding,
                // which is exactly the state this path is in.
                is ImageDecodeResult.Found -> if (baseline != null) {
                    // Back to Idle first. A rejection leaves the content alone, and leaving it on
                    // Decoding would strand the screen on a spinner with the camera unbound.
                    reduce { copy(content = QrScanState.ContentState.Idle) }
                    compareAgainst(baseline, result.payload)
                } else {
                    applyContent(contentFor(result.payload))
                }

                // A picked image that holds no code is a rejection during a comparison for the same
                // reason a damaged payload is: there is nothing to repair, only another to try.
                ImageDecodeResult.NoBarcode -> if (baseline != null) {
                    reduce { copy(content = QrScanState.ContentState.Idle) }
                    emit(QrScanEffect.CompareRejected(CompareRejection.Unreadable))
                } else {
                    reduce {
                        copy(content = QrScanState.ContentState.Failure(QrScanError.NoBarcodeInImage))
                    }
                }

                ImageDecodeResult.Unreadable -> if (baseline != null) {
                    reduce { copy(content = QrScanState.ContentState.Idle) }
                    emit(QrScanEffect.CompareRejected(CompareRejection.Unreadable))
                } else {
                    reduce {
                        copy(content = QrScanState.ContentState.Failure(QrScanError.ImageUnreadable))
                    }
                }
            }
        }
    }

    /**
     * Decodes [payload] into the state that renders its outcome.
     *
     * A malformed payload is also written back to the editor, so a failed **camera** scan lands
     * somewhere the user can repair it. Previously the scanned string was discarded at this point
     * and only a typed one survived, which made the failure screen a dead end for the path most
     * people arrive by.
     */
    private fun contentFor(payload: String): QrScanState.ContentState =
        when (val result = decodeScannedPayload(payload)) {
            is ScanResult.Recognised -> QrScanState.ContentState.Success(result.content)

            is ScanResult.Malformed -> {
                rememberPayload(result.payload)
                QrScanState.ContentState.Failure(
                    error = QrScanError.Parse(result.error),
                    payload = result.payload,
                )
            }

            ScanResult.Unrecognised ->
                QrScanState.ContentState.Failure(QrScanError.UnrecognisedFormat)
        }

    /**
     * Shares the scanned payment code as a response document.
     *
     * Built here rather than by the screen, and the effect carries the finished string. The report
     * and the comparison hand their domain object over instead, because turning either into text
     * needs string resources and has to follow a locale change — a JSON contract has neither, and
     * resolving its field names through resources would let a language setting rename them.
     *
     * Suspending now that the contract is a template the user can replace: the active one comes
     * from DataStore, and the first read of the built-in comes from an asset.
     *
     * A template that cannot produce a document is reported rather than swallowed, and never
     * quietly replaced by the built-in — an export that silently emits a *different contract* from
     * the one configured looks like it worked, which is worse than failing.
     */
    private fun shareJson() {
        val payment = scannedContent as? ScannedContent.Payment ?: return

        viewModelScope.launch {
            when (val export = exportPaymentJson(payment.report)) {
                is PaymentJsonExport.Ready -> emitEffect(QrScanEffect.ShareJson(export.json))

                is PaymentJsonExport.UnknownPlaceholder ->
                    emitEffect(QrScanEffect.SchemaRefused(SchemaRefusal.Unknown(export.token)))

                PaymentJsonExport.Outdated ->
                    emitEffect(QrScanEffect.SchemaRefused(SchemaRefusal.Outdated))

                PaymentJsonExport.Unusable ->
                    emitEffect(QrScanEffect.SchemaRefused(SchemaRefusal.Unusable))
            }
        }
    }

    /** Pins whatever is on screen and re-arms the camera to look for its counterpart. */
    private fun pinBaseline() {
        val content = scannedContent ?: return

        rememberComparison(baseline = content.payload, candidate = null)
        reduce { copy(baseline = content, content = QrScanState.ContentState.Idle) }
    }

    /** Puts the comparison away and returns to the report the baseline came from. */
    private fun cancelComparison() {
        val baseline = currentState.baseline ?: return

        rememberComparison(baseline = null, candidate = null)
        reduce {
            copy(baseline = null, content = QrScanState.ContentState.Success(baseline))
        }
    }

    /** Keeps the baseline pinned and goes looking for another code to read against it. */
    private fun rearmForCandidate() {
        val baseline = currentState.baseline ?: return

        rememberComparison(baseline = baseline.payload, candidate = null)
        reduce { copy(content = QrScanState.ContentState.Idle) }
    }

    /**
     * Reads the same two codes the other way round.
     *
     * The character alignment is dropped rather than reversed: what was a removal from A is now an
     * addition to B, so the stored spans index the wrong payloads in both directions.
     */
    private fun swapComparison() {
        val current = currentState.content as? QrScanState.ContentState.Comparison ?: return
        val swapped = compareScannedContent(
            baseline = current.comparison.candidate,
            candidate = current.comparison.baseline,
        ) ?: return

        applyComparison(swapped)
    }

    /** Shows a finished comparison and remembers both of its sides. */
    private fun applyComparison(comparison: QrComparison) {
        rememberComparison(
            baseline = comparison.baseline.payload,
            candidate = comparison.candidate.payload,
        )
        reduce {
            copy(
                baseline = comparison.baseline,
                content = QrScanState.ContentState.Comparison(comparison),
            )
        }
    }

    /**
     * Aligns the two payloads character by character, once.
     *
     * The only work in this feature that leaves the main thread. Everything else here is a single
     * pass over a few hundred characters; this is quadratic in the length of whatever the two
     * payloads do not have in common, and a worst-case pair would drop frames.
     */
    private fun computeRawDiff() {
        val current = currentState.content as? QrScanState.ContentState.Comparison ?: return
        if (current.rawDiff !is RawDiffState.NotComputed) return

        val baseline = current.comparison.baseline.payload
        val candidate = current.comparison.candidate.payload

        reduce { copy(content = current.copy(rawDiff = RawDiffState.Computing)) }

        viewModelScope.launch {
            val diff = withContext(defaultDispatcher) {
                diffPayloadCharacters(baseline, candidate)
            }

            // Swapping or re-scanning while this ran replaced the comparison the result describes,
            // and that replacement already reset the alignment to NotComputed.
            val latest = currentState.content as? QrScanState.ContentState.Comparison ?: return@launch
            if (latest.rawDiff !is RawDiffState.Computing) return@launch

            reduce { copy(content = latest.copy(rawDiff = RawDiffState.Ready(diff))) }
        }
    }

    /**
     * Rebuilds a comparison interrupted by process death.
     *
     * Only the two payloads are kept, and both are decoded again on the way back. Storing the
     * decoded reports instead would mean putting the whole of `:core:emv`'s model through the
     * saved-state bundle, to save a parse that takes less time than reading the bundle did.
     */
    private fun restoreComparison() {
        val baselinePayload = savedStateHandle.get<String>(KEY_COMPARE_BASELINE)
            ?.takeIf { it.isNotEmpty() }
            ?: return

        val baseline = (decodeScannedPayload(baselinePayload) as? ScanResult.Recognised)
            ?.content
            ?: return

        val candidate = savedStateHandle.get<String>(KEY_COMPARE_CANDIDATE)
            ?.takeIf { it.isNotEmpty() }
            ?.let { decodeScannedPayload(it) as? ScanResult.Recognised }
            ?.content

        val comparison = candidate?.let { compareScannedContent(baseline, it) }

        reduce {
            copy(
                baseline = baseline,
                content = if (comparison == null) {
                    QrScanState.ContentState.Idle
                } else {
                    QrScanState.ContentState.Comparison(comparison)
                },
            )
        }
    }

    /** Holds the payload in state and in saved state, which must not be allowed to disagree. */
    private fun rememberPayload(payload: String) {
        savedStateHandle[KEY_PAYLOAD] = payload
        reduce { copy(manualPayload = payload) }
    }

    /** Holds both sides of a comparison, so walking away from the app does not end it. */
    private fun rememberComparison(baseline: String?, candidate: String?) {
        savedStateHandle[KEY_COMPARE_BASELINE] = baseline.orEmpty()
        savedStateHandle[KEY_COMPARE_CANDIDATE] = candidate.orEmpty()
    }

    /**
     * Where a scanned code goes to be edited.
     *
     * Chosen here rather than by `:app`, because this is where the format is already known. The
     * host receiving a route it only has to navigate to mirrors how the tools list hands back an
     * `AppRoute` instead of a tool id.
     */
    private fun ScannedContent.editRoute(): AppRoute = when (this) {
        is ScannedContent.Payment -> QrCreateRoute(payload)
        is ScannedContent.Wifi -> WifiCreateRoute(payload)
        is ScannedContent.Web -> UrlCreateRoute(payload)
        is ScannedContent.Contact -> VCardCreateRoute(payload)
    }

    private fun QrScanIntent.PermissionResult.toPermissionState(): PermissionState =
        PermissionState.resolve(granted = granted, shouldShowRationale = shouldShowRationale)

    /** Whatever was scanned, or null when nothing has been. */
    private val scannedContent: ScannedContent?
        get() = (currentState.content as? QrScanState.ContentState.Success)?.content

    /** Emits [effect] for whatever was scanned, or does nothing when nothing was. */
    private fun withContent(effect: (ScannedContent) -> QrScanEffect) {
        val content = scannedContent ?: return
        emit(effect(content))
    }

    /** Emits [effect] for the comparison on screen, or does nothing when there is none. */
    private fun withComparison(effect: (QrComparison) -> QrScanEffect) {
        val current = currentState.content as? QrScanState.ContentState.Comparison ?: return
        emit(effect(current.comparison))
    }

    private fun emit(effect: QrScanEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }

    private companion object {
        const val KEY_PAYLOAD = "qrscan_payload"
        const val KEY_COMPARE_BASELINE = "qrscan_compare_baseline"
        const val KEY_COMPARE_CANDIDATE = "qrscan_compare_candidate"
    }
}

/**
 * Whether a new payload should be turned away.
 *
 * [QrScanState.ContentState.Failure] is deliberately absent: a failure is not a lock-out, and the
 * user has to be able to fix a typo or pick another photo without clearing first.
 *
 * [QrScanState.ContentState.Comparison] is present for the same reason `Success` is — a live camera
 * pointed at a finished diff would otherwise replace it thirty times a second.
 */
private val QrScanState.ContentState.isBusyOrDone: Boolean
    get() = this is QrScanState.ContentState.Success ||
        this is QrScanState.ContentState.Decoding ||
        this is QrScanState.ContentState.Comparison
