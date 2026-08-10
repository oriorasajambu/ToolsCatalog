package com.minion.scaffold.feature.qrscan.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class QrScanViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val decodeScannedPayload: DecodeScannedPayloadUseCase,
    private val imageBarcodeDecoder: ImageBarcodeDecoder,
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

        applyContent(contentFor(payload))
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

            when (result) {
                // Not routed through `decode`: that refuses to run while the state is Decoding,
                // which is exactly the state this path is in.
                is ImageDecodeResult.Found -> applyContent(contentFor(result.payload))

                ImageDecodeResult.NoBarcode -> reduce {
                    copy(content = QrScanState.ContentState.Failure(QrScanError.NoBarcodeInImage))
                }

                ImageDecodeResult.Unreadable -> reduce {
                    copy(content = QrScanState.ContentState.Failure(QrScanError.ImageUnreadable))
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

    /** Holds the payload in state and in saved state, which must not be allowed to disagree. */
    private fun rememberPayload(payload: String) {
        savedStateHandle[KEY_PAYLOAD] = payload
        reduce { copy(manualPayload = payload) }
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

    private fun emit(effect: QrScanEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }

    private companion object {
        const val KEY_PAYLOAD = "qrscan_payload"
    }
}

/**
 * Whether a new payload should be turned away.
 *
 * [QrScanState.ContentState.Failure] is deliberately absent: a failure is not a lock-out, and the
 * user has to be able to fix a typo or pick another photo without clearing first.
 */
private val QrScanState.ContentState.isBusyOrDone: Boolean
    get() = this is QrScanState.ContentState.Success || this is QrScanState.ContentState.Decoding
