package com.minion.scaffold.core.ocr.usecase

import com.minion.scaffold.core.ocr.model.BoundingBox
import com.minion.scaffold.core.ocr.model.RecognizedBlock
import com.minion.scaffold.core.ocr.model.RecognizedLine
import javax.inject.Inject

/**
 * Gathers individual recognised lines into paragraph-sized blocks.
 *
 * PaddleOCR's detector finds *lines*; ML Kit reports *blocks*. Passing lines straight through would
 * make the two engines behave differently in the one place the user is trying to compare them — and
 * it would turn a receipt into forty tappable boxes instead of five, so "drop the footer" becomes
 * fifteen taps. This normalises PaddleOCR onto ML Kit's granularity.
 *
 * Two lines join the same block when they overlap horizontally and sit close together vertically.
 * The vertical threshold is expressed as a fraction of the *median* line height rather than an
 * absolute pixel count, because the same page photographed from twice the distance has half the
 * line height and must group identically.
 *
 * Each line is offered to whichever open block it overlaps *most*, not simply the last one seen:
 * on a two-column layout the greedy choice would alternate between columns and merge them into one
 * block, while picking the best overlap keeps them apart.
 *
 * Output is in no particular order — [OrderBlocksUseCase] runs afterwards, exactly as it does for
 * ML Kit's blocks.
 */
class GroupLinesIntoBlocksUseCase @Inject constructor() {

    operator fun invoke(lines: List<RecognizedLine>): List<RecognizedBlock> {
        if (lines.isEmpty()) return emptyList()

        // Top-first, so a block is always seeded by its highest line and every later line is
        // compared against a block whose bottom edge is already final for the lines seen so far.
        val ordered = lines.sortedBy { it.box.top }
        val medianHeight = ordered.map { it.box.height }.sorted()[ordered.size / 2]
        val maxGap = (medianHeight * GAP_FRACTION).coerceAtLeast(1f)

        val blocks = mutableListOf<MutableList<RecognizedLine>>()

        for (line in ordered) {
            val host = blocks
                .filter { block -> block.canAccept(line, maxGap) }
                .maxByOrNull { block -> block.bounds().horizontalOverlapWith(line.box) }

            if (host != null) host.add(line) else blocks.add(mutableListOf(line))
        }

        return blocks.mapIndexed { index, block -> block.toBlock(index) }
    }

    /**
     * Whether [line] belongs with the lines already in this block.
     *
     * The gap is measured from the block's *bottom* edge, so a line that overlaps the block
     * vertically gives a negative gap and is always close enough — which is what keeps a line
     * detected at a slight angle attached to its paragraph.
     */
    private fun List<RecognizedLine>.canAccept(line: RecognizedLine, maxGap: Float): Boolean {
        val bounds = bounds()
        val gap = line.box.top - bounds.bottom

        return gap <= maxGap &&
            bounds.horizontalOverlapWith(line.box) >= MIN_HORIZONTAL_OVERLAP
    }

    private fun List<RecognizedLine>.bounds(): BoundingBox =
        map { it.box }.reduce(BoundingBox::union)

    private fun List<RecognizedLine>.toBlock(index: Int): RecognizedBlock {
        // Sorted here rather than relying on insertion order: a line can be added to an older block
        // after a newer one was started, so the list is not necessarily top-to-bottom by the end.
        val sorted = sortedBy { it.box.top }

        return RecognizedBlock(
            // The index is the only stable handle available, and stable within one recognition is
            // all it needs to be — ids are regenerated each time and never persisted. Same
            // reasoning as the ML Kit mapper's.
            id = index.toString(),
            text = sorted.joinToString(separator = "\n") { it.text },
            box = bounds(),
            // The weakest line, not the mean: a block containing one badly-read line is worth
            // proofreading even if its other six lines were perfect, and averaging hides exactly
            // that case.
            confidence = sorted.mapNotNull { it.confidence }.minOrNull(),
        )
    }

    private companion object {

        /**
         * How far below a block's last line the next line may start, as a fraction of the median
         * line height.
         *
         * Below one line height, so ordinary line spacing within a paragraph joins while a blank
         * line separates. Tuned to split on a deliberate paragraph break rather than on the leading
         * of a single body of text; worth revisiting against real documents rather than treating as
         * exact.
         */
        const val GAP_FRACTION = 0.8f

        /**
         * How much of the narrower box must overlap horizontally to join.
         *
         * Low, because a short last line of a paragraph — or an indented one — still belongs to it.
         * Its job is only to keep two side-by-side columns from merging.
         */
        const val MIN_HORIZONTAL_OVERLAP = 0.3f
    }
}
