package com.minion.scaffold.feature.tools.presentation

import com.minion.scaffold.core.toolcatalog.ToolCatalog
import com.minion.scaffold.core.toolcatalog.ToolCategory
import com.minion.scaffold.core.toolcatalog.ToolDescriptor
import androidx.compose.runtime.Immutable
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.navigation.AppRoute

/**
 * The home screen's state: which tools are on show, and how they group.
 *
 * One field. The four lists the layout is built from are computed properties rather than stored
 * ones, so there is exactly one place a tool can be present — a stored `utilities` alongside a
 * stored `tools` is two answers to the same question, and they drift.
 *
 * The default is the **whole** catalog, deliberately. The switches fail open (see
 * [com.minion.scaffold.core.domain.featureflag.FeatureFlags]), the in-app Remote Config defaults
 * are all `true`, and the first snapshot arrives before the first frame — so the screen has never
 * got anything to gain from starting empty and populating. Starting empty would only buy a visible
 * flash of nothing on every cold start.
 *
 * @property tools The catalog filtered to what the remote configuration currently allows, in
 *                 catalog order.
 */
@Immutable
internal data class ToolsState(
    val tools: List<ToolDescriptor> = ToolCatalog.entries,
) : UiState {

    /**
     * The tool promoted to the hero card, or `null` if it has been switched off.
     *
     * Nothing is promoted in its place. The hero is a specific piece of layout built around the
     * scanner — its own copy, its own scanline QR — and quietly substituting a different tool into
     * it would show that copy over the wrong feature.
     */
    val hero: ToolDescriptor? get() = tools.firstOrNull { it.id == ToolCatalog.HERO_ID }

    /** Reader tools other than the hero — the edit entry, shown as a slim card beneath it. */
    val secondaryReaders: List<ToolDescriptor>
        get() = tools.filter { it.category == ToolCategory.Reader && it.id != ToolCatalog.HERO_ID }

    /** Tools that write a code, listed in the Create section. */
    val creators: List<ToolDescriptor> get() = tools.filter { it.category == ToolCategory.Create }

    /** Everything else, gridded under Utilities. */
    val utilities: List<ToolDescriptor> get() = tools.filter { it.category == ToolCategory.Utility }
}

/** Everything the user can do on the home screen. */
internal sealed interface ToolsIntent : UiIntent {

    /**
     * A tool card was tapped.
     *
     * Carries the [ToolDescriptor] rather than its route so the ViewModel keeps the option of doing
     * something with the identity — logging which tool was opened, say — without the screen
     * having to change.
     *
     * @property tool The tool the user selected.
     */
    data class ToolSelected(val tool: ToolDescriptor) : ToolsIntent
}

/** One-shot events from the home screen. */
internal sealed interface ToolsEffect : UiEffect {

    /**
     * Open a tool.
     *
     * An effect and not state: replaying it after a rotation would navigate a user who has just
     * come back to the home screen straight out of it again.
     *
     * @property route The route of the selected tool.
     */
    data class OpenTool(val route: AppRoute) : ToolsEffect
}
