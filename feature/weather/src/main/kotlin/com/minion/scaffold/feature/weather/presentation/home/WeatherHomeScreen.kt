package com.minion.scaffold.feature.weather.presentation.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.AppOutlinedButton
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.ui.error.toMessageRes
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.core.weather.model.WeatherCondition
import com.minion.scaffold.core.weather.model.WeatherUnit
import com.minion.scaffold.feature.weather.R
import com.minion.scaffold.feature.weather.presentation.stalenessLabel
import com.minion.scaffold.feature.weather.presentation.temperatureFormatRes
import com.minion.scaffold.feature.weather.presentation.toIcon
import com.minion.scaffold.feature.weather.presentation.toLabelRes
import kotlin.math.roundToInt

@Composable
internal fun WeatherHomeScreen(
    onNavigateBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeatherHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.onIntent(
            WeatherHomeIntent.PermissionResult(
                granted = results.values.any { it },
                shouldShowRationale = activity != null && LOCATION_PERMISSIONS.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                },
            ),
        )
    }

    // Re-checked on every resume: coming back from Settings, or from the system dialog, has to be
    // reflected without a second explicit request. See `QrScanScreen`'s identical pattern.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = LOCATION_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        when {
            granted -> viewModel.onIntent(
                WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false),
            )
            state.permission == PermissionState.Unknown -> permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            WeatherHomeEffect.OpenAppSettings -> context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )

            is WeatherHomeEffect.NavigateToDetail -> onOpenDetail(effect.locationId)
            WeatherHomeEffect.NavigateToSearch -> onOpenSearch()
            WeatherHomeEffect.NavigateToSettings -> onOpenSettings()
        }
    }

    WeatherHomeContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        onRequestPermission = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherHomeContent(
    state: WeatherHomeState,
    onIntent: (WeatherHomeIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.weather_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.weather_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.weather_navigate_back),
                        )
                    }
                },
                actions = {
                    // Only offered once the gate is passed: adding cities behind a permission wall
                    // would build a list the user cannot see the weather for.
                    if (state.permission == PermissionState.Granted) {
                        IconButton(onClick = { onIntent(WeatherHomeIntent.AddLocationClicked) }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.weather_add_location),
                            )
                        }
                    }
                    IconButton(onClick = { onIntent(WeatherHomeIntent.SettingsClicked) }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.weather_settings_title),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding)) {
            when (state.permission) {
                PermissionState.Unknown -> Unit // avoids a flash behind the system dialog
                PermissionState.Denied -> PermissionGate(
                    message = stringResource(R.string.weather_permission_rationale),
                    actionLabel = stringResource(R.string.weather_permission_grant),
                    onAction = onRequestPermission,
                )
                PermissionState.PermanentlyDenied -> PermissionGate(
                    message = stringResource(R.string.weather_permission_blocked),
                    actionLabel = stringResource(R.string.weather_permission_open_settings),
                    onAction = { onIntent(WeatherHomeIntent.AppSettingsRequested) },
                )
                PermissionState.Granted -> PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { onIntent(WeatherHomeIntent.PullToRefresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LocationList(state = state, onIntent = onIntent, spacing = spacing)
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.weather_spacing)

    Column(
        modifier = modifier.fillMaxWidth().padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
        AppButton(text = actionLabel, onClick = onAction)
    }
}

@Composable
private fun LocationList(
    state: WeatherHomeState,
    onIntent: (WeatherHomeIntent) -> Unit,
    spacing: Dp,
) {
    // The drag maths needs one number in pixels: how far the finger travels before the dragged
    // card has passed its neighbour. See SavedCardDragState for why a fixed height is assumed.
    val itemHeightPx = with(LocalDensity.current) { (SAVED_CARD_HEIGHT + spacing).toPx() }
    val dragState = rememberSavedCardDragState(itemHeightPx)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        item(key = "pinned") {
            PinnedContent(content = state.content, unit = state.unit, onIntent = onIntent, spacing = spacing)
        }

        if (state.savedCards.isNotEmpty()) {
            item(key = "saved-header") {
                Text(
                    text = stringResource(R.string.weather_saved_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        itemsIndexed(state.savedCards, key = { _, card -> card.locationId }) { index, card ->
            val isDragging = card.locationId == dragState.draggingId

            SavedLocationRow(
                card = card,
                unit = state.unit,
                onIntent = onIntent,
                modifier = Modifier
                    // Lifted above its neighbours so it is drawn over them, not under.
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragState.offsetY else 0f }
                    // Only the cards that are not under the finger animate — animating the dragged
                    // one would fight the finger for control of its position.
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .pointerInput(card.locationId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragState.onDragStart(card.locationId) },
                            onDrag = { change, amount ->
                                change.consume()
                                dragState.onDrag(
                                    deltaY = amount.y,
                                    currentIndex = index,
                                    itemCount = state.savedCards.size,
                                    onMove = { from, to ->
                                        onIntent(WeatherHomeIntent.SavedCardMoved(from, to))
                                    },
                                )
                            },
                            onDragEnd = {
                                dragState.onDragStop()
                                onIntent(WeatherHomeIntent.SavedCardOrderCommitted)
                            },
                            // Cancel still commits: the cards have already moved on screen, so
                            // dropping the new order here would silently undo a visible change.
                            onDragCancel = {
                                dragState.onDragStop()
                                onIntent(WeatherHomeIntent.SavedCardOrderCommitted)
                            },
                        )
                    },
            )
        }

        if (state.savedCards.isEmpty()) {
            item(key = "saved-empty") {
                Text(
                    text = stringResource(R.string.weather_saved_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PinnedContent(
    content: WeatherHomeState.ContentState,
    unit: WeatherUnit,
    onIntent: (WeatherHomeIntent) -> Unit,
    spacing: Dp,
) {
    when (content) {
        WeatherHomeState.ContentState.Loading -> Box(
            modifier = Modifier.fillMaxWidth().height(SAVED_CARD_HEIGHT),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        WeatherHomeState.ContentState.NoFix -> Column(
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Text(text = stringResource(R.string.weather_no_fix), style = MaterialTheme.typography.bodyMedium)
            AppOutlinedButton(
                text = stringResource(R.string.weather_retry),
                onClick = { onIntent(WeatherHomeIntent.Retry) },
            )
        }

        is WeatherHomeState.ContentState.Success -> PinnedLocationCard(
            card = content.card,
            unit = unit,
            onClick = { onIntent(WeatherHomeIntent.CardClicked) },
        )

        is WeatherHomeState.ContentState.Failure -> Column(
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Text(
                text = stringResource(content.error.toMessageRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
            AppButton(
                text = stringResource(R.string.weather_retry),
                onClick = { onIntent(WeatherHomeIntent.Retry) },
            )
        }
    }
}

@Composable
private fun PinnedLocationCard(
    card: LocationCardUi,
    unit: WeatherUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.weather_spacing)

    Card(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing / 4),
        ) {
            Text(text = card.displayName, style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = card.condition.toIcon(),
                    contentDescription = stringResource(card.condition.toLabelRes()),
                )
                Text(
                    text = stringResource(unit.temperatureFormatRes(), card.temperature.roundToInt()),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            card.staleHoursAgo?.let { hours ->
                Text(
                    text = stalenessLabel(hours),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedLocationRow(
    card: SavedCardUi,
    unit: WeatherUnit,
    onIntent: (WeatherHomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    // Reacting to the settled value rather than vetoing via the deprecated `confirmValueChange`.
    // Removing the location takes this row out of the list, so the state never needs resetting —
    // the composable it belongs to leaves composition with it.
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onIntent(WeatherHomeIntent.SavedCardRemoved(card.locationId))
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { DismissBackground() },
        // One direction only, so the affordance matches the delete icon pinned to the trailing
        // edge — a card that could also be swiped the other way would show no icon on that side.
        enableDismissFromStartToEnd = false,
    ) {
        SavedLocationCard(card = card, unit = unit, onClick = {
            onIntent(WeatherHomeIntent.SavedCardClicked(card.locationId))
        })
    }
}

@Composable
private fun DismissBackground() {
    val spacing = dimensionResource(R.dimen.weather_spacing)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SAVED_CARD_HEIGHT)
            .padding(horizontal = spacing),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.weather_saved_remove),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SavedLocationCard(
    card: SavedCardUi,
    unit: WeatherUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.weather_spacing)

    Card(
        modifier = modifier.fillMaxWidth().height(SAVED_CARD_HEIGHT),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = spacing),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = card.displayName, style = MaterialTheme.typography.titleSmall)
                (card.forecast as? SavedCardUi.ForecastState.Ready)?.staleHoursAgo?.let { hours ->
                    Text(
                        text = stalenessLabel(hours),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val forecast = card.forecast) {
                SavedCardUi.ForecastState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.alpha(LOADING_INDICATOR_ALPHA),
                )

                SavedCardUi.ForecastState.Failed -> Text(
                    text = stringResource(R.string.weather_saved_unavailable),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is SavedCardUi.ForecastState.Ready -> Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing / 2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = forecast.condition.toIcon(),
                        contentDescription = stringResource(forecast.condition.toLabelRes()),
                    )
                    Text(
                        text = stringResource(
                            unit.temperatureFormatRes(),
                            forecast.temperature.roundToInt(),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/**
 * Fixed, because [SavedCardDragState] measures drag distance against it. A card that sized itself
 * to its content would make the drag threshold wrong for every card but the one it was computed
 * from.
 */
private val SAVED_CARD_HEIGHT = 72.dp

private const val LOADING_INDICATOR_ALPHA = 0.6f

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

@Preview
@Composable
internal fun WeatherHomePermissionDeniedPreview() {
    AppTheme {
        WeatherHomeContent(
            state = WeatherHomeState(permission = PermissionState.Denied),
            onIntent = {},
            onNavigateBack = {},
            onRequestPermission = {},
        )
    }
}

@Preview
@Composable
internal fun WeatherHomeNoFixPreview() {
    AppTheme {
        WeatherHomeContent(
            state = WeatherHomeState(
                permission = PermissionState.Granted,
                content = WeatherHomeState.ContentState.NoFix,
            ),
            onIntent = {},
            onNavigateBack = {},
            onRequestPermission = {},
        )
    }
}

@Preview
@Composable
internal fun WeatherHomeSuccessPreview() {
    AppTheme {
        WeatherHomeContent(
            state = WeatherHomeState(
                permission = PermissionState.Granted,
                content = WeatherHomeState.ContentState.Success(
                    LocationCardUi(
                        locationId = "current",
                        displayName = "Jakarta",
                        temperature = 31.0,
                        condition = WeatherCondition.PARTLY_CLOUDY,
                        staleHoursAgo = null,
                    ),
                ),
                savedCards = listOf(
                    SavedCardUi(
                        locationId = "1",
                        displayName = "Berlin",
                        forecast = SavedCardUi.ForecastState.Ready(18.0, WeatherCondition.RAIN, null),
                    ),
                    SavedCardUi(
                        locationId = "2",
                        displayName = "Reykjavík",
                        forecast = SavedCardUi.ForecastState.Loading,
                    ),
                    SavedCardUi(
                        locationId = "3",
                        displayName = "Cairo",
                        forecast = SavedCardUi.ForecastState.Failed,
                    ),
                ),
            ),
            onIntent = {},
            onNavigateBack = {},
            onRequestPermission = {},
        )
    }
}
