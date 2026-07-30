package com.minion.scaffold.feature.qrscan.presentation.camera

import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.minion.scaffold.feature.qrscan.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.util.concurrent.Executors

/**
 * The camera viewfinder: an aiming reticle, stepped and pinch zoom, tap to focus.
 *
 * Built on [LifecycleCameraController] rather than `ProcessCameraProvider`, which is what makes
 * pinch-to-zoom and tap-to-focus two property assignments instead of two gesture detectors and a
 * `FocusMeteringAction`. The controller also reports where a focus tap landed, which is what the
 * focus ring is drawn from.
 *
 * Everything about aiming lives here rather than in the ViewModel. It is geometry in view pixels
 * that means nothing once the camera is gone, and the only part worth testing — [isAimed] — is a
 * pure function that needs none of this.
 *
 * @param scanningEnabled whether to look for codes. False detaches the analyzer, which is what
 *   stops the camera re-reading a code whose report is already on screen.
 */
@androidx.annotation.OptIn(TransformExperimental::class)
@Composable
internal fun CameraPreview(
    scanningEnabled: Boolean,
    torchEnabled: Boolean,
    onToggleTorch: () -> Unit,
    onPayloadDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            // Preview is implicit; naming only IMAGE_ANALYSIS keeps the controller from allocating
            // an ImageCapture nothing here uses.
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            isPinchToZoomEnabled = true
            isTapToFocusEnabled = true
        }
    }

    val previewView = remember(context) {
        PreviewView(context).apply {
            // FILL_CENTER crops the stream to the view instead of letterboxing it, which is what
            // makes the viewfinder edge-to-edge when the aspect ratios differ.
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var detection by remember { mutableStateOf<DetectedCode?>(null) }
    var reticle by remember { mutableStateOf(Rect.Zero) }

    /** Set the moment a code is accepted, so detection jitter cannot re-fire the lock. */
    var locked by remember { mutableStateOf(false) }

    // rememberUpdatedState, not a direct capture: the analyzer is remembered across recompositions,
    // so capturing the lambda would pin the very first one.
    val currentOnPayloadDetected by rememberUpdatedState(onPayloadDetected)

    // State writes from the analysis executor are safe — snapshot state is thread-safe to write.
    val analyzer = remember { BarcodeAnalyzer { detection = it } }

    DisposableEffect(controller, lifecycleOwner) {
        previewView.controller = controller
        controller.bindToLifecycle(lifecycleOwner)

        onDispose {
            previewView.controller = null
            controller.unbind()
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    /**
     * Watches for an aimed code, then holds green briefly and hands the payload on.
     *
     * Keyed on [scanningEnabled] alone — deliberately **not** on the detection or the aim state.
     * Those change at the camera's frame rate, and keying on them cancelled this coroutine
     * mid-`delay` whenever the code drifted or left frame during the hold: the payload was never
     * delivered, `locked` stayed set, and the reticle sat green forever. That was the bug.
     *
     * `first()` latches the payload at the moment of aiming, so a code that moves away afterwards
     * is still the one reported. Having already buzzed to say it was captured, dropping it would be
     * the wrong answer.
     */
    LaunchedEffect(scanningEnabled) {
        if (!scanningEnabled) {
            controller.clearImageAnalysisAnalyzer()
            detection = null
            return@LaunchedEffect
        }

        detection = null
        locked = false
        controller.setImageAnalysisAnalyzer(analysisExecutor, analyzer)

        // Reads both detection and the reticle, so it re-evaluates when either changes — including
        // a rotation that moves the box out from under a code.
        val payload = snapshotFlow {
            detection?.takeIf { isAimed(code = it.bounds, reticle = reticle) }?.payload
        }
            .filterNotNull()
            .first()

        locked = true
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        delay(LOCK_HOLD_MILLIS)
        currentOnPayloadDetected(payload)
    }

    LaunchedEffect(torchEnabled) {
        controller.enableTorch(torchEnabled)
    }

    val streamState by previewView.previewStreamState.observeAsState()

    // Refreshed on stream start and on every size change — the latter is what keeps the mapping
    // right through a rotation, where a stale transform silently rejects centred codes.
    LaunchedEffect(streamState, reticle) {
        analyzer.onPreviewTransformChanged(previewView.outputTransform)
    }

    val seen = detection
    val aim = when {
        // Stays green through the hold even if the code drifts, so the reticle agrees with the
        // haptic that already fired.
        locked -> AimState.Locked
        seen == null -> AimState.Searching
        isAimed(code = seen.bounds, reticle = reticle) -> AimState.Locked
        else -> AimState.OffTarget
    }

    val zoomState by controller.zoomState.observeAsState()
    val focusInfo by controller.tapToFocusInfoState.observeAsState()
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { reticle = reticleIn(it) },
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.matchParentSize(),
        )

        ScanReticle(
            reticle = reticle,
            aim = aim,
            modifier = Modifier.matchParentSize(),
        )

        FocusRing(
            tapPoint = focusInfo?.tapPoint,
            modifier = Modifier.matchParentSize(),
        )

        aim.hintRes()?.let { hint ->
            Text(
                text = stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(spacing),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            ZoomControls(
                currentRatio = zoomState?.zoomRatio ?: MIN_ZOOM_RATIO,
                maxRatio = zoomState?.maxZoomRatio ?: MIN_ZOOM_RATIO,
                onSelectRatio = { controller.setZoomRatio(it) },
            )
        }

        // Hidden rather than disabled on a device with no flash: a control that cannot do anything
        // is noise, and whether the hardware exists is knowable here.
        //
        // Gated on `streamState` rather than reading `cameraInfo` bare. The controller has no
        // camera until it binds, which happens after the first composition — and a bare read gives
        // Compose nothing to invalidate on, so the button would stay hidden forever. The stream
        // state is observed, so it recomposes once there is a camera to ask.
        if (streamState != null && controller.cameraInfo?.hasFlashUnit() == true) {
            FilledTonalIconButton(
                onClick = onToggleTorch,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(spacing),
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = stringResource(
                        if (torchEnabled) R.string.qrscan_torch_off else R.string.qrscan_torch_on,
                    ),
                )
            }
        }
    }
}

private fun AimState.hintRes(): Int? = when (this) {
    AimState.Searching -> R.string.qrscan_aim_searching
    AimState.OffTarget -> R.string.qrscan_aim_off_target
    // Nothing to say once it is locked — the green box and the haptic have said it.
    AimState.Locked -> null
}

/** How long the reticle stays green before the report replaces the viewfinder. */
private const val LOCK_HOLD_MILLIS = 250L

private const val MIN_ZOOM_RATIO = 1f
