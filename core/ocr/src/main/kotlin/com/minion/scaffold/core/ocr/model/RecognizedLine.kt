package com.minion.scaffold.core.ocr.model

/**
 * One line of recognised text.
 *
 * Exists because the two engines report at different granularities: ML Kit hands back blocks
 * (roughly paragraphs) directly, while PaddleOCR's detector finds individual *lines* and reads each
 * one separately. A line is not a useful thing to select at — deselecting a receipt's footer would
 * be fifteen taps instead of one — so lines are grouped into [RecognizedBlock]s by
 * [com.minion.scaffold.core.ocr.usecase.GroupLinesIntoBlocksUseCase] before they reach the UI.
 *
 * No id: lines are an intermediate shape that never reaches the overlay, and identity is assigned
 * when the blocks are formed.
 */
data class RecognizedLine(
    val text: String,
    val box: BoundingBox,

    /** 0..1, or `null` when the recognizer did not report one. See [RecognizedBlock.confidence]. */
    val confidence: Float?,
)
