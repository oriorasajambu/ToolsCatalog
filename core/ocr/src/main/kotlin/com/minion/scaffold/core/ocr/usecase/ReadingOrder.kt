package com.minion.scaffold.core.ocr.usecase

import com.minion.scaffold.core.ocr.model.BoundingBox

/**
 * Puts boxed items into the order a person would read them: rows top to bottom, and left to right
 * within a row.
 *
 * Shared by [OrderBlocksUseCase], which sorts ML Kit's blocks, and [GroupLinesIntoBlocksUseCase],
 * which sorts PaddleOCR's lines before merging them. One implementation because the rule is the
 * same; generic because the two call it with different types.
 *
 * **This works well only when the items are of similar height.** Grouping is transitive, so one
 * unusually tall box vertically overlaps everything beside it and drags the lot into a single row,
 * where their left edges tie and the order becomes arbitrary. That is precisely why
 * [GroupLinesIntoBlocksUseCase] orders *lines* and merges afterwards, rather than merging first and
 * ordering the resulting blocks — a receipt's item list merged into one tall block would otherwise
 * swallow every price in the column beside it.
 */
internal fun <T> readingOrder(items: List<T>, box: (T) -> BoundingBox): List<T> {
    if (items.size < 2) return items

    // Seeded top-first so each row is started by its highest item, which makes the grouping below
    // deterministic rather than dependent on the recognizer's arbitrary detection order.
    val remaining = items.sortedBy { box(it).top }.toMutableList()
    val rows = mutableListOf<List<T>>()

    while (remaining.isNotEmpty()) {
        val row = mutableListOf(remaining.removeAt(0))

        // Repeated until nothing new joins: an item that shares a row with one added on this pass
        // may not have shared one with the seed. A single pass would drop the far end of a long
        // line — the price at the right edge of a wide receipt, typically.
        do {
            val joined = remaining.filter { candidate ->
                row.any { box(it).sharesRowWith(box(candidate)) }
            }
            remaining.removeAll(joined)
            row.addAll(joined)
        } while (joined.isNotEmpty())

        rows.add(row)
    }

    return rows
        .sortedBy { row -> row.minOf { box(it).top } }
        .flatMap { row -> row.sortedBy { box(it).left } }
}
