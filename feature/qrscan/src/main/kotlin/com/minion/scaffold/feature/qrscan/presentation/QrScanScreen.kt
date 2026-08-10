package com.minion.scaffold.feature.qrscan.presentation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.AppOutlinedButton
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.core.emv.model.HeaderDefect
import com.minion.scaffold.core.emv.model.PayloadSpan
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.core.emv.model.SegmentTrace
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.vcard.model.ContactCard
import com.minion.scaffold.feature.qrscan.domain.ScannedContent
import com.minion.scaffold.feature.qrscan.presentation.camera.CameraPreview
import com.minion.scaffold.feature.qrscan.presentation.report.ContactReportView
import com.minion.scaffold.feature.qrscan.presentation.report.PayloadDiagnosticCard
import com.minion.scaffold.feature.qrscan.presentation.report.QrInquiryReportView
import com.minion.scaffold.feature.qrscan.presentation.report.WebReportView
import com.minion.scaffold.feature.qrscan.presentation.report.WifiReportView
import com.minion.scaffold.feature.qrscan.presentation.report.describe
import com.minion.scaffold.feature.qrscan.presentation.report.describeContext
import com.minion.scaffold.feature.qrscan.presentation.report.toPlainText
import kotlinx.coroutines.launch

@Composable
internal fun QrScanScreen(
    onNavigateBack: () -> Unit,
    onEditPayload: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QrScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Read in composition, used in the effect handler below. A configuration change recomposes
    // and re-reads these, which is the point: LocalContext.current.getString() in the handler
    // would keep resolving against the locale that was active when the screen was first composed.
    val resources = LocalResources.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val clipboard = LocalClipboard.current
    val clipboardLabel = stringResource(R.string.qrscan_clipboard_label)
    val noAppMessage = stringResource(R.string.qrscan_no_app)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // The photo picker, not READ_MEDIA_IMAGES. It grants access to exactly the one image the user
    // chose, needs no permission at all, and cannot be declined into a broken state.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.onIntent(QrScanIntent.ImagePicked(it)) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onIntent(
            QrScanIntent.PermissionResult(
                granted = granted,
                shouldShowRationale = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.CAMERA,
                    ),
            ),
        )
    }

    // Re-checked on every resume, not once on first composition. The "open settings" path leaves
    // the app and comes back with the permission changed; without this the screen would still be
    // showing the blocked message over a camera it is now allowed to use.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        when {
            granted -> viewModel.onIntent(
                QrScanIntent.PermissionResult(granted = true, shouldShowRationale = false),
            )
            // Ask once, on arrival. Re-asking on every resume would trap a user who declined in
            // a dialog they cannot get past.
            state.cameraPermission == PermissionState.Unknown ->
                permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is QrScanEffect.CopyReport -> coroutineScope.launch {
                clipboard.copy(clipboardLabel, effect.content.toPlainText(resources))
            }

            is QrScanEffect.CopyText -> coroutineScope.launch {
                clipboard.copy(clipboardLabel, effect.text)
            }

            is QrScanEffect.ShareReport -> {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = MIME_TYPE_PLAIN_TEXT
                    putExtra(Intent.EXTRA_TEXT, effect.content.toPlainText(resources))
                }
                context.startActivity(Intent.createChooser(share, null))
            }

            is QrScanEffect.EditPayload -> onEditPayload(effect.route)

            is QrScanEffect.OpenLink -> {
                val opened = context.tryStart(Intent(Intent.ACTION_VIEW, effect.url.toUri()))
                if (!opened) coroutineScope.launch { snackbarHostState.showSnackbar(noAppMessage) }
            }

            is QrScanEffect.AddContact -> {
                val opened = context.tryStart(effect.card.toInsertIntent())
                if (!opened) coroutineScope.launch { snackbarHostState.showSnackbar(noAppMessage) }
            }

            QrScanEffect.OpenAppSettings -> context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        }
    }

    QrScanContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onPickImage = {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        modifier = modifier,
    )
}

/**
 * Stateless, so every arrangement is previewable.
 *
 * [onRequestPermission] is a lambda rather than an intent for the same reason navigation is: it
 * needs an `ActivityResultLauncher`, which belongs to the composition and not to a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScanContent(
    state: QrScanState,
    onIntent: (QrScanIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onRequestPermission: () -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    // A result is a step within this screen, not a screen of its own — it replaces the viewfinder
    // rather than being pushed onto the back stack. Back therefore has to mean "dismiss this
    // result" before it means "leave", or scanning one code and glancing backdrops the user all
    // the way out to the tool list.
    val hasResult = state.content !is QrScanState.ContentState.Idle

    // `Dismissed`, not `Cleared`. Back puts the result away and keeps the payload: on a failure
    // this screen is where a few hundred characters get repaired, and a mis-swipe wiping that is
    // data loss with no undo. Discarding stays on the explicit Clear button.
    val onBack = { if (hasResult) onIntent(QrScanIntent.Dismissed) else onNavigateBack() }

    // The system gesture and the arrow have to agree; handling only the arrow leaves the swipe
    // still exiting the feature.
    BackHandler(enabled = hasResult) { onIntent(QrScanIntent.Dismissed) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.qrscan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.qrscan_navigate_back),
                        )
                    }
                },
                actions = {
                    // The picker stays available on a failure — trying another photo is one of the
                    // two obvious next moves. The mode toggle does not: the editor is already on
                    // screen there, so the button would do nothing visible.
                    if (state.content is QrScanState.ContentState.Idle ||
                        state.content is QrScanState.ContentState.Failure
                    ) {
                        IconButton(onClick = onPickImage) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = stringResource(R.string.qrscan_pick_image),
                            )
                        }
                    }
                    if (state.content is QrScanState.ContentState.Idle) {
                        InputModeAction(mode = state.mode, onIntent = onIntent)
                    }
                    // Only offered once there is a report. An always-present copy button that
                    // silently does nothing is worse than one that appears when it works.
                    if (state.hasReport) {
                        IconButton(onClick = { onIntent(QrScanIntent.EditRequested) }) {
                            Icon(
                                imageVector = Icons.Filled.EditNote,
                                contentDescription = stringResource(R.string.qrscan_edit_payload),
                            )
                        }
                        IconButton(onClick = { onIntent(QrScanIntent.CopyReportRequested) }) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.qrscan_copy_report),
                            )
                        }
                        IconButton(onClick = { onIntent(QrScanIntent.ShareReportRequested) }) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(R.string.qrscan_share_report),
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            when (val content = state.content) {
                QrScanState.ContentState.Idle -> IdleContent(
                    state = state,
                    onIntent = onIntent,
                    onRequestPermission = onRequestPermission,
                    modifier = Modifier.weight(1f),
                )

                QrScanState.ContentState.Decoding -> DecodingContent(
                    modifier = Modifier.weight(1f),
                )

                is QrScanState.ContentState.Failure -> FailureContent(
                    failure = content,
                    editedPayload = state.manualPayload,
                    onIntent = onIntent,
                    // `weight`, so the grid and the editor scroll. Without it this column had no
                    // bounded height and no scroller at all — survivable with one sentence in it,
                    // not with thirty rows of payload.
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = spacing, vertical = spacing),
                )

                is QrScanState.ContentState.Success -> when (val scanned = content.content) {
                    is ScannedContent.Payment -> QrInquiryReportView(
                        report = scanned.report,
                        onCopy = { onIntent(QrScanIntent.CopyValueRequested(it)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = spacing, vertical = spacing),
                    )

                    is ScannedContent.Wifi -> WifiReportView(
                        credentials = scanned.credentials,
                        onCopy = { onIntent(QrScanIntent.CopyValueRequested(it)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = spacing, vertical = spacing),
                    )

                    is ScannedContent.Web -> WebReportView(
                        url = scanned.url,
                        onCopy = { onIntent(QrScanIntent.CopyValueRequested(it)) },
                        onOpenLink = { onIntent(QrScanIntent.OpenLinkRequested) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = spacing, vertical = spacing),
                    )

                    is ScannedContent.Contact -> ContactReportView(
                        card = scanned.card,
                        onCopy = { onIntent(QrScanIntent.CopyValueRequested(it)) },
                        onAddContact = { onIntent(QrScanIntent.AddContactRequested) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = spacing, vertical = spacing),
                    )
                }
            }
        }
    }
}

@Composable
private fun InputModeAction(
    mode: InputMode,
    onIntent: (QrScanIntent) -> Unit,
) {
    val switchingToManual = mode == InputMode.Camera

    IconButton(
        onClick = {
            onIntent(
                QrScanIntent.ModeChanged(
                    if (switchingToManual) InputMode.Manual else InputMode.Camera,
                ),
            )
        },
    ) {
        Icon(
            imageVector = if (switchingToManual) {
                Icons.Filled.Keyboard
            } else {
                Icons.Filled.QrCodeScanner
            },
            contentDescription = stringResource(
                if (switchingToManual) R.string.qrscan_mode_manual else R.string.qrscan_mode_camera,
            ),
        )
    }
}

@Composable
private fun IdleContent(
    state: QrScanState,
    onIntent: (QrScanIntent) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    when {
        state.mode == InputMode.Manual -> PayloadEditorPanel(
            payload = state.manualPayload,
            onPayloadChanged = { onIntent(QrScanIntent.ManualPayloadChanged(it)) },
            onDecode = { onIntent(QrScanIntent.PayloadSubmitted(state.manualPayload)) },
            onClear = { onIntent(QrScanIntent.Cleared) },
            showHint = true,
            modifier = modifier.padding(horizontal = spacing),
        )

        state.cameraPermission == PermissionState.Granted -> CameraPreview(
            scanningEnabled = state.isScanning,
            torchEnabled = state.torchEnabled,
            onToggleTorch = { onIntent(QrScanIntent.TorchToggled) },
            onPayloadDetected = { onIntent(QrScanIntent.PayloadSubmitted(it)) },
            modifier = modifier,
        )

        state.cameraPermission == PermissionState.PermanentlyDenied -> PermissionPanel(
            message = stringResource(R.string.qrscan_permission_blocked),
            actionLabel = stringResource(R.string.qrscan_permission_open_settings),
            onAction = { onIntent(QrScanIntent.AppSettingsRequested) },
            onUseManualInput = { onIntent(QrScanIntent.ModeChanged(InputMode.Manual)) },
            modifier = modifier.padding(horizontal = spacing),
        )

        state.cameraPermission == PermissionState.Denied -> PermissionPanel(
            message = stringResource(R.string.qrscan_permission_rationale),
            actionLabel = stringResource(R.string.qrscan_permission_grant),
            onAction = onRequestPermission,
            onUseManualInput = { onIntent(QrScanIntent.ModeChanged(InputMode.Manual)) },
            modifier = modifier.padding(horizontal = spacing),
        )

        // Unknown: the request dialog is on screen. Anything rendered here would flash behind it.
        else -> Unit
    }
}

@Composable
private fun PermissionPanel(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onUseManualInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            AppButton(text = actionLabel, onClick = onAction)
            // Always offered. Declining the camera costs one input method, not the feature.
            AppOutlinedButton(
                text = stringResource(R.string.qrscan_permission_use_manual),
                onClick = onUseManualInput,
            )
        }
    }
}

/**
 * The payload field and its two actions.
 *
 * Shared by the idle screen and the failure screen, so a decode that fails leaves the text exactly
 * where it was rather than replacing it with a dead end. It does not survive as the *same*
 * composition node across that transition — different branch, different node — but nothing is lost
 * but focus, because the text itself lives in the ViewModel.
 */
@Composable
private fun PayloadEditorPanel(
    payload: String,
    onPayloadChanged: (String) -> Unit,
    onDecode: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    showHint: Boolean = false,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        OutlinedTextField(
            value = payload,
            onValueChange = onPayloadChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(R.dimen.qrscan_input_min_height)),
            label = { Text(text = stringResource(R.string.qrscan_manual_label)) },
            placeholder = { Text(text = stringResource(R.string.qrscan_manual_placeholder)) },
            // Monospace so a reader can count characters against a declared length, which is the
            // whole activity when a payload is malformed.
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            AppButton(
                text = stringResource(R.string.qrscan_manual_parse),
                onClick = onDecode,
                enabled = payload.isNotBlank(),
            )
            AppOutlinedButton(
                text = stringResource(R.string.qrscan_manual_clear),
                onClick = onClear,
                enabled = payload.isNotEmpty(),
            )
        }

        if (showHint) {
            Text(
                text = stringResource(R.string.qrscan_idle_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DecodingContent(modifier: Modifier = Modifier) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.qrscan_decoding),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * A failure, as somewhere to work rather than somewhere to leave.
 *
 * The old version was one sentence and a button that discarded the payload — for a few hundred
 * characters that is not enough to act on, and it threw away the only copy. This keeps the payload
 * editable, says what was expected and what was found, and draws the payload with the damage marked.
 */
@Composable
private fun FailureContent(
    failure: QrScanState.ContentState.Failure,
    editedPayload: String,
    onIntent: (QrScanIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val parseError = (failure.error as? QrScanError.Parse)?.error

    // Trimmed on both sides: the parser trims before framing, so the offsets belong to the trimmed
    // string. Comparing raw would call a payload stale over a trailing newline nobody typed.
    val stale = failure.payload.isNotEmpty() && editedPayload.trim() != failure.payload

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        item(key = "summary") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(spacing),
                    verticalArrangement = Arrangement.spacedBy(
                        dimensionResource(R.dimen.qrscan_spacing_tight),
                    ),
                ) {
                    Text(
                        text = failure.error.describe(resources),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // The other end of the bracket: where the parser stopped is rarely where the
                    // payload went wrong, and the last segment that read cleanly is the closest
                    // honest pointer at the difference.
                    parseError?.describeContext(resources)?.let { context ->
                        Text(text = context, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (failure.payload.isNotEmpty()) {
            item(key = "editor") {
                PayloadEditorPanel(
                    payload = editedPayload,
                    onPayloadChanged = { onIntent(QrScanIntent.ManualPayloadChanged(it)) },
                    onDecode = { onIntent(QrScanIntent.PayloadSubmitted(editedPayload)) },
                    onClear = { onIntent(QrScanIntent.Cleared) },
                )
            }

            item(key = "diagnostic") {
                PayloadDiagnosticCard(
                    payload = failure.payload,
                    error = failure.error,
                    stale = stale,
                    onCopyDiagnostic = { onIntent(QrScanIntent.CopyValueRequested(it)) },
                )
            }
        }

        item(key = "actions") {
            AppOutlinedButton(
                text = stringResource(R.string.qrscan_scan_again),
                onClick = { onIntent(QrScanIntent.Cleared) },
            )
        }
    }
}

private const val MIME_TYPE_PLAIN_TEXT = "text/plain"

private suspend fun Clipboard.copy(label: String, text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
}

@Preview
@Composable
internal fun QrScanManualInputPreview() {
    QrScanContentPreview(QrScanState(mode = InputMode.Manual))
}

@Preview
@Composable
internal fun QrScanPermissionDeniedPreview() {
    QrScanContentPreview(QrScanState(cameraPermission = PermissionState.Denied))
}

@Preview
@Composable
internal fun QrScanDecodingPreview() {
    QrScanContentPreview(QrScanState(content = QrScanState.ContentState.Decoding))
}

@Preview
@Composable
internal fun QrScanPaymentReportPreview() {
    QrScanContentPreview(
        QrScanState(
            content = QrScanState.ContentState.Success(
                ScannedContent.Payment(QrScanPreviewData.report),
            ),
            manualPayload = QrScanPreviewData.SAMPLE_PAYLOAD,
        ),
    )
}

@Preview
@Composable
internal fun QrScanWifiReportPreview() {
    QrScanContentPreview(
        QrScanState(
            content = QrScanState.ContentState.Success(
                ScannedContent.Wifi(
                    payload = QrScanPreviewData.SAMPLE_WIFI_PAYLOAD,
                    credentials = QrScanPreviewData.wifiCredentials,
                ),
            ),
        ),
    )
}

@Preview
@Composable
internal fun QrScanUnrecognisedPreview() {
    QrScanContentPreview(
        QrScanState(
            content = QrScanState.ContentState.Failure(QrScanError.UnrecognisedFormat),
        ),
    )
}

/**
 * The payload that prompted the diagnostic work: tag `32` missing its two length digits.
 *
 * A preview rather than only a test, because the grid's layout — row length, gutter alignment, where
 * the highlight lands across a row boundary — is the kind of thing that only shows itself drawn.
 */
@Preview
@Composable
internal fun QrScanParseFailurePreview() {
    val payload = "000201010212320011SA.GOV.SAMA011612345678901234560206VVSSRR030212520412345" +
        "3036825403100550201570310580 2SA5925merchantNameUpTo25char12"

    QrScanContentPreview(
        QrScanState(
            content = QrScanState.ContentState.Failure(
                error = QrScanError.Parse(
                    QrParseError.MalformedTlv(
                        offset = 16,
                        span = PayloadSpan(18, 20),
                        defect = HeaderDefect.NON_NUMERIC_LENGTH,
                        found = "SA",
                        lastGoodSegment = SegmentTrace("32", 0, PayloadSpan(12, 16)),
                    ),
                ),
                payload = payload,
            ),
            manualPayload = payload,
        ),
    )
}

@Preview
@Composable
internal fun QrScanNoBarcodeInImagePreview() {
    QrScanContentPreview(
        QrScanState(content = QrScanState.ContentState.Failure(QrScanError.NoBarcodeInImage)),
    )
}

/**
 * Private, so Showcase skips it — it takes a parameter and could not be called from the catalog
 * anyway. The `@Preview` functions above stay `internal` so they do appear.
 */
@Composable
private fun QrScanContentPreview(state: QrScanState) {
    AppTheme {
        QrScanContent(
            state = state,
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
            onRequestPermission = {},
            onPickImage = {},
        )
    }
}

/**
 * Starts [intent], reporting whether anything on the device could.
 *
 * A phone with no browser or no contacts app is close to impossible, but silently doing nothing
 * when a button is pressed is the one outcome worth ruling out.
 */
private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * The contacts app's own insert screen, pre-filled.
 *
 * Deliberately an offer rather than a write: the user confirms it there, which is why this needs no
 * contacts permission and why they get a last look before anything reaches their address book.
 */
private fun ContactCard.toInsertIntent(): Intent =
    Intent(ContactsContract.Intents.Insert.ACTION).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        putExtra(ContactsContract.Intents.Insert.NAME, formattedName)
        if (phone.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, phone)
        if (email.isNotBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, email)
        if (organization.isNotBlank()) {
            putExtra(ContactsContract.Intents.Insert.COMPANY, organization)
        }
        if (title.isNotBlank()) putExtra(ContactsContract.Intents.Insert.JOB_TITLE, title)
    }
