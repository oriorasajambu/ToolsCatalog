package com.minion.scaffold.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.minion.scaffold.core.designsystem.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders [payload] as a QR code.
 *
 * Renders nothing while encoding, and nothing at all if the payload will not fit in a QR code.
 *
 * [contentDescription] is a parameter rather than a module string: a design-system widget takes
 * its copy from the caller, the same way [AppButton] takes its label, so the wording can suit the
 * screen using it.
 *
 * @param payload            The text to encode as a QR code.
 * @param contentDescription The accessibility description, or `null` when purely decorative.
 * @param modifier           The [Modifier] for the image.
 */
@Composable
fun QrCodeImage(
    payload: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // produceState, not remember: encoding allocates a quarter-million-entry pixel array and runs
    // the whole Reed-Solomon pass, which is enough to drop a frame if it happens during
    // composition on the main thread.
    val bitmap by produceState<Bitmap?>(initialValue = null, payload) {
        value = withContext(Dispatchers.Default) { encodeQrBitmap(payload, DISPLAY_SIZE_PX) }
    }

    bitmap?.let { encoded ->
        Image(
            bitmap = encoded.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            // The bitmap is a grid of hard-edged squares. Bilinear filtering would blur the module
            // boundaries as it scales, which is exactly the detail a scanner needs.
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * Flattens the matrix into a row-major pixel array.
 *
 * Separate from [encodeQrBitmap] so the encode reads as encode-then-write; the blit itself is two
 * loops over a square and has nothing to say.
 */
private fun BitMatrix.toPixels(sizePx: Int): IntArray {
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val rowOffset = y * sizePx
        for (x in 0 until sizePx) {
            pixels[rowOffset + x] = if (this[x, y]) MODULE_DARK else MODULE_LIGHT
        }
    }
    return pixels
}

/**
 * Encodes [payload] as a square QR bitmap [sizePx] on a side, or null if it will not fit.
 *
 * Public alongside [QrCodeImage] because exporting a QR — sharing it, saving it to the gallery —
 * needs the bitmap itself, at a higher resolution than the one on screen. Blocking work: call it
 * off the main thread.
 *
 * @param payload The text to encode.
 * @param sizePx  The side length of the square output bitmap, in pixels.
 * @return The encoded bitmap, or `null` when the payload is empty or too large for any QR version.
 */
fun encodeQrBitmap(payload: String, sizePx: Int = DISPLAY_SIZE_PX): Bitmap? {
    val matrix = encodeQrMatrix(payload, sizePx) ?: return null
    val pixels = matrix.toPixels(sizePx)

    return createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).apply {
        setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    }
}

/**
 * [payload] as a QR module matrix, or `null` when it will not fit in a code.
 *
 * Split out from [encodeQrBitmap] because everything that can be *wrong* about an encoding is
 * decided here, and none of it involves a `Bitmap` — which is what lets a test encode a payload and
 * read it straight back on the JVM. See `QrCodeEncodingTest`.
 */
internal fun encodeQrMatrix(payload: String, sizePx: Int): BitMatrix? = try {
    MultiFormatWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
            // Without this zxing encodes as ISO-8859-1, and its encoder replaces every character
            // that charset cannot hold with a literal '?'. A merchant name in Arabic, Thai or
            // Cyrillic is then destroyed *in the code itself*: it scans back as "????? ???????",
            // with a checksum that fails because tag 63 still carries the original payload's
            // value. Nothing downstream can recover it — the bytes really are question marks, and
            // the code is a faithful rendering of them.
            //
            // Declaring UTF-8 makes zxing emit an ECI segment naming the character set, so the
            // code describes its own encoding rather than leaving every reader to guess. That
            // segment is only added for a payload that actually needs byte mode; a pure-ASCII one
            // still encodes as numeric or alphanumeric and comes out byte-for-byte as before.
            EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
        ),
    )
} catch (_: WriterException) {
    // The payload exceeds what a QR code can hold at any version.
    null
} catch (_: IllegalArgumentException) {
    // Empty content.
    null
}

/** Big enough that scaling down stays crisp, small enough to encode in a couple of milliseconds. */
const val DISPLAY_SIZE_PX = 512

/**
 * Four modules, as the QR specification requires.
 *
 * ZXing defaults to a wider margin; four is the minimum that scanners rely on, and going below it
 * produces a code that renders fine and fails to scan against a busy background.
 */
private const val QUIET_ZONE_MODULES = 4

/**
 * Fixed black on white, deliberately not theme colors.
 *
 * These are not design tokens — they are the encoding. A QR code themed to a dark surface inverts
 * the module polarity and stops being readable by scanners that do not handle inversion, so this
 * is the one place in the design system where a literal color is the correct answer.
 */
private const val MODULE_DARK = Color.BLACK
private const val MODULE_LIGHT = Color.WHITE

@ShowkaseComposable(name = "QR Code", group = "Media")
@Preview(showBackground = true)
@Composable
internal fun QrCodeImagePreview() {
    AppTheme {
        QrCodeImage(payload = "https://example.com", contentDescription = null)
    }
}
