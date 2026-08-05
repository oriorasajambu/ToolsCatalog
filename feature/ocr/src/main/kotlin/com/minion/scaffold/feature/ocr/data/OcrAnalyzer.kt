package com.minion.scaffold.feature.ocr.data

import android.graphics.RectF
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.compose.ui.geometry.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable

/**
 * Reports where text is in the viewfinder, so the user can tell they are framed right.
 *
 * **Boxes only — never the strings.** Analysis frames are low resolution, so small text misreads
 * badly and the reading churns frame to frame; rendering it would show the user wrong text in the
 * moment before they commit to a capture. The real recognition runs against the full-resolution
 * still, in [MlKitTextRecognizer].
 *
 * **Throttled to roughly [MIN_INTERVAL_MILLIS].** Text recognition costs far more per frame than
 * barcode scanning — tens to hundreds of milliseconds — and this overlay is only an aiming aid.
 * Running it flat out would pin a core for as long as the screen is open, for boxes that do not
 * need to move at 30fps. Skipped frames are closed immediately so the pipeline keeps flowing.
 *
 * `@TransformExperimental` sits on the class because the coordinate-mapping types appear in its
 * properties as well as in `analyze`. Applied directly rather than via `@OptIn`: CameraX's markers
 * are plain annotations checked by lint rather than `@RequiresOptIn` ones, so `@OptIn` on the
 * declaration compiles and silently does nothing.
 */
@TransformExperimental
internal class OcrAnalyzer(
    private val onBlocks: (List<Rect>) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {

    /** Held for the whole session — it runs on every frame that survives the throttle. */
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * `isUsingRotationDegrees`, so the source space is the *rotated* image — the same orientation
     * ML Kit reported the boxes in. Without it the mapping is correct only when the device happens
     * to be held the way the sensor is mounted.
     */
    private val transformFactory = ImageProxyTransformFactory().apply {
        isUsingRotationDegrees = true
    }

    /**
     * How to get from the analysis image to the viewfinder, as of the last layout.
     *
     * Volatile because it is written on the main thread — `PreviewView.getOutputTransform` may only
     * be read there — and consumed here on the analysis executor.
     */
    @Volatile
    private var previewTransform: OutputTransform? = null

    @Volatile
    private var lastRunAt = 0L

    /** Called from the main thread whenever the viewfinder's geometry may have changed. */
    fun onPreviewTransformChanged(transform: OutputTransform?) {
        previewTransform = transform
    }

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val target = previewTransform
        val now = System.currentTimeMillis()

        // No transform yet means the viewfinder has not laid out, so there is no coordinate space
        // to report boxes in.
        if (mediaImage == null || target == null || now - lastRunAt < MIN_INTERVAL_MILLIS) {
            imageProxy.close()
            return
        }
        lastRunAt = now

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val source = transformFactory.getOutputTransform(imageProxy)

        recognizer.process(image)
            .addOnSuccessListener { text ->
                val transform = CoordinateTransform(source, target)
                onBlocks(
                    text.textBlocks.mapNotNull { block ->
                        block.boundingBox?.let { box ->
                            RectF(box).also(transform::mapRect).toComposeRect()
                        }
                    },
                )
            }
            .addOnFailureListener { onBlocks(emptyList()) }
            // Closing in the completion listener rather than after `process` returns: the call is
            // asynchronous and reads from the image, so closing early hands the detector a
            // released buffer. Failing to close at all is worse — the pipeline stalls after a
            // handful of frames and presents as the camera freezing for no visible reason.
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun close() {
        recognizer.close()
    }

    private fun RectF.toComposeRect() = Rect(left, top, right, bottom)

    private companion object {

        /** Roughly 3 Hz — responsive enough to aim by, cheap enough to leave running. */
        const val MIN_INTERVAL_MILLIS = 300L
    }
}
