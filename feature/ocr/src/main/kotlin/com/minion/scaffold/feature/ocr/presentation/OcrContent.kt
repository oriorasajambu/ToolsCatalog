package com.minion.scaffold.feature.ocr.presentation

import androidx.camera.view.TransformExperimental
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.minion.scaffold.core.camera.CameraCaptureController
import com.minion.scaffold.core.camera.CameraViewfinder
import com.minion.scaffold.core.camera.CaptureResult
import com.minion.scaffold.core.camera.CapturedFrame
import com.minion.scaffold.core.camera.rememberCameraCaptureController
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.AppOutlinedButton
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.feature.ocr.R
import com.minion.scaffold.feature.ocr.data.OcrAnalyzer
import com.minion.scaffold.feature.ocr.presentation.overlay.BlockOverlay
import com.minion.scaffold.feature.ocr.presentation.overlay.HintBoxes
import kotlinx.coroutines.launch

/** The capture stage: live viewfinder when the camera is available, gallery entry when it is not. */
@androidx.annotation.OptIn(TransformExperimental::class)
@Composable
internal fun CaptureStage(
    state: OcrState,
    onIntent: (OcrIntent) -> Unit,
    onFrameCaptured: (CapturedFrame?) -> Unit,
    onRequestPermission: () -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.ocr_spacing)

    when (state.permission) {
        // Nothing while the system dialog is up — anything here flashes behind it.
        PermissionState.Unknown -> Box(modifier = modifier.fillMaxSize())

        PermissionState.Granted -> Viewfinder(
            state = state,
            onIntent = onIntent,
            onFrameCaptured = onFrameCaptured,
            modifier = modifier,
        )

        PermissionState.Denied -> PermissionPanel(
            message = stringResource(R.string.ocr_permission_rationale),
            actionLabel = stringResource(R.string.ocr_permission_grant),
            onAction = onRequestPermission,
            onPickImage = onPickImage,
            spacing = spacing,
            modifier = modifier,
        )

        PermissionState.PermanentlyDenied -> PermissionPanel(
            message = stringResource(R.string.ocr_permission_blocked),
            actionLabel = stringResource(R.string.ocr_permission_open_settings),
            onAction = { onIntent(OcrIntent.AppSettingsRequested) },
            onPickImage = onPickImage,
            spacing = spacing,
            modifier = modifier,
        )
    }
}

@androidx.annotation.OptIn(TransformExperimental::class)
@Composable
private fun Viewfinder(
    state: OcrState,
    onIntent: (OcrIntent) -> Unit,
    onFrameCaptured: (CapturedFrame?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.ocr_spacing)
    val captureController: CameraCaptureController = rememberCameraCaptureController()
    val coroutineScope = rememberCoroutineScope()

    val analyzer = remember { OcrAnalyzer { boxes -> onIntent(OcrIntent.HintBoxesChanged(boxes)) } }

    // The viewfinder attaches and detaches the analyzer but does not own it, because it cannot know
    // whether one holds a native detector. This one does, so closing it is this file's job.
    DisposableEffect(analyzer) {
        onDispose { analyzer.close() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CameraViewfinder(
            // Detached while a recognition is running: the result is about to replace this screen,
            // and the boxes would keep twitching over a frame the user has already committed to.
            analyzer = analyzer.takeIf { !state.isRecognising },
            torchEnabled = false,
            onToggleTorch = {},
            captureController = captureController,
            onTransformChanged = analyzer::onPreviewTransformChanged,
        ) {
            HintBoxes(boxes = state.hintBoxes, modifier = Modifier.matchParentSize())
        }

        if (state.isRecognising) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        onFrameCaptured(
                            (captureController.capture() as? CaptureResult.Success)?.frame,
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = spacing * FAB_BOTTOM_INSET_MULTIPLIER),
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = stringResource(R.string.ocr_shutter),
                )
            }
        }
    }
}

@Composable
private fun PermissionPanel(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onPickImage: () -> Unit,
    spacing: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            AppButton(text = actionLabel, onClick = onAction)
            // Always offered. Declining the camera costs one input method, not the feature — the
            // same stance :feature:qrscan takes with its manual-input escape hatch.
            AppOutlinedButton(
                text = stringResource(R.string.ocr_permission_use_gallery),
                onClick = onPickImage,
            )
        }
    }
}

/** The selection stage: the captured still with its blocks tappable. */
@Composable
internal fun SelectionStage(
    capture: CaptureUi,
    pageNumber: Int,
    canDiscardPage: Boolean,
    onIntent: (OcrIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.ocr_spacing)

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.ocr_selection_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = spacing, vertical = spacing / 2),
        )

        BlockOverlay(
            image = capture.bitmap.asImageBitmap(),
            text = capture.text,
            selectedIds = capture.selectedBlockIds,
            onToggleBlock = { onIntent(OcrIntent.BlockToggled(it)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing / 2),
        ) {
            Text(
                text = stringResource(R.string.ocr_page_count, pageNumber),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing / 2)) {
                AppOutlinedButton(
                    text = stringResource(
                        if (capture.selectedBlockIds.size == capture.text.blocks.size) {
                            R.string.ocr_select_none
                        } else {
                            R.string.ocr_select_all
                        },
                    ),
                    onClick = { onIntent(OcrIntent.SelectAllToggled) },
                    enabled = capture.text.blocks.isNotEmpty(),
                )
                AppOutlinedButton(
                    text = stringResource(R.string.ocr_rotate_retry),
                    onClick = { onIntent(OcrIntent.RotateAndRetry) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing / 2)) {
                AppButton(
                    text = stringResource(R.string.ocr_selection_confirm),
                    onClick = { onIntent(OcrIntent.SelectionConfirmed) },
                )
                AppOutlinedButton(
                    text = stringResource(R.string.ocr_add_page),
                    onClick = { onIntent(OcrIntent.AddAnotherPage) },
                )
                // Only once there is more than one page. Discarding the only page would leave an
                // empty result with nothing to explain itself — "start over" is that job, and it
                // already exists on the result screen.
                if (canDiscardPage) {
                    AppOutlinedButton(
                        text = stringResource(R.string.ocr_remove_page, pageNumber),
                        onClick = { onIntent(OcrIntent.CaptureRemoved(capture.id)) },
                    )
                }
            }
        }
    }
}

/** The result stage: the assembled text, editable before use. */
@Composable
internal fun ResultStage(
    state: OcrState,
    onIntent: (OcrIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.ocr_spacing)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(
            text = stringResource(R.string.ocr_result_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.editedText,
            onValueChange = { onIntent(OcrIntent.ResultEdited(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(R.dimen.ocr_result_min_height)),
            label = { Text(text = stringResource(R.string.ocr_result_label)) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing / 2)) {
            AppButton(
                text = stringResource(R.string.ocr_copy),
                onClick = { onIntent(OcrIntent.CopyRequested) },
                enabled = state.editedText.isNotBlank(),
            )
            AppOutlinedButton(
                text = stringResource(R.string.ocr_share),
                onClick = { onIntent(OcrIntent.ShareRequested) },
                enabled = state.editedText.isNotBlank(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing / 2)) {
            AppOutlinedButton(
                text = stringResource(R.string.ocr_send_to_text_tools),
                onClick = { onIntent(OcrIntent.SendToTextToolsRequested) },
                enabled = state.editedText.isNotBlank(),
            )
            AppOutlinedButton(
                text = stringResource(R.string.ocr_restart),
                onClick = { onIntent(OcrIntent.Restarted) },
            )
        }
    }
}

/** Lifts the shutter clear of the zoom controls the viewfinder draws along the bottom. */
private const val FAB_BOTTOM_INSET_MULTIPLIER = 4
