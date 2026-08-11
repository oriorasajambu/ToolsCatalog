package com.minion.scaffold.feature.weather.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.ui.error.toMessageRes
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.core.weather.model.LocationSearchResult
import com.minion.scaffold.feature.weather.R
import kotlinx.coroutines.launch

/**
 * The add-location screen: type-ahead place-name search, tap a hit to save it.
 *
 * @param onNavigateBack Called when the user leaves the search screen.
 * @param modifier       The [Modifier] for the screen.
 * @param viewModel      The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun LocationSearchScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Read in composition so a locale change re-resolves it — see QrScanScreen for why the
    // handler must not call LocalContext.current.getString() itself.
    val resources = LocalResources.current

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is LocationSearchEffect.LocationAdded -> coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    resources.getString(R.string.weather_search_added, effect.name),
                )
            }
        }
    }

    LocationSearchContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSearchContent(
    state: LocationSearchState,
    onIntent: (LocationSearchIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.weather_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.weather_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.weather_navigate_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onIntent(LocationSearchIntent.QueryChanged(it)) },
                modifier = Modifier.fillMaxWidth().padding(spacing),
                singleLine = true,
                label = { Text(text = stringResource(R.string.weather_search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onIntent(LocationSearchIntent.QueryChanged("")) }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.weather_search_clear),
                            )
                        }
                    }
                },
            )

            SearchResults(
                content = state.content,
                savedIds = state.savedIds,
                onIntent = onIntent,
                spacing = spacing,
            )
        }
    }
}

@Composable
private fun SearchResults(
    content: LocationSearchState.ContentState,
    savedIds: Set<String>,
    onIntent: (LocationSearchIntent) -> Unit,
    spacing: Dp,
) {
    when (content) {
        LocationSearchState.ContentState.Idle -> Message(stringResource(R.string.weather_search_idle), spacing)

        LocationSearchState.ContentState.Searching -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        LocationSearchState.ContentState.Empty -> Message(stringResource(R.string.weather_search_empty), spacing)

        is LocationSearchState.ContentState.Results -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing),
        ) {
            items(content.results, key = { it.id }) { result ->
                ResultRow(
                    result = result,
                    alreadySaved = result.id in savedIds,
                    onAdd = { onIntent(LocationSearchIntent.ResultSelected(result)) },
                )
            }
        }

        is LocationSearchState.ContentState.Failure -> Column(
            modifier = Modifier.fillMaxWidth().padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Text(
                text = stringResource(content.error.toMessageRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
            AppButton(
                text = stringResource(R.string.weather_retry),
                onClick = { onIntent(LocationSearchIntent.Retry) },
            )
        }
    }
}

@Composable
private fun ResultRow(
    result: LocationSearchResult,
    alreadySaved: Boolean,
    onAdd: () -> Unit,
) {
    // "Illinois, United States" — the whole reason the search model carries these two fields.
    val subtitle = listOfNotNull(result.admin1, result.country).joinToString(", ")

    ListItem(
        headlineContent = { Text(text = result.name) },
        supportingContent = { if (subtitle.isNotBlank()) Text(text = subtitle) },
        trailingContent = {
            if (alreadySaved) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.weather_search_already_saved),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                IconButton(onClick = onAdd) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.weather_search_add, result.name),
                    )
                }
            }
        },
    )
}

@Composable
private fun Message(text: String, spacing: Dp) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview
@Composable
internal fun LocationSearchResultsPreview() {
    AppTheme {
        LocationSearchContent(
            state = LocationSearchState(
                query = "Jakarta",
                content = LocationSearchState.ContentState.Results(
                    listOf(
                        LocationSearchResult("1", "Jakarta", "Indonesia", "Jakarta", -6.2, 106.8),
                        LocationSearchResult("2", "Jakarta Barat", "Indonesia", "Jakarta", -6.1, 106.7),
                    ),
                ),
                savedIds = setOf("1"),
            ),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
internal fun LocationSearchEmptyPreview() {
    AppTheme {
        LocationSearchContent(
            state = LocationSearchState(
                query = "zzzzzz",
                content = LocationSearchState.ContentState.Empty,
            ),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
