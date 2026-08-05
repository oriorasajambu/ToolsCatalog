package com.minion.scaffold.core.ocr.usecase

import com.minion.scaffold.core.ocr.model.RecognizedText
import javax.inject.Inject

/**
 * Joins the blocks the user kept into the final string.
 *
 * Blocks arrive already in reading order (see [OrderBlocksUseCase]), so this only has to decide
 * separators — one newline between blocks of a capture, a blank line between captures. The blank
 * line matters for multi-capture: without it two pages run together and the seam is invisible.
 */
class AssembleTextUseCase @Inject constructor() {

    /** One capture's selected blocks. [selectedIds] preserves nothing — order comes from [text]. */
    operator fun invoke(text: RecognizedText, selectedIds: Set<String>): String =
        text.blocks
            .filter { it.id in selectedIds }
            .joinToString(BLOCK_SEPARATOR) { it.text }

    /**
     * Several captures, in the order they were taken.
     *
     * Captures that contributed nothing are dropped rather than leaving a run of blank lines
     * behind — a page whose blocks were all deselected should vanish, not leave a gap.
     */
    fun across(captures: List<Pair<RecognizedText, Set<String>>>): String =
        captures
            .map { (text, selectedIds) -> invoke(text, selectedIds) }
            .filter { it.isNotBlank() }
            .joinToString(CAPTURE_SEPARATOR)

    private companion object {
        const val BLOCK_SEPARATOR = "\n"
        const val CAPTURE_SEPARATOR = "\n\n"
    }
}
