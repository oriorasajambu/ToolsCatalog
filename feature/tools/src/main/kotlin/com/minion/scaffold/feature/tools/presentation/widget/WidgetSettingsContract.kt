package com.minion.scaffold.feature.tools.presentation.widget

import androidx.compose.runtime.Immutable
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.data.widget.MAX_PINNED_TOOLS
import com.minion.scaffold.core.data.widget.PinnedTool
import com.minion.scaffold.core.toolcatalog.ToolDescriptor

/**
 * What the widget configuration screen draws.
 *
 * [pinned] is the widget's own order and [catalog] is the shipped order, and the screen renders
 * them as two blocks in exactly that arrangement. They are stored separately rather than derived
 * from one list because they answer different questions: what is on the widget, and what could be.
 *
 * @property pinned       The tools on the widget, in widget order, at most [MAX_PINNED_TOOLS].
 * @property catalog      Every tool this build ships, in catalog order.
 * @property canPinToHome Whether the launcher will accept a request to place the widget.
 */
@Immutable
internal data class WidgetSettingsState(
    val pinned: List<PinnedTool> = emptyList(),
    val catalog: List<ToolDescriptor> = emptyList(),
    val canPinToHome: Boolean = false,
) : UiState {

    /**
     * The tools not currently on the widget, in catalog order.
     *
     * An unavailable pinned tool is *not* here — it stays in the pinned block holding its slot,
     * which is what makes unpinning it the way to free one.
     */
    val unpinned: List<ToolDescriptor>
        get() {
            val pinnedIds = pinned.mapTo(mutableSetOf()) { it.descriptor.id }
            return catalog.filterNot { it.id in pinnedIds }
        }

    /** The widget is full. Every unpinned row's checkbox is disabled while this is true. */
    val isAtCap: Boolean get() = pinned.size >= MAX_PINNED_TOOLS

    /** `3` of `3/5`. */
    val pinnedCount: Int get() = pinned.size
}

/** Everything the user can do on the widget configuration screen. */
internal sealed interface WidgetSettingsIntent : UiIntent {

    /**
     * A tool was ticked or unticked.
     *
     * One intent for both directions rather than Pin and Unpin: the row is a checkbox, the
     * ViewModel already knows which state it is in, and two intents would let a screen ask for a
     * pin on something already pinned.
     *
     * @property toolId The tool whose checkbox was tapped.
     */
    data class ToggleTool(val toolId: String) : WidgetSettingsIntent

    /**
     * A pinned tool was dragged to a new position.
     *
     * Carries indices rather than ids because that is what a drag produces, and because all the
     * ordering logic then lives in the ViewModel where it can be tested without gesture code.
     *
     * @property from Index in [WidgetSettingsState.pinned] the tool came from.
     * @property to   Index it was dropped at.
     */
    data class Reorder(val from: Int, val to: Int) : WidgetSettingsIntent

    /** The "Add to home screen" button was pressed. */
    data object PinWidgetRequested : WidgetSettingsIntent
}

/**
 * This screen has no one-shot events.
 *
 * The pin request looked like one — it opens a system dialog — but the dialog belongs to the
 * launcher rather than to this screen, and routing it through an effect would mean the composable
 * holding a `WidgetPinRequester` only to hand it straight back. It goes through the ViewModel
 * instead. The type stays because the MVI contract is three types, and a screen that later needs a
 * snackbar should not have to change shape to get one.
 */
internal sealed interface WidgetSettingsEffect : UiEffect
