package com.minion.scaffold.feature.tools.presentation.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.minion.scaffold.core.data.widget.MAX_PINNED_TOOLS
import com.minion.scaffold.core.data.widget.PinnedTool
import com.minion.scaffold.core.toolcatalog.ToolDescriptor
import com.minion.scaffold.feature.tools.R

/**
 * The widget configuration screen.
 *
 * Two blocks: what is on the widget, in widget order, and everything else in catalog order. A
 * pinned-but-unavailable tool stays in the first block holding its slot — unpinning it is how the
 * slot is freed, which is one rule rather than a hidden second kind of state.
 *
 * @param state          What to draw.
 * @param onIntent       Where the user's actions go.
 * @param onNavigateBack Leaves the screen.
 * @param canPinToHome   Whether the launcher supports being asked to place a widget. False hides
 *                       the button entirely: one that does nothing on some launchers is worse
 *                       than none.
 * @param modifier       The [Modifier] for the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetSettingsScreen(
    state: WidgetSettingsState,
    onIntent: (WidgetSettingsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    canPinToHome: Boolean,
    modifier: Modifier = Modifier,
) {
    val gutter = dimensionResource(R.dimen.tools_gutter)
    val rowGap = dimensionResource(R.dimen.tools_widget_row_gap)

    val reorder = rememberReorderState { from, to ->
        onIntent(WidgetSettingsIntent.Reorder(from, to))
    }
    reorder.count = state.pinned.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.tools_widget_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.tools_widget_navigate_back,
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = gutter),
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            item(key = "subtitle") {
                Text(
                    text = stringResource(
                        R.string.tools_widget_settings_subtitle,
                        MAX_PINNED_TOOLS,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (canPinToHome) {
                item(key = "pin-to-home") {
                    OutlinedButton(
                        onClick = { onIntent(WidgetSettingsIntent.PinWidgetRequested) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.tools_widget_add_to_home))
                    }
                }
            }

            item(key = "pinned-heading") {
                SectionHeading(
                    title = stringResource(R.string.tools_widget_section_pinned),
                    trailing = stringResource(
                        R.string.tools_widget_count,
                        state.pinnedCount,
                        MAX_PINNED_TOOLS,
                    ),
                )
            }

            if (state.pinned.isEmpty()) {
                item(key = "pinned-empty") {
                    Text(
                        text = stringResource(R.string.tools_widget_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            itemsIndexed(
                items = state.pinned,
                key = { _, tool -> tool.descriptor.id },
            ) { index, tool ->
                PinnedRow(
                    tool = tool,
                    index = index,
                    count = state.pinned.size,
                    reorder = reorder,
                    onIntent = onIntent,
                )
            }

            item(key = "all-heading") {
                SectionHeading(title = stringResource(R.string.tools_widget_section_all))
            }

            if (state.isAtCap) {
                item(key = "cap") {
                    Text(
                        text = stringResource(
                            R.string.tools_widget_cap_reached,
                            MAX_PINNED_TOOLS,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(items = state.unpinned, key = { it.id }) { tool ->
                CatalogRow(
                    tool = tool,
                    enabled = !state.isAtCap,
                    onToggle = { onIntent(WidgetSettingsIntent.ToggleTool(tool.id)) },
                )
            }
        }
    }
}

/**
 * A row in the pinned block.
 *
 * **The move actions are not a nicety.** A drag gesture is invisible to a screen reader, so
 * without `customActions` the ordering half of this screen would not exist for a TalkBack user.
 * They dispatch the same [WidgetSettingsIntent.Reorder] the gesture does, which is also what keeps
 * the ordering logic testable without touching gesture code.
 */
@Composable
private fun PinnedRow(
    tool: PinnedTool,
    index: Int,
    count: Int,
    reorder: ReorderState,
    onIntent: (WidgetSettingsIntent) -> Unit,
) {
    val label = stringResource(tool.descriptor.titleRes)
    val liftElevation = dimensionResource(R.dimen.tools_widget_drag_elevation)
    val moveUp = stringResource(R.string.tools_widget_move_up)
    val moveDown = stringResource(R.string.tools_widget_move_down)

    val actions = buildList {
        if (index > 0) {
            add(CustomAccessibilityAction(moveUp) { onIntent(reorder(index, index - 1)); true })
        }
        if (index < count - 1) {
            add(CustomAccessibilityAction(moveDown) { onIntent(reorder(index, index + 1)); true })
        }
    }

    ToolRow(
        state = ToolRowState(
            descriptor = tool.descriptor,
            checked = true,
            enabled = true,
            caption = stringResource(R.string.tools_widget_unavailable)
                .takeUnless { tool.isAvailable },
        ),
        leading = {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.tools_widget_drag_handle, label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(dimensionResource(R.dimen.tools_widget_drag_handle))
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { reorder.start(index) },
                            onDrag = { change, drag ->
                                change.consume()
                                reorder.drag(drag.y)
                            },
                            onDragEnd = { reorder.end() },
                            onDragCancel = { reorder.cancel() },
                        )
                    },
            )
        },
        onToggle = { onIntent(WidgetSettingsIntent.ToggleTool(tool.descriptor.id)) },
        modifier = Modifier
            .onSizeChanged { reorder.rowHeightPx = it.height }
            .zIndex(if (reorder.isDragging(index)) 1f else 0f)
            .graphicsLayer {
                translationY = if (reorder.isDragging(index)) reorder.offsetY else 0f
                shadowElevation = if (reorder.isDragging(index)) liftElevation.toPx() else 0f
            }
            .semantics { customActions = actions },
    )
}

/** A row in the catalog block: tappable unless the widget is full. */
@Composable
private fun CatalogRow(tool: ToolDescriptor, enabled: Boolean, onToggle: () -> Unit) {
    ToolRow(
        state = ToolRowState(descriptor = tool, checked = false, enabled = enabled),
        leading = { Spacer(Modifier.size(dimensionResource(R.dimen.tools_widget_drag_handle))) },
        onToggle = onToggle,
    )
}

private fun reorder(from: Int, to: Int) = WidgetSettingsIntent.Reorder(from, to)
