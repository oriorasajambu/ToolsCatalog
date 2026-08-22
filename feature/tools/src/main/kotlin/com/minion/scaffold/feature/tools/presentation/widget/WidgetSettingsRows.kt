package com.minion.scaffold.feature.tools.presentation.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.designsystem.theme.AppTextStyles
import com.minion.scaffold.core.toolcatalog.ToolDescriptor
import com.minion.scaffold.feature.tools.R

/**
 * A section heading, with an optional counter on the trailing edge.
 *
 * `AppTextStyles.eyebrow` rather than a `labelMedium.copy(letterSpacing = …)`: the tracked-out
 * treatment has no Material slot, and widening a shared slot for one screen re-tracks its two
 * dozen other call sites.
 *
 * @param title    The heading text, already upper-cased by the string resource.
 * @param trailing Counter text, or null where there is nothing to count.
 */
@Composable
internal fun SectionHeading(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimensionResource(R.dimen.tools_widget_block_gap)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AppTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (trailing != null) {
            Text(
                text = trailing,
                style = AppTextStyles.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What one row draws.
 *
 * A holder rather than six loose parameters. The row is the same component in both blocks and the
 * fields are read together — and grouping them is what the complexity rule is asking for when it
 * counts a composable's required arguments.
 *
 * @property descriptor The tool. Its `widgetIconRes` is drawn rather than its `ImageVector`, so
 *                      this screen previews the glyph the widget will actually show.
 * @property checked    Whether the tool is on the widget.
 * @property enabled    Whether the row may be tapped. False at the cap, for unpinned rows.
 * @property caption    A line under the title, or null. Carries the unavailable notice.
 */
@Immutable
internal data class ToolRowState(
    val descriptor: ToolDescriptor,
    val checked: Boolean,
    val enabled: Boolean,
    val caption: String? = null,
)

/**
 * One tool row, in either block.
 *
 * A single composable for both so a tool cannot look like two different things depending on which
 * list it is in — the only differences are the leading slot, whether the checkbox is ticked, and
 * whether it can be tapped at all.
 *
 * @param state    What to draw.
 * @param leading  The slot before the icon: a drag handle in the pinned block, a spacer elsewhere,
 *                 so both lists' icons line up.
 * @param onToggle Called when the row or its checkbox is tapped.
 * @param modifier The [Modifier] for the row.
 */
@Composable
internal fun ToolRow(
    state: ToolRowState,
    leading: @Composable () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val padding = dimensionResource(R.dimen.tools_widget_row_padding)

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(padding),
        ) {
            leading()

            Icon(
                painter = painterResource(state.descriptor.widgetIconRes),
                contentDescription = null,
                tint = if (state.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(dimensionResource(R.dimen.tools_widget_row_tile)),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(state.descriptor.titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                if (state.caption != null) {
                    Text(
                        text = state.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Checkbox(
                checked = state.checked,
                onCheckedChange = { onToggle() },
                enabled = state.enabled,
            )
        }
    }
}
