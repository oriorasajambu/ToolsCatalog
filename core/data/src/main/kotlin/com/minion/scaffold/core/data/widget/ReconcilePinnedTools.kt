package com.minion.scaffold.core.data.widget

import com.minion.scaffold.core.domain.featureflag.FeatureFlags
import com.minion.scaffold.core.toolcatalog.ToolDescriptor

/** How many tools the widget holds. Counts pinned ids, available or not. */
const val MAX_PINNED_TOOLS: Int = 5

/**
 * What a reconcile pass concluded: what to draw, and what to keep.
 *
 * Two lists rather than one because they answer different questions. [tools] is the render, and a
 * flag-disabled tool is in it — greyed. [retainedIds] is what should be written back, and differs
 * from the stored list exactly when something was pruned, de-duplicated or truncated.
 *
 * @property tools       The tools to render, in display order, at most [MAX_PINNED_TOOLS].
 * @property retainedIds The ids worth persisting, in the same order.
 */
data class ReconcileResult(
    val tools: List<PinnedTool>,
    val retainedIds: List<String>,
)

/**
 * Reconciles a stored pin list against the catalog that shipped and the flags in force.
 *
 * The one piece of genuine logic behind the widget, and pure, so it can be proved in a JVM test
 * rather than inferred from a rendered strip.
 *
 * The rules, in order:
 *
 *  1. **An unknown id is dropped.** No catalog entry means the tool no longer ships. Keeping it
 *     would mean a permanent placeholder tile for a situation that cannot resolve itself.
 *  2. **A known but flag-disabled id is retained**, and surfaces with `isAvailable = false`. A
 *     flag is reversible, so the user's arrangement should survive a console mistake or a
 *     temporary kill switch.
 *  3. **Duplicates collapse to the first occurrence.** Defensive; the configuration screen cannot
 *     produce one.
 *  4. **The result truncates to [MAX_PINNED_TOOLS]**, after the rules above.
 *
 * Seeding a first-run default is deliberately *not* done here. An empty stored list reconciles to
 * an empty result, because "the user unpinned everything" has to stay representable — conflating
 * it with "nothing stored yet" hands the defaults back to someone who just cleared them.
 *
 * @param storedIds The ids as persisted, in display order.
 * @param catalog   The tools this build actually ships.
 * @param flags     The switches currently in force.
 * @return What to draw and what to keep.
 */
fun reconcilePinnedTools(
    storedIds: List<String>,
    catalog: List<ToolDescriptor>,
    flags: FeatureFlags,
): ReconcileResult {
    val byId = catalog.associateBy { it.id }

    val tools = storedIds
        .asSequence()
        .distinct()
        .mapNotNull { byId[it] }
        .take(MAX_PINNED_TOOLS)
        .map { PinnedTool(descriptor = it, isAvailable = flags.isEnabled(it.id)) }
        .toList()

    return ReconcileResult(tools = tools, retainedIds = tools.map { it.descriptor.id })
}
