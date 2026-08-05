package com.minion.scaffold.feature.ocr.data

import android.graphics.Bitmap
import com.minion.scaffold.core.ocr.model.RecognizedText

/**
 * Reads text out of an already-decoded, already-upright bitmap.
 *
 * An interface purely so the ViewModel is testable: the real implementation needs a native ML Kit
 * detector, which does not exist in a JVM unit test. Same seam, and same reason, as
 * `:feature:qrscan`'s `ImageBarcodeDecoder`.
 *
 * Takes a `Bitmap` rather than a `Uri` so both entry points — camera capture and gallery pick —
 * converge before they reach here. The caller loads the image, which is also what lets the
 * block-selection overlay draw the very same bitmap that was recognised.
 */
internal interface TextRecognizer {

    suspend fun recognize(bitmap: Bitmap): OcrResult
}

/**
 * Three outcomes, not two.
 *
 * "Read the image and it holds no text" and "could not decode the image at all" lead the user to
 * different next moves — reframe versus pick a different file — so collapsing them into one
 * failure would tell someone their perfectly good photo of a blank wall was corrupt.
 * `:feature:qrscan`'s `ImageDecodeResult` splits the same way.
 */
internal sealed interface OcrResult {

    data class Found(val text: RecognizedText) : OcrResult

    /** The image was fine; the model found nothing in it. */
    data object NoText : OcrResult

    /** Recognition itself failed. */
    data object Unreadable : OcrResult
}
