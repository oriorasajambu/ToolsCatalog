package com.minion.scaffold.feature.qrscan.presentation.camera

import android.graphics.RectF
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.compose.ui.geometry.Rect
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable

/** A QR code seen in the viewfinder, with its position in **view** pixels. */
internal data class DetectedCode(
    val payload: String,
    val bounds: Rect,
)

/**
 * Reads QR codes off the camera stream and reports where each one sits on screen.
 *
 * Restricted to [Barcode.FORMAT_QR_CODE]. ML Kit scans for every enabled format on every frame, so
 * leaving the others on costs work per frame to find codes this tool cannot read.
 *
 * **Reports on every frame, and no longer latches on the first hit.** The reticle has to know a
 * code is visible while the user is still lining it up, which means a detection that is *not* aimed
 * has to arrive too. Preventing repeat delivery moved to the caller, which is the only place that
 * knows whether a payload has already been handed on.
 *
 * `@TransformExperimental` sits on the class because the coordinate-mapping types appear in its
 * properties as well as in `analyze`. Applied directly rather than via `@OptIn`: CameraX's markers
 * are plain annotations checked by lint rather than `@RequiresOptIn` ones, so `@OptIn` on the
 * declaration compiles and silently does nothing.
 */
@TransformExperimental
internal class BarcodeAnalyzer(
    private val onDetection: (DetectedCode?) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    /**
     * `isUsingRotationDegrees`, so the source space is the *rotated* image — the same orientation
     * ML Kit reported the bounding box in. Without it the mapping is correct only when the device
     * happens to be held the way the sensor is mounted.
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

    /** Called from the main thread whenever the viewfinder's geometry may have changed. */
    fun onPreviewTransformChanged(transform: OutputTransform?) {
        previewTransform = transform
    }

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val target = previewTransform

        // No transform yet means the viewfinder has not laid out, so there is nothing on screen to
        // be aimed at and no coordinate space to report in.
        if (mediaImage == null || target == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val source = transformFactory.getOutputTransform(imageProxy)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                onDetection(barcodes.firstOnScreen(source, target))
            }
            .addOnFailureListener { onDetection(null) }
            // Closing in the completion listener rather than after `process` returns: the call is
            // asynchronous and reads from the image, so closing early hands the detector a
            // released buffer. Failing to close at all is worse — the pipeline stalls after a
            // handful of frames and presents as the camera freezing for no visible reason.
            .addOnCompleteListener { imageProxy.close() }
    }

    /**
     * The first readable code, with its box mapped into view pixels.
     *
     * The mapping is the whole reason this class knows about the viewfinder. ML Kit reports a box in
     * the analysis image's space, which differs from the screen by rotation, resolution and the
     * `FILL_CENTER` crop; comparing it against the reticle without mapping would reject codes
     * sitting visibly inside the box.
     */
    private fun List<Barcode>.firstOnScreen(
        source: OutputTransform,
        target: OutputTransform,
    ): DetectedCode? {
        val transform = CoordinateTransform(source, target)

        for (barcode in this) {
            val payload = barcode.rawValue ?: continue
            val box = barcode.boundingBox ?: continue

            val mapped = RectF(box)
            transform.mapRect(mapped)

            return DetectedCode(
                payload = payload,
                bounds = Rect(mapped.left, mapped.top, mapped.right, mapped.bottom),
            )
        }

        return null
    }

    override fun close() {
        scanner.close()
    }
}
