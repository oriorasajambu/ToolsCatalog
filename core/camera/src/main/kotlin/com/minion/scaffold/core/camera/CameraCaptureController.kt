package com.minion.scaffold.core.camera

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * One still frame, straight out of the camera and still compressed.
 *
 * Deliberately JPEG bytes rather than a decoded `Bitmap`: how far to downsample is a policy the
 * *consumer* owns — OCR caps the long edge to bound memory, and a future consumer might not — and
 * a `Bitmap` here would have made that choice for everyone at the widest possible setting.
 *
 * A plain class rather than a `data class` because [jpegBytes] is an array: the generated `equals`
 * would compare identity while looking like it compares content.
 *
 * @property jpegBytes       The still frame as compressed JPEG bytes.
 * @property rotationDegrees The clockwise rotation, in degrees, needed to display it upright.
 */
class CapturedFrame(
    val jpegBytes: ByteArray,
    val rotationDegrees: Int,
)

/** The outcome of a shutter press. */
sealed interface CaptureResult {

    /**
     * The shot was taken.
     *
     * @property frame The captured still.
     */
    class Success(val frame: CapturedFrame) : CaptureResult

    /**
     * The camera refused the shot. Carries nothing: every underlying cause — no camera bound, disk
     * pressure, hardware error — leaves the user with the same single option, which is to try
     * again, and `ImageCaptureException`'s reasons are not phrasable for anyone.
     */
    data object Failed : CaptureResult
}

/**
 * A handle for taking a still from a [CameraViewfinder].
 *
 * Created by [rememberCameraCaptureController] and passed *in*, rather than being returned from the
 * viewfinder: the shutter button generally lives outside the viewfinder's own layout, so the
 * caller needs the handle before the viewfinder composes.
 *
 * Passing one also switches `IMAGE_CAPTURE` on. A viewfinder given `null` binds analysis only,
 * which is what `:feature:qrscan` wants — an `ImageCapture` use case nothing triggers still costs
 * a buffer allocation, and on some devices it constrains the resolutions the other use cases can
 * take.
 */
@Stable
class CameraCaptureController {

    /** Set by [CameraViewfinder] while it is composed; null once it leaves. */
    internal var controller: LifecycleCameraController? = null
    internal var executor: Executor? = null

    /** Whether a shot can be taken right now — false before the viewfinder has bound a camera. */
    val isReady: Boolean get() = controller != null

    /**
     * Takes a single still.
     *
     * @return [CaptureResult.Success] with the frame, or [CaptureResult.Failed] when no camera is
     *   bound or the capture errors.
     */
    suspend fun capture(): CaptureResult {
        val camera = controller
        val callbackExecutor = executor
        if (camera == null || callbackExecutor == null) return CaptureResult.Failed

        return suspendCancellableCoroutine { continuation ->
            camera.takePicture(
                callbackExecutor,
                object : ImageCapture.OnImageCapturedCallback() {

                    override fun onCaptureSuccess(image: ImageProxy) {
                        // Copied out and the proxy closed here rather than handed on: the buffer
                        // belongs to the capture pipeline, and holding it open past this callback
                        // stalls further captures the same way an unclosed analysis frame stalls
                        // the analyzer.
                        //
                        // Rotation is read *before* the close, not after — `imageInfo` is only
                        // valid while the proxy is open, and reading it in the resume below would
                        // be a use-after-close that happens to work until it doesn't.
                        val result = try {
                            val rotation = image.imageInfo.rotationDegrees
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining()).also(buffer::get)
                            CaptureResult.Success(CapturedFrame(bytes, rotation))
                        } catch (_: Exception) {
                            CaptureResult.Failed
                        } finally {
                            image.close()
                        }

                        if (continuation.isActive) continuation.resume(result)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resume(CaptureResult.Failed)
                    }
                },
            )
        }
    }
}

/**
 * Remembers a [CameraCaptureController] across recompositions.
 *
 * @return The retained controller to pass into [CameraViewfinder] and drive from a shutter button.
 */
@Composable
fun rememberCameraCaptureController(): CameraCaptureController = remember { CameraCaptureController() }
