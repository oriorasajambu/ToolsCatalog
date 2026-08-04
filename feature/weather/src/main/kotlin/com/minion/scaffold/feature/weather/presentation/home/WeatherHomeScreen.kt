package com.minion.scaffold.feature.weather.presentation.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
import com.minion.scaffold.feature.weather.R
import com.minion.scaffold.feature.weather.presentation.toIcon
import com.minion.scaffold.feature.weather.presentation.toLabelRes
import kotlin.math.roundToInt

@Composable
internal fun WeatherHomeScreen(
    onNavigateBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
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
                    LocationContent(content = state.content, onIntent = onIntent, spacing = spacing)
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
private fun LocationContent(
    content: WeatherHomeState.ContentState,
    onIntent: (WeatherHomeIntent) -> Unit,
    spacing: Dp,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        when (content) {
            WeatherHomeState.ContentState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
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
}

@Composable
private fun PinnedLocationCard(
    card: LocationCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.weather_spacing)

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
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
                    text = "${card.temperatureCelsius.roundToInt()}°C",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            card.staleHoursAgo?.let { hours ->
                Text(
                    text = stringResource(R.string.weather_updated_hours_ago, hours),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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
                        temperatureCelsius = 31.0,
                        condition = WeatherCondition.PARTLY_CLOUDY,
                        staleHoursAgo = null,
                    ),
                ),
            ),
            onIntent = {},
            onNavigateBack = {},
            onRequestPermission = {},
        )
    }
}
