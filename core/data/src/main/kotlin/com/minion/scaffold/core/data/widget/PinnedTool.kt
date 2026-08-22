package com.minion.scaffold.core.data.widget

import com.minion.scaffold.core.toolcatalog.ToolDescriptor

/**
 * A pinned tool as a surface should draw it.
 *
 * [isAvailable] is the difference between "the catalog still has this tool, but the remote
 * configuration is currently withholding it" and "this tool is gone". Only the first is
 * representable here: a tool absent from the catalog produces no [PinnedTool] at all, because
 * there would be no title to draw and no way for it to come back.
 *
 * **No `@Immutable`, deliberately.** This module's own build file says it never imports Compose,
 * and the annotation lives in `androidx.compose.runtime`. Carrying it would mean putting the
 * Compose runtime on a data-tier module to describe a two-field holder. The cost is that Compose
 * reads this as unstable and will not skip on it; with at most five of them on screen that is not
 * a recomposition worth a module dependency. [ToolDescriptor] itself is annotated, where it
 * belongs.
 *
 * @property descriptor  The catalog entry being pinned.
 * @property isAvailable Whether the feature flags currently allow the tool to be opened.
 */
data class PinnedTool(
    val descriptor: ToolDescriptor,
    val isAvailable: Boolean,
)
