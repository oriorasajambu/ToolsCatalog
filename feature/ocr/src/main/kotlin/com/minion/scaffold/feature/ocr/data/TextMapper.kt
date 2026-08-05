package com.minion.scaffold.feature.ocr.data

import com.google.mlkit.vision.text.Text
import com.minion.scaffold.core.ocr.model.BoundingBox
import com.minion.scaffold.core.ocr.model.RecognizedBlock
import com.minion.scaffold.core.ocr.model.RecognizedText
import com.minion.scaffold.core.ocr.usecase.OrderBlocksUseCase

/**
 * ML Kit `Text` → the pure models in `:core:ocr`.
 *
 * The boundary exists so the ordering algorithm can be unit-tested without an emulator: ML Kit's
 * `Text` is final, un-constructible and carries `android.graphics.Rect`, none of which survives in
 * a JVM test. Same split as `:feature:weather`'s Open-Meteo DTOs.
 *
 * Ordering is applied *here*, not later, so [RecognizedText]'s "already in reading order" invariant
 * holds from the moment it exists.
 */
internal fun Text.toRecognizedText(orderBlocks: OrderBlocksUseCase): RecognizedText {
    val blocks = textBlocks.mapIndexedNotNull { index, block ->
        val box = block.boundingBox ?: return@mapIndexedNotNull null
        val text = block.text.trim().takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null

        RecognizedBlock(
            // ML Kit gives blocks no identity, and the text is not unique — a receipt repeats
            // "1.00" — so the index is the only stable handle for selection. Stable is all it has
            // to be: it is regenerated with every recognition and never persisted.
            id = index.toString(),
            text = text,
            box = BoundingBox(box.left, box.top, box.right, box.bottom),
            confidence = block.averageConfidence(),
        )
    }

    return RecognizedText(blocks = orderBlocks(blocks))
}

/**
 * Mean confidence across the block's elements.
 *
 * ML Kit reports confidence per element rather than per block, so this averages rather than
 * inventing one. Null when nothing reported — a block whose elements all decline to score is
 * "unknown", not "perfect", and [RecognizedBlock.confidence] is nullable to say so.
 */
private fun Text.TextBlock.averageConfidence(): Float? {
    val scores = lines.flatMap { it.elements }.mapNotNull { it.confidence }
    return scores.takeIf { it.isNotEmpty() }?.average()?.toFloat()
}
