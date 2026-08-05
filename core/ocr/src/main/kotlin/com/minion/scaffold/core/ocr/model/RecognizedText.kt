package com.minion.scaffold.core.ocr.model

/**
 * Everything recognised in one image, already in reading order.
 *
 * "Already ordered" is an invariant of this type, not a suggestion:
 * [com.minion.scaffold.core.ocr.usecase.OrderBlocksUseCase] runs before construction, so nothing
 * downstream has to re-sort or wonder whether it should.
 */
data class RecognizedText(
    val blocks: List<RecognizedBlock>,
) {

    val isEmpty: Boolean get() = blocks.isEmpty()

    companion object {
        val EMPTY = RecognizedText(blocks = emptyList())
    }
}
