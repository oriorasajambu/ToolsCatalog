package com.minion.scaffold.widget

import com.minion.scaffold.core.domain.featureflag.FeatureFlags
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.toolcatalog.ToolDescriptor

/**
 * The contract between a widget tile and the activity it opens.
 *
 * Both halves live in `:app` because both halves *are* `:app`: it builds the intent, through the
 * `WidgetLaunchIntentFactory` it binds, and it reads the intent back in `MainActivity`. The widget
 * module never names the extra — it asks for an intent and starts it.
 */
internal object WidgetLaunch {

    /**
     * The tool to open, as a catalog id.
     *
     * A string id rather than a serialized route. A route would be type-safe end to end but puts a
     * kotlinx-serialized payload through a boundary R8 processes — the enum-renaming trap
     * `ScanPurpose`'s `@Keep` exists to close — and would make the widget depend on the serializer.
     * An id has neither problem, and it is also what keeps `qr-scan` and `qr-edit` distinguishable
     * when both resolve to the same route.
     */
    const val EXTRA_TOOL_ID: String = "com.minion.scaffold.widget.EXTRA_TOOL_ID"
}

/**
 * Works out where a widget tap should land.
 *
 * Pure, and the launch path's weak point, so it is decided here rather than inside an activity
 * where it could only be tested with one.
 *
 * `null` means "do nothing special" and the app opens on its start destination as usual. Three
 * different situations resolve to it, and all three should:
 *
 *  - **No extra.** An ordinary launcher tap.
 *  - **An id the catalog does not have.** A stale intent from a widget that outlived a tool, or a
 *    pinned list a downgrade left behind.
 *  - **An id whose flag is off.** Both the greyed tile's own tap and a tile that was available
 *    when the widget last drew but is not now. Neither should open something the console is
 *    currently withholding.
 *
 * @param toolId  The id carried by the intent, or `null` when there was none.
 * @param catalog The tools this build ships.
 * @param flags   The switches currently in force.
 * @return The route to open, or `null` to leave the app on its start destination.
 */
internal fun resolveWidgetRoute(
    toolId: String?,
    catalog: List<ToolDescriptor>,
    flags: FeatureFlags,
): AppRoute? =
    toolId
        ?.let { id -> catalog.firstOrNull { it.id == id } }
        ?.takeIf { flags.isEnabled(it.id) }
        ?.route
