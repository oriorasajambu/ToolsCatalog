package com.minion.scaffold.feature.weather.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Long-press-drag reordering for the saved-location list.
 *
 * Hand-rolled rather than pulled from a library, and deliberately simple: it assumes every saved
 * card is [itemHeightPx] tall, which is true because this screen gives them a fixed height. That
 * assumption is what makes the arithmetic below a threshold comparison instead of the usual
 * measure-every-neighbour dance — and it is why the card's height and this value have to stay in
 * step.
 *
 * The list is reordered *live* as the drag crosses each threshold, rather than once on drop: the
 * cards that are not being dragged then animate into their new places on their own (via
 * `animateItem`), so the gesture shows its result as it happens instead of rearranging everything
 * at the end.
 */
@Stable
internal class SavedCardDragState(private val itemHeightPx: Float) {

    /** The card currently under the finger, or null when no drag is in progress. */
    var draggingId: String? by mutableStateOf(null)
        private set

    /**
     * How far the dragged card is drawn from its slot.
     *
     * Reset by one item height every time a swap happens, so it only ever holds the *residual*
     * within the current slot — otherwise the card would run away from the finger by one full row
     * per swap.
     */
    var offsetY: Float by mutableFloatStateOf(0f)
        private set

    fun onDragStart(id: String) {
        draggingId = id
        offsetY = 0f
    }

    /**
     * @param currentIndex where the dragged card sits *now* — it changes as swaps happen, so the
     *   caller re-reads it from the list rather than the drag remembering where it started.
     */
    fun onDrag(deltaY: Float, currentIndex: Int, itemCount: Int, onMove: (Int, Int) -> Unit) {
        offsetY += deltaY
        val threshold = itemHeightPx / 2f

        when {
            offsetY > threshold && currentIndex < itemCount - 1 -> {
                onMove(currentIndex, currentIndex + 1)
                offsetY -= itemHeightPx
            }

            offsetY < -threshold && currentIndex > 0 -> {
                onMove(currentIndex, currentIndex - 1)
                offsetY += itemHeightPx
            }
        }
    }

    fun onDragStop() {
        draggingId = null
        offsetY = 0f
    }
}

@Composable
internal fun rememberSavedCardDragState(itemHeightPx: Float): SavedCardDragState =
    remember(itemHeightPx) { SavedCardDragState(itemHeightPx) }
