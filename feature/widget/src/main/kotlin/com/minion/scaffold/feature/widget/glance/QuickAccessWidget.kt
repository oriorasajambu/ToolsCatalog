package com.minion.scaffold.feature.widget.glance

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
// Parked with the tile labels, which were the only reader of the widget's measured size.
// import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.minion.scaffold.core.data.widget.PinnedTool
import com.minion.scaffold.core.data.widget.reconcilePinnedTools
import com.minion.scaffold.core.toolcatalog.ToolCatalog
import com.minion.scaffold.feature.widget.R
import com.minion.scaffold.feature.widget.WidgetEntryPoint
import com.minion.scaffold.feature.widget.WidgetLaunchIntentFactory
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

/** Icon only. */
private val COMPACT_SIZE = DpSize(250.dp, 50.dp)

/**
 * Icon above a single-line label.
 *
 * Its height doubles as the breakpoint the tile reads, so the size the widget is laid out at and
 * the size it decides against cannot disagree.
 */
private val REGULAR_SIZE = DpSize(250.dp, 70.dp)

// ---------------------------------------------------------------------------------------------
// Tile labels are parked, not deleted. Icon-only for now, while what a widget tile should be
// labelled with is settled properly.
//
// What was here worked: labels appeared at three or four tools and dropped at five, tested on the
// width each tile actually gets rather than on the widget's height — five across four cells gives
// each about 50dp, where three of them truncated to the identical "Crea...". Restoring it is
// uncommenting this block, the two lines in QuickAccessStrip, the `showLabel` parameter, and the
// `if (showLabel)` body in ToolTile.
//
// The content description is deliberately *not* part of this. Icon-only is a visual decision and
// never a semantic one: a TalkBack user still hears every tool's name, and an unavailable tile
// still says so.
//
// private val MIN_LABEL_TILE_WIDTH = 60.dp
// ---------------------------------------------------------------------------------------------

/**
 * A strip of up to five tools, straight onto the home screen.
 *
 * Tiles are launchers and nothing more — no reading, no last-scanned code. Everything drawn is
 * decided at render time from the pinned list and the flags, which is why `updatePeriodMillis` is
 * zero and every redraw is triggered explicitly.
 */
internal class QuickAccessWidget : GlanceAppWidget() {

    /**
     * Two breakpoints, on height.
     *
     * A launcher strip gains nothing from more, and every extra size is one more thing to lay out
     * and check. Below [REGULAR_SIZE] a label plus an icon stops clearing the minimum touch target
     * with anything legible left over, so the label goes rather than shrinking.
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, REGULAR_SIZE))

    /**
     * Reads, reconciles, renders. Never writes.
     *
     * Writing back a reconciled list is the app's job. `provideGlance` can run from a broadcast
     * with barely an app process behind it, and the widget being the thing that decides what is
     * persisted would make the store's single-writer rule untrue.
     *
     * Neither read races: `currentPinnedIds` suspends on DataStore's first read, and `flags()`
     * emits immediately with the activated-or-default configuration and never fails. There is
     * therefore a value to draw on the first frame and no loading state to design.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )

        val stored = entryPoint.pinnedToolsRepository().currentPinnedIds()
        val flags = entryPoint.featureFlagRepository().flags().first()
        val reconciled = reconcilePinnedTools(stored, ToolCatalog.entries, flags)
        val intents = entryPoint.widgetLaunchIntentFactory()

        provideContent {
            WidgetGlanceTheme {
                QuickAccessStrip(tools = reconciled.tools, intents = intents)
            }
        }
    }
}

/**
 * The strip itself.
 *
 * Fewer than five pinned means wider tiles rather than blank slots: each takes an equal share of
 * whatever width there is, so any number of them looks deliberate.
 */
@Composable
private fun QuickAccessStrip(tools: List<PinnedTool>, intents: WidgetLaunchIntentFactory) {
    val context = LocalContext.current

    // Parked with the labels — see the note above MIN_LABEL_TILE_WIDTH.
    // val size = LocalSize.current
    // val showLabels = size.height >= REGULAR_SIZE.height &&
    //     (size.width / tools.size.coerceAtLeast(1)) >= MIN_LABEL_TILE_WIDTH

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetGround()
            .padding(R.dimen.widget_padding),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (tools.isEmpty()) {
            // Until the configuration screen exists (SPEC.md §13 step 6) this opens the tools
            // home, which is where the user can already reach everything.
            EmptyPrompt(intent = intents.intentFor(null))
            return@Row
        }

        tools.forEachIndexed { index, tool ->
            if (index > 0) {
                Spacer(GlanceModifier.width(R.dimen.widget_tile_gap))
            }

            ToolTile(
                // `defaultWeight` is a RowScope member, so the share-the-width decision has to be
                // taken here rather than inside the tile.
                modifier = GlanceModifier.defaultWeight(),
                tool = tool,
                label = context.getString(tool.descriptor.titleRes),
                // showLabel = showLabels,
                // A withheld tool resolves to the tools home rather than to nothing: a greyed tile
                // that does nothing at all reads as a broken widget.
                intent = intents.intentFor(tool.descriptor.id.takeIf { tool.isAvailable }),
            )
        }
    }
}

/**
 * One tile.
 *
 * **The content description is built in both breakpoints.** Icon-only is a visual decision, never
 * a semantic one — a TalkBack user must hear the same tool names whatever height the widget
 * happens to be. An unavailable tile says so, because that state is otherwise carried entirely by
 * a colour a screen reader cannot see.
 *
 * "Reduced alpha" from the spec is expressed as the dimmer colour role rather than an alpha value:
 * Glance has no alpha modifier, and a `ColorProvider` built at a fixed alpha would stop following
 * day/night — which is the one thing the colour bridge exists to preserve.
 */
@Composable
private fun ToolTile(
    tool: PinnedTool,
    label: String,
    intent: Intent,
    // showLabel: Boolean,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current

    val colour: ColorProvider =
        if (tool.isAvailable) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant

    val description = context.getString(
        if (tool.isAvailable) R.string.widget_tile_available else R.string.widget_tile_unavailable,
        label,
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(R.dimen.widget_tile_padding)
            .semantics { contentDescription = description }
            .clickable(actionStartActivity(intent)),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(tool.descriptor.widgetIconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colour),
            modifier = GlanceModifier.size(R.dimen.widget_icon_size),
        )

        // if (showLabel) {
        //     Spacer(GlanceModifier.size(R.dimen.widget_icon_label_gap))
        //     Text(
        //         text = label,
        //         maxLines = 1,
        //         style = TextStyle(color = colour, textAlign = TextAlign.Center),
        //     )
        // }
    }
}

/**
 * What the widget is when nothing is pinned.
 *
 * A legal state, not an error: no minimum is enforced, because a disabled last checkbox with no
 * explanation reads as a bug. Drawing a prompt that opens the app keeps the widget from ever being
 * a dead rectangle.
 */
@Composable
private fun EmptyPrompt(intent: Intent) {
    val context = LocalContext.current

    Text(
        text = context.getString(R.string.widget_empty_prompt),
        maxLines = 1,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        ),
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(intent)),
    )
}

/**
 * The widget's ground: the app's background colour, with the rounded corner a launcher expects.
 *
 * **Not `GlanceTheme.colors.widgetBackground`.** Glance derives that token from `secondaryContainer`
 * with a tone shift, which is a reasonable default for a scheme that has no opinion — but this app's
 * `secondaryContainer` is the integrity-passed green, so the widget painted itself in the colour
 * that means "this code checked out". `background` is the role the app actually draws its ground
 * with, and it is what the bridge maps straight from the two schemes.
 *
 * **Two paths for the corner, because `cornerRadius` is API 31+.** It is backed by
 * `RemoteViews.setViewOutlinePreferredRadius`, and below API 31 it does nothing at all — silently,
 * which is how the widget shipped square corners through a whole emulator run. Above it the
 * platform publishes the radius the launcher itself uses, which is worth matching; below it a
 * tinted shape drawable is the only way to round a `RemoteViews` background.
 */
@Composable
private fun GlanceModifier.widgetGround(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(GlanceTheme.colors.background)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        background(
            imageProvider = ImageProvider(R.drawable.widget_background),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.background),
        )
    }
