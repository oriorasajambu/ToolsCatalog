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
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.vcard.model.ContactCard
import com.minion.scaffold.feature.qrscan.domain.ScannedContent
import com.minion.scaffold.feature.qrscan.presentation.camera.CameraPreview
import com.minion.scaffold.feature.qrscan.presentation.report.ContactReportView
import com.minion.scaffold.feature.qrscan.presentation.report.QrInquiryReportView
import com.minion.scaffold.feature.qrscan.presentation.report.WebReportView
import com.minion.scaffold.feature.qrscan.presentation.report.WifiReportView
import com.minion.scaffold.feature.qrscan.presentation.report.describe
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
            state.cameraPermission == CameraPermissionState.Unknown ->
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
    val onBack = { if (hasResult) onIntent(QrScanIntent.Cleared) else onNavigateBack() }

    // The system gesture and the arrow have to agree; handling only the arrow leaves the swipe
    // still exiting the feature.
    BackHandler(enabled = hasResult) { onIntent(QrScanIntent.Cleared) }

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
                    if (state.content is QrScanState.ContentState.Idle) {
                        IconButton(onClick = onPickImage) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = stringResource(R.string.qrscan_pick_image),
                            )
                        }
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
                    error = content.error,
                    onIntent = onIntent,
                    modifier = Modifier.padding(horizontal = spacing),
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
        state.mode == InputMode.Manual -> ManualInputPanel(
            payload = state.manualPayload,
            onPayloadChanged = { onIntent(QrScanIntent.ManualPayloadChanged(it)) },
            onDecode = { onIntent(QrScanIntent.PayloadSubmitted(state.manualPayload)) },
            onClear = { onIntent(QrScanIntent.Cleared) },
            modifier = modifier.padding(horizontal = spacing),
        )

        state.cameraPermission == CameraPermissionState.Granted -> CameraPreview(
            scanningEnabled = state.isScanning,
            torchEnabled = state.torchEnabled,
            onToggleTorch = { onIntent(QrScanIntent.TorchToggled) },
            onPayloadDetected = { onIntent(QrScanIntent.PayloadSubmitted(it)) },
            modifier = modifier,
        )

        state.cameraPermission == CameraPermissionState.PermanentlyDenied -> PermissionPanel(
            message = stringResource(R.string.qrscan_permission_blocked),
            actionLabel = stringResource(R.string.qrscan_permission_open_settings),
            onAction = { onIntent(QrScanIntent.AppSettingsRequested) },
            onUseManualInput = { onIntent(QrScanIntent.ModeChanged(InputMode.Manual)) },
            modifier = modifier.padding(horizontal = spacing),
        )

        state.cameraPermission == CameraPermissionState.Denied -> PermissionPanel(
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

@Composable
private fun ManualInputPanel(
    payload: String,
    onPayloadChanged: (String) -> Unit,
    onDecode: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
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

        Text(
            text = stringResource(R.string.qrscan_idle_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Composable
private fun FailureContent(
    error: QrScanError,
    onIntent: (QrScanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Text(
                text = error.describe(resources),
                modifier = Modifier.padding(spacing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        AppButton(
            text = stringResource(R.string.qrscan_try_again),
            onClick = { onIntent(QrScanIntent.Cleared) },
        )
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
    QrScanContentPreview(QrScanState(cameraPermission = CameraPermissionState.Denied))
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

@Preview
@Composable
internal fun QrScanParseFailurePreview() {
    QrScanContentPreview(
        QrScanState(
            content = QrScanState.ContentState.Failure(
                QrScanError.Parse(QrParseError.MissingCrc),
            ),
            manualPayload = "000201010212",
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
