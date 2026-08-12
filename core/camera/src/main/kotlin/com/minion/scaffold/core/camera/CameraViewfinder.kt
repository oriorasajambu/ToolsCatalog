package com.minion.scaffold.core.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.OutputTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * The camera viewfinder: live preview, stepped and pinch zoom, tap to focus, torch.
 *
 * Built on [LifecycleCameraController] rather than `ProcessCameraProvider`, which is what makes
 * pinch-to-zoom and tap-to-focus two property assignments instead of two gesture detectors and a
 * `FocusMeteringAction`. The controller also reports where a focus tap landed, which is what the
 * focus ring is drawn from.
 *
 * Everything here is true of any viewfinder. What a tool draws on top of the feed goes in
 * [overlay]; what it does with the frames goes in [analyzer]. Neither is known to this module —
 * `:feature:qrscan` draws a reticle and reads QR codes, `:feature:ocr` draws text boxes and reads
 * text, and this file contains no trace of either.
 *
 * @param analyzer attached while non-null, detached when null. Null is how a caller pauses
 *   analysis — `:feature:qrscan` passes null once a report is on screen so the camera cannot
 *   re-read a code it has already delivered. **The caller owns the analyzer's lifetime**, including
 *   closing it if it holds a native detector; this composable only attaches and detaches it.
 * @param torchEnabled whether the torch is currently on.
 * @param onToggleTorch called when the user taps the torch button.
 * @param modifier the [Modifier] for the viewfinder container.
 * @param captureController non-null enables the `IMAGE_CAPTURE` use case. Left null the controller
 *   binds analysis only, which avoids allocating a capture pipeline nothing triggers.
 * @param onTransformChanged fires whenever the mapping from analysis image to viewfinder may have
 *   moved — stream start and every size change. An analyzer that maps coordinates onto the screen
 *   needs this; one that only reads content can ignore it.
 * @param overlay drawn over the feed, below the focus ring and controls. A caller that needs the
 *   viewfinder's pixel size — to place a reticle, say — puts `Modifier.onSizeChanged` on the
 *   [modifier] it passes in, rather than this taking a size it would mostly ignore.
 */
@androidx.annotation.OptIn(TransformExperimental::class)
@Composable
fun CameraViewfinder(
    analyzer: ImageAnalysis.Analyzer?,
    torchEnabled: Boolean,
    onToggleTorch: () -> Unit,
    modifier: Modifier = Modifier,
    captureController: CameraCaptureController? = null,
    onTransformChanged: (OutputTransform?) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val controller = remember(context, captureController != null) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                if (captureController != null) {
                    CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE
                } else {
                    // Preview is implicit; naming only IMAGE_ANALYSIS keeps the controller from
                    // allocating an ImageCapture nothing would use.
                    CameraController.IMAGE_ANALYSIS
                },
            )
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

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(controller, lifecycleOwner) {
        previewView.controller = controller
        controller.bindToLifecycle(lifecycleOwner)

        onDispose {
            previewView.controller = null
            controller.unbind()
            cameraExecutor.shutdown()
        }
    }

    DisposableEffect(controller, captureController, cameraExecutor) {
        captureController?.controller = controller
        captureController?.executor = cameraExecutor

        onDispose {
            captureController?.controller = null
            captureController?.executor = null
        }
    }

    DisposableEffect(controller, analyzer) {
        if (analyzer != null) {
            controller.setImageAnalysisAnalyzer(cameraExecutor, analyzer)
        }

        onDispose { controller.clearImageAnalysisAnalyzer() }
    }

    LaunchedEffect(torchEnabled) {
        controller.enableTorch(torchEnabled)
    }

    val streamState by previewView.previewStreamState.observeAsState()

    // Refreshed on stream start and on every size change — the latter is what keeps the mapping
    // right through a rotation, where a stale transform silently misplaces everything.
    LaunchedEffect(streamState, viewSize) {
        onTransformChanged(previewView.outputTransform)
    }

    val zoomState by controller.zoomState.observeAsState()
    val focusInfo by controller.tapToFocusInfoState.observeAsState()
    val spacing = dimensionResource(R.dimen.camera_spacing)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it },
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.matchParentSize(),
        )

        overlay()

        FocusRing(
            tapPoint = focusInfo?.tapPoint,
            modifier = Modifier.matchParentSize(),
        )

        ZoomControls(
            currentRatio = zoomState?.zoomRatio ?: MIN_ZOOM_RATIO,
            maxRatio = zoomState?.maxZoomRatio ?: MIN_ZOOM_RATIO,
            onSelectRatio = { controller.setZoomRatio(it) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(spacing),
        )

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
                        if (torchEnabled) R.string.camera_torch_off else R.string.camera_torch_on,
                    ),
                )
            }
        }
    }
}

private const val MIN_ZOOM_RATIO = 1f
