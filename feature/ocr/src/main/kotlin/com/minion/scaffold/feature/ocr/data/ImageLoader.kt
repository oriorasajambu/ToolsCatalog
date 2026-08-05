package com.minion.scaffold.feature.ocr.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import kotlin.math.max

/**
 * Turns a picked `Uri` or a captured JPEG into a bitmap that is safe to hold and upright to read.
 *
 * Two jobs, both load-bearing:
 *
 * **Bounding memory.** Phone cameras produce 50–100MP images, and a full-resolution decode of one
 * is hundreds of megabytes as `ARGB_8888` — an out-of-memory crash on exactly the devices with the
 * best cameras. Everything is downsampled so the long edge lands near [MAX_EDGE_PX].
 *
 * **Uprighting.** Cameras write the orientation into EXIF rather than rotating the pixels, and ML
 * Kit's Latin model tolerates a few degrees of skew but not a 90° rotation. A sideways photo whose
 * EXIF is ignored recognises nothing at all, which reads as "OCR is broken" rather than "the photo
 * was rotated".
 *
 * The cap is a real trade, not a free win: downsampling is what destroys small text, which is
 * often the text most worth extracting. [MAX_EDGE_PX] is set where normal document text survives
 * comfortably; genuinely tiny print may need the user to move closer, which is what the
 * viewfinder's zoom is for.
 */
internal class ImageLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Decodes a picked image, or null when it cannot be opened at all. */
    suspend fun load(uri: Uri): Bitmap? = withContext(ioDispatcher) {
        val bounds = openStream(uri)?.use { it.readBounds() } ?: return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }

        val decoded = openStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@withContext null

        val rotation = openStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        decoded.rotated(rotation)
    }

    /**
     * Decodes captured JPEG bytes.
     *
     * [rotationDegrees] comes from the capture rather than EXIF — CameraX reports how the device
     * was held, which is authoritative for a frame it produced itself.
     */
    suspend fun load(jpegBytes: ByteArray, rotationDegrees: Int): Bitmap? = withContext(ioDispatcher) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }

        val decoded = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
            ?: return@withContext null

        decoded.rotated(rotationDegrees)
    }

    /**
     * Turns a bitmap a quarter turn clockwise, for the rotate-and-retry path.
     *
     * Here rather than in the ViewModel even though only the ViewModel calls it: `Bitmap` is a
     * framework type whose static factories are throwing stubs on the JVM, so a rotation inlined
     * into the ViewModel makes that whole code path untestable without Robolectric. Behind this
     * seam it is one mocked call. Keeping every pixel operation in one class is the same reasoning
     * that put the decoding here.
     */
    fun rotateQuarterTurn(bitmap: Bitmap): Bitmap =
        Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(QUARTER_TURN_DEGREES) },
            true,
        )

    private fun openStream(uri: Uri): InputStream? =
        try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            // Any of: the file is gone, the provider revoked access, the Uri was never readable.
            // None of them are separable to the user, who just picked a picture that will not open.
            null
        }

    private fun InputStream.readBounds(): BitmapFactory.Options =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeStream(this@readBounds, null, this)
        }

    /**
     * `inSampleSize` only honours powers of two, so this halves until the long edge fits rather
     * than computing an exact ratio the decoder would round anyway.
     */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longEdge = max(width, height)

        while (longEdge / 2 >= MAX_EDGE_PX) {
            longEdge /= 2
            sample *= 2
        }

        return sample
    }

    /**
     * Rotation allocates a second bitmap, which is the other reason the cap above matters: at full
     * resolution this is the moment the process runs out of memory, not the decode.
     */
    private fun Bitmap.rotated(degrees: Int): Bitmap {
        if (degrees % FULL_TURN_DEGREES == 0) return this

        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)

        // createBitmap returns the receiver unchanged when the matrix is an identity it optimised
        // away; recycling then would destroy the bitmap being returned.
        if (rotated !== this) recycle()
        return rotated
    }

    private companion object {

        /**
         * Long-edge ceiling in pixels. Bounded at roughly 16MB per decoded bitmap, comfortably
         * above what ML Kit needs for document text.
         */
        const val MAX_EDGE_PX = 2048

        const val FULL_TURN_DEGREES = 360
        const val QUARTER_TURN_DEGREES = 90f
    }
}
