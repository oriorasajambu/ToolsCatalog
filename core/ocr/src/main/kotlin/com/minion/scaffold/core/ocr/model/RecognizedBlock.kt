package com.minion.scaffold.core.ocr.model

/**
 * One block of recognised text — ML Kit's coarsest unit, roughly a paragraph.
 *
 * Blocks are the granularity the user selects at, so this is what the overlay draws and what
 * [com.minion.scaffold.core.ocr.usecase.AssembleTextUseCase] joins.
 */
data class RecognizedBlock(
    val id: String,
    val text: String,
    val box: BoundingBox,

    /**
     * Mean recognition confidence over the block, 0..1, or `null` when the recognizer did not
     * report one.
     *
     * Nullable rather than defaulted to 1.0: "the model is certain" and "the model did not say"
     * are different claims, and a default would quietly render every block as confident on a
     * version that stops reporting it.
     */
    val confidence: Float?,
) {

    /** Whether this block is shaky enough to be worth flagging for proofreading. */
    val isLowConfidence: Boolean get() = confidence != null && confidence < LOW_CONFIDENCE_THRESHOLD

    private companion object {

        /**
         * Below this, the block gets a visual warning in the result screen.
         *
         * Chosen to flag the genuinely doubtful without painting half a normal photo — ML Kit
         * scores clean printed text well above this, and drops sharply on blur, glare and skew.
         * Tune against real captures rather than treating it as exact.
         */
        const val LOW_CONFIDENCE_THRESHOLD = 0.7f
    }
}
