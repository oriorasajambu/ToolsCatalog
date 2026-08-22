package com.minion.scaffold.feature.tools.presentation.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Drag-to-reorder for the pinned block.
 *
 * Hand-rolled because the list is short, fixed-height and never scrolls far — the cases a general
 * reorderable-list library exists to handle. What it deliberately does *not* do is decide the new
 * order: it converts a pointer offset into a pair of indices and hands them to the ViewModel,
 * which is what keeps the ordering rules testable without a gesture in sight.
 *
 * Rows are a uniform height, so the target index is the drag distance divided by that height. A
 * per-item position map would be more general and would buy nothing here.
 */
internal class ReorderState(private val onMove: (from: Int, to: Int) -> Unit) {

    /** Which row is being dragged, or null when none is. */
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    /** How far it has been dragged, in pixels, since the long press. */
    var offsetY by mutableFloatStateOf(0f)
        private set

    /** One row's height, in pixels. Reported by the first row that measures itself. */
    var rowHeightPx by mutableIntStateOf(0)

    /** How many rows there are, so a drag cannot target past the end. */
    var count by mutableIntStateOf(0)

    /** True while [index] is the row under the finger. */
    fun isDragging(index: Int): Boolean = draggingIndex == index

    fun start(index: Int) {
        draggingIndex = index
        offsetY = 0f
    }

    fun drag(delta: Float) {
        offsetY += delta
    }

    /**
     * Commits the move, if the row travelled far enough to land on a different slot.
     *
     * The ViewModel refuses an out-of-range pair anyway; clamping here as well means a drag past
     * either end settles on the last real slot instead of being silently discarded, which is what
     * a user dragging to the top of a list is asking for.
     */
    fun end() {
        val from = draggingIndex ?: return
        val height = rowHeightPx

        if (height > 0) {
            val target = (from + (offsetY / height).roundToInt()).coerceIn(0, (count - 1).coerceAtLeast(0))
            if (target != from) onMove(from, target)
        }

        cancel()
    }

    fun cancel() {
        draggingIndex = null
        offsetY = 0f
    }
}

/**
 * Remembers a [ReorderState] for the lifetime of the screen.
 *
 * @param onMove Called with the indices a completed drag resolved to.
 * @return The state to hand to the pinned rows.
 */
@Composable
internal fun rememberReorderState(onMove: (from: Int, to: Int) -> Unit): ReorderState =
    remember { ReorderState(onMove) }
