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
 * Encodes [payload] as a square QR bitmap [sizePx] on a side, or null if it will not fit.
 *
 * Public alongside [QrCodeImage] because exporting a QR — sharing it, saving it to the gallery —
 * needs the bitmap itself, at a higher resolution than the one on screen. Blocking work: call it
 * off the main thread.
 */
fun encodeQrBitmap(payload: String, sizePx: Int = DISPLAY_SIZE_PX): Bitmap? = try {
    val matrix = MultiFormatWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(EncodeHintType.MARGIN to QUIET_ZONE_MODULES),
    )

    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val rowOffset = y * sizePx
        for (x in 0 until sizePx) {
            pixels[rowOffset + x] = if (matrix[x, y]) MODULE_DARK else MODULE_LIGHT
        }
    }

    createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).apply {
        setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    }
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
