package com.minion.scaffold.feature.ocr.data

import android.graphics.Bitmap
import com.minion.scaffold.core.ocr.model.OcrEngine
import com.minion.scaffold.core.ocr.model.RecognizedText

/**
 * One recognition engine — ML Kit or PaddleOCR.
 *
 * Reads text out of an already-decoded, already-upright bitmap. An interface purely so the
 * ViewModel is testable: the real implementations need a native detector, which does not exist in a
 * JVM unit test. Same seam, and same reason, as `:feature:qrscan`'s `ImageBarcodeDecoder`.
 *
 * Takes a `Bitmap` rather than a `Uri` so both entry points — camera capture and gallery pick —
 * converge before they reach here. The caller loads the image, which is also what lets the
 * block-selection overlay draw the very same bitmap that was recognised.
 *
 * An engine does not report which engine it is; that is [TextRecognizer]'s job, because only the
 * thing that made the choice can say whether the choice was honoured.
 */
internal interface TextRecognitionEngine {

    suspend fun recognize(bitmap: Bitmap): OcrResult
}

/**
 * What the rest of the feature recognises through.
 *
 * Distinct from [TextRecognitionEngine] because a recognition has two answers, not one: what was
 * read, and which engine read it. The second only exists because the selected engine can fail to
 * start — see `SelectingTextRecognizer` — and a fallback the user is not told about is a setting
 * that quietly lies.
 */
internal interface TextRecognizer {

    suspend fun recognize(bitmap: Bitmap): Recognition

    /** Drops any engine resources held for the screen. Safe to call more than once. */
    fun release()
}

/** A recognition, and the engine that actually performed it — not necessarily the one selected. */
internal data class Recognition(
    val result: OcrResult,
    val engine: OcrEngine,
)

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
