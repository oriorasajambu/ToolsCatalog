package com.minion.scaffold.feature.tools.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.feature.tools.R

/**
 * The tool catalog.
 *
 * Stateless by construction — it renders [ToolCatalog] and reports clicks upward. There is no
 * ViewModel because there is no state: the list is a compile-time constant, nothing loads, and
 * nothing can fail. An `MviViewModel` here would hold a value that never changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolsScreen(
    onOpenTool: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.tools_title)) })
        },
    ) { contentPadding ->
        LazyColumn(modifier = Modifier.padding(contentPadding)) {
            // Keyed by id: without one, inserting a tool at the top shifts every position and
            // recomposes every row.
            items(items = ToolCatalog.entries, key = { it.id }) { tool ->
                ListItem(
                    headlineContent = { Text(text = stringResource(tool.titleRes)) },
                    supportingContent = { Text(text = stringResource(tool.descriptionRes)) },
                    leadingContent = {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable { onOpenTool(tool.route) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Preview
@Composable
internal fun ToolsScreenPreview() {
    AppTheme {
        ToolsScreen(onOpenTool = {})
    }
}
