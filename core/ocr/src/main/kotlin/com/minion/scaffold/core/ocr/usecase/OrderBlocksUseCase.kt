package com.minion.scaffold.core.ocr.usecase

import com.minion.scaffold.core.ocr.model.RecognizedBlock
import javax.inject.Inject

/**
 * Puts recognised blocks into the order a person would read them.
 *
 * ML Kit returns blocks in *detection* order, which is only loosely reading order. On a plain
 * paragraph photographed straight-on the two coincide and this is a no-op; on a receipt it is not,
 * and `visionText.text` interleaves item names with prices from other lines. Since receipts and
 * forms are most of what anyone points an OCR tool at, taking ML Kit's order verbatim would be
 * wrong exactly where the feature is most used.
 *
 * The rule itself lives in [readingOrder]: group into rows by vertical overlap, order the rows top
 * to bottom, order within each row left to right. Deliberately geometric rather than layout-aware.
 *
 * **Known limitation, accepted on purpose.** Grouping is transitive, so on a true two-column page a
 * left-column block that vertically overlaps a right-column block pulls both into one row, and the
 * columns interleave. Fixing that needs column clustering, which is heuristic and misfires on
 * tables — the trade was made in favour of the simpler algorithm that is reliably right on
 * single-column text and receipts. Transitivity is also what makes receipts work: it is the same
 * mechanism that keeps an item name and its right-aligned price on one line.
 *
 * Only ML Kit's output comes through here. PaddleOCR's is ordered by
 * [GroupLinesIntoBlocksUseCase], which has to order before merging rather than after.
 */
class OrderBlocksUseCase @Inject constructor() {

    /**
     * Puts already-formed blocks into reading order.
     *
     * @param blocks The blocks to order.
     * @return The same blocks, in reading order.
     */
    operator fun invoke(blocks: List<RecognizedBlock>): List<RecognizedBlock> =
        readingOrder(blocks) { it.box }
}
