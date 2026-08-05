package com.minion.scaffold.feature.ocr.presentation

import android.graphics.Bitmap
import com.minion.scaffold.core.ocr.model.BoundingBox
import com.minion.scaffold.core.ocr.model.RecognizedBlock
import com.minion.scaffold.core.ocr.model.RecognizedText
import com.minion.scaffold.feature.ocr.data.OcrResult
import com.minion.scaffold.feature.ocr.data.TextRecognizer

/**
 * A scripted [TextRecognizer], so the ViewModel can be tested without ML Kit or a real image.
 *
 * [results] is a queue rather than a single value: rotate-and-retry recognises twice, and the
 * whole point of that test is that the second answer differs from the first.
 */
internal class FakeTextRecognizer : TextRecognizer {

    private val results = ArrayDeque<OcrResult>()

    var callCount = 0
        private set

    fun enqueue(vararg outcomes: OcrResult) {
        results.addAll(outcomes)
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        callCount++
        return results.removeFirstOrNull() ?: OcrResult.NoText
    }

    companion object {

        fun found(vararg lines: String) = OcrResult.Found(textOf(*lines))

        fun textOf(vararg lines: String) = RecognizedText(
            blocks = lines.mapIndexed { index, line ->
                RecognizedBlock(
                    id = index.toString(),
                    text = line,
                    box = BoundingBox(0, index * 100, 100, index * 100 + 20),
                    confidence = null,
                )
            },
        )
    }
}
