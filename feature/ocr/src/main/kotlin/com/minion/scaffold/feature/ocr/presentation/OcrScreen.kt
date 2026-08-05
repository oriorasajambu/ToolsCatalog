package com.minion.scaffold.feature.ocr.presentation

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.feature.ocr.R
import kotlinx.coroutines.launch

@Composable
internal fun OcrScreen(
    onNavigateBack: () -> Unit,
    onSendToTextTools: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OcrViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Read in composition, used in the effect handler below. A configuration change recomposes and
    // re-reads these; LocalContext.current.getString() inside the handler would keep resolving
    // against the locale that was active when the screen was first composed.
    val resources = LocalResources.current
    val clipboardLabel = stringResource(R.string.ocr_clipboard_label)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onIntent(
            OcrIntent.PermissionResult(
                granted = granted,
                shouldShowRationale = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.CAMERA,
                    ),
            ),
        )
    }

    // The photo picker, not READ_MEDIA_IMAGES. It grants access to exactly the one image the user
    // chose, needs no permission at all, and cannot be declined into a broken state.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.onIntent(OcrIntent.ImagePicked(it)) } }

    // Re-checked on every resume, not once on first composition. The "open settings" path leaves
    // the app and comes back with the permission changed. See `QrScanScreen`'s identical pattern.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        when {
            granted -> viewModel.onIntent(
                OcrIntent.PermissionResult(granted = true, shouldShowRationale = false),
            )
            // Ask once, on arrival. Re-asking on every resume would trap a user who declined in a
            // dialog they cannot get past.
            state.permission == PermissionState.Unknown ->
                permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            OcrEffect.OpenAppSettings -> context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )

            is OcrEffect.CopyText -> coroutineScope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipboardLabel, effect.text)))
            }

            is OcrEffect.ShareText -> {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = MIME_TYPE_PLAIN_TEXT
                    putExtra(Intent.EXTRA_TEXT, effect.text)
                }
                context.startActivity(Intent.createChooser(share, null))
            }

            is OcrEffect.SendToTextTools -> onSendToTextTools(effect.text)
        }
    }

    // A notice is a passing remark, not a state to sit in — it clears itself so the user is not
    // left dismissing a message about a shot they already moved on from.
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(notice.messageRes()))
        viewModel.onIntent(OcrIntent.NoticeDismissed)
    }

    OcrContent(
        state = state,
        onIntent = viewModel::onIntent,
        onFrameCaptured = viewModel::onFrameCaptured,
        onNavigateBack = onNavigateBack,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onPickImage = {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onNavigateToSettings = onNavigateToSettings,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless, so every stage is previewable.
 *
 * [onRequestPermission] and [onPickImage] are lambdas rather than intents for the same reason
 * navigation is: they need an `ActivityResultLauncher`, which belongs to the composition and not
 * to a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrContent(
    state: OcrState,
    onIntent: (OcrIntent) -> Unit,
    onFrameCaptured: (com.minion.scaffold.core.camera.CapturedFrame?) -> Unit,
    onNavigateBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onPickImage: () -> Unit,
    onNavigateToSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // Later stages are steps within this screen rather than screens of their own, so back has to
    // mean "go back a stage" before it means "leave" — otherwise one capture and a stray swipe
    // drops the user all the way out to the tool list, losing the extraction.
    val canGoBackAStage = state.stage != OcrState.Stage.Capture
    val onBack = { if (canGoBackAStage) onIntent(OcrIntent.Restarted) else onNavigateBack() }

    // The system gesture and the arrow have to agree; handling only the arrow leaves the swipe
    // still exiting the feature.
    BackHandler(enabled = canGoBackAStage) { onIntent(OcrIntent.Restarted) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(state.stage.titleRes())) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.ocr_navigate_back),
                        )
                    }
                },
                actions = {
                    // Offered only while capturing: once there is a still on screen, picking a new
                    // image would silently discard the selection in progress.
                    if (state.stage == OcrState.Stage.Capture) {
                        IconButton(onClick = onPickImage) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = stringResource(R.string.ocr_pick_image),
                            )
                        }
                    }

                    // Shown in every stage, unlike the picker above: switching engine mid-selection
                    // re-reads the capture that is already on screen, which is the whole point of
                    // being able to compare two engines on one photograph.
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.ocr_settings),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (state.stage) {
                OcrState.Stage.Capture -> CaptureStage(
                    state = state,
                    onIntent = onIntent,
                    onFrameCaptured = onFrameCaptured,
                    onRequestPermission = onRequestPermission,
                    onPickImage = onPickImage,
                )

                OcrState.Stage.Selection -> state.currentCapture?.let { capture ->
                    SelectionStage(
                        capture = capture,
                        pageNumber = state.captures.size,
                        canDiscardPage = state.captures.size > 1,
                        onIntent = onIntent,
                    )
                }

                OcrState.Stage.Result -> ResultStage(state = state, onIntent = onIntent)
            }
        }
    }
}

private fun OcrState.Stage.titleRes(): Int = when (this) {
    OcrState.Stage.Capture -> R.string.ocr_title
    OcrState.Stage.Selection -> R.string.ocr_selection_title
    OcrState.Stage.Result -> R.string.ocr_result_title
}

private fun OcrNotice.messageRes(): Int = when (this) {
    OcrNotice.NoTextFound -> R.string.ocr_no_text
    OcrNotice.ImageUnreadable -> R.string.ocr_image_unreadable
    OcrNotice.CaptureFailed -> R.string.ocr_capture_failed
    OcrNotice.TextTruncated -> R.string.ocr_truncated
}

private const val MIME_TYPE_PLAIN_TEXT = "text/plain"
