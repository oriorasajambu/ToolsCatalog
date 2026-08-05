package com.minion.scaffold.core.ocr.usecase

import com.minion.scaffold.core.ocr.model.BoundingBox
import com.minion.scaffold.core.ocr.model.RecognizedBlock
import com.minion.scaffold.core.ocr.model.RecognizedLine
import javax.inject.Inject

/**
 * Gathers individual recognised lines into paragraph-sized blocks, in reading order.
 *
 * PaddleOCR's detector finds *lines*; ML Kit reports *blocks*. Passing lines straight through would
 * make the two engines behave differently in the one place the user is trying to compare them, and
 * would turn a page of prose into dozens of tappable boxes instead of a handful.
 *
 * **Order first, merge second — not the other way around.** Merging first and then running
 * [OrderBlocksUseCase] over the result looks equivalent and is not: a receipt's item list merges
 * into one tall block, which then vertically overlaps every price in the column beside it, pulls
 * them all into a single row, and — their left edges being equal — leaves their order arbitrary.
 * The prices end up detached from their items and shuffled. Ordering the *lines* first avoids it
 * entirely, because lines on one printed row genuinely do overlap each other and nothing else.
 *
 * Consequently only *consecutive* lines merge. Two columns stay interleaved line by line, which is
 * how they read; a paragraph's lines are consecutive and merge as one.
 *
 * The vertical threshold is a fraction of the *median* line height rather than an absolute pixel
 * count, because the same page photographed from twice the distance has half the line height and
 * must group identically.
 */
class GroupLinesIntoBlocksUseCase @Inject constructor() {

    operator fun invoke(lines: List<RecognizedLine>): List<RecognizedBlock> {
        if (lines.isEmpty()) return emptyList()

        val ordered = readingOrder(lines) { it.box }
        val medianHeight = ordered.map { it.box.height }.sorted()[ordered.size / 2]
        val maxGap = (medianHeight * GAP_FRACTION).coerceAtLeast(1f)

        val blocks = mutableListOf<MutableList<RecognizedLine>>()

        for (line in ordered) {
            val open = blocks.lastOrNull()

            if (open != null && open.canAccept(line, maxGap)) {
                open.add(line)
            } else {
                blocks.add(mutableListOf(line))
            }
        }

        return blocks.mapIndexed { index, block -> block.toBlock(index) }
    }

    /**
     * Whether [line] continues the block being built.
     *
     * Measured against the block's accumulated bounds, so a paragraph whose lines drift slightly
     * still holds together. The gap comes from the block's *bottom* edge, so a line overlapping it
     * vertically gives a negative gap and is always close enough.
     */
    private fun List<RecognizedLine>.canAccept(line: RecognizedLine, maxGap: Float): Boolean {
        val bounds = bounds()
        val gap = line.box.top - bounds.bottom

        return gap <= maxGap &&
            bounds.horizontalOverlapWith(line.box) >= MIN_HORIZONTAL_OVERLAP
    }

    private fun List<RecognizedLine>.bounds(): BoundingBox =
        map { it.box }.reduce(BoundingBox::union)

    private fun List<RecognizedLine>.toBlock(index: Int): RecognizedBlock = RecognizedBlock(
        // The index is the only stable handle available, and stable within one recognition is all
        // it needs to be — ids are regenerated each time and never persisted. Same reasoning as the
        // ML Kit mapper's.
        id = index.toString(),
        // Already in reading order, so insertion order is the order to join in.
        text = joinToString(separator = "\n") { it.text },
        box = bounds(),
        // The weakest line, not the mean: a block containing one badly-read line is worth
        // proofreading even if its other six lines were perfect, and averaging hides exactly that.
        confidence = mapNotNull { it.confidence }.minOrNull(),
    )

    private companion object {

        /**
         * How far below a block's last line the next line may start, as a fraction of the median
         * line height.
         *
         * Below one line height, so ordinary line spacing within a paragraph joins while a blank
         * line separates. Worth revisiting against real documents rather than treating as exact.
         */
        const val GAP_FRACTION = 0.8f

        /**
         * How much of the narrower box must overlap horizontally to join.
         *
         * Low, because a short last line of a paragraph — or an indented one — still belongs to it.
         * Its job is only to keep two side-by-side columns apart.
         */
        const val MIN_HORIZONTAL_OVERLAP = 0.3f
    }
}
