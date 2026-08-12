package com.minion.scaffold.feature.speedometer.presentation

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.feature.speedometer.R
import com.minion.scaffold.feature.speedometer.presentation.component.LocationGate
import com.minion.scaffold.feature.speedometer.presentation.component.PositionPanel
import com.minion.scaffold.feature.speedometer.presentation.component.SatelliteView
import com.minion.scaffold.feature.speedometer.presentation.component.SpeedReadout
import com.minion.scaffold.feature.speedometer.presentation.component.TripPanel
import kotlinx.coroutines.launch

/**
 * The GPS speedometer screen: a large speed readout, altitude, coordinates, satellites and a trip.
 *
 * @param onNavigateBack       Called when the user leaves the speedometer.
 * @param onNavigateToSettings Called when the user opens the speedometer's settings.
 * @param modifier             The [Modifier] for the screen.
 * @param viewModel            The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun SpeedometerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpeedometerViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val noMapApp = stringResource(R.string.speedometer_notice_no_map_app)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        viewModel.onIntent(
            SpeedometerIntent.PermissionResult(
                fineGranted = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true,
                coarseGranted = granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true,
                shouldShowRationale = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ),
            ),
        )
    }

    /**
     * Whether this screen has already put the dialog up.
     *
     * A local latch rather than an inference from the state. The `ActivityResultLauncher` callback is
     * not guaranteed to run before `ON_RESUME`, so a state-based check races and can fire a second
     * request at someone who has just declined — a defect found on a device in `:feature:soundmeter`,
     * and the same shape here.
     */
    var requested by rememberSaveable { mutableStateOf(false) }

    // Re-read on every resume, not once. Both Settings paths — app permissions and location services
    // — leave the app and come back with the world changed, and without this the screen would still
    // be showing a gate for something that is now allowed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        when {
            fine || coarse -> viewModel.onIntent(
                SpeedometerIntent.PermissionResult(
                    fineGranted = fine,
                    coarseGranted = coarse,
                    shouldShowRationale = false,
                ),
            )

            !requested -> {
                requested = true
                permissionLauncher.launch(LOCATION_PERMISSIONS)
            }
        }

        viewModel.onIntent(SpeedometerIntent.ScreenResumed)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.onIntent(SpeedometerIntent.ScreenPaused)
    }

    KeepScreenOn()

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            SpeedometerEffect.OpenAppSettings -> context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )

            SpeedometerEffect.OpenLocationSettings -> context.startActivity(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            )

            is SpeedometerEffect.Copy -> coroutineScope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText(resources.getString(R.string.speedometer_position), effect.text)),
                )
                snackbarHostState.showSnackbar(
                    resources.getString(R.string.speedometer_notice_copied),
                )
            }

            is SpeedometerEffect.OpenInMaps -> {
                val geo = "geo:${effect.latitude},${effect.longitude}" +
                    "?q=${effect.latitude},${effect.longitude}"
                val opened = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, geo.toUri()))
                }.isSuccess

                if (!opened) coroutineScope.launch { snackbarHostState.showSnackbar(noMapApp) }
            }

            is SpeedometerEffect.Notice -> coroutineScope.launch {
                snackbarHostState.showSnackbar(resources.getString(effect.notice.messageRes()))
            }
        }
    }

    SpeedometerContent(
        state = state,
        onIntent = viewModel::onIntent,
        onRequestPermission = {
            requested = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        },
        onNavigateBack = onNavigateBack,
        onNavigateToSettings = onNavigateToSettings,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Keeps the display awake, and gives it back on the way out.
 *
 * A speedometer read from a car mount that times out mid-journey is useless. Deliberately **not**
 * paired with an orientation lock, unlike the level: no reading here depends on which way up the
 * device is, and a landscape mount is a real use.
 */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedometerContent(
    state: State<SpeedometerState>,
    onIntent: (SpeedometerIntent) -> Unit,
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.speedometer_spacing)

    // The speed readout reads this directly and quantises; everything else sits behind a slower slice
    // so a fix does not recompose the whole screen.
    val reading = remember(state) { derivedStateOf { state.value.reading } }
    val chrome by remember(state) { derivedStateOf { state.value.toChrome() } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.speedometer_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.speedometer_navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = stringResource(R.string.speedometer_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            LocationGate(
                access = chrome.access,
                providerEnabled = chrome.providerEnabled,
                onRequest = onRequestPermission,
                onRequestPrecise = onRequestPermission,
                onOpenAppSettings = { onIntent(SpeedometerIntent.AppSettingsRequested) },
                onOpenLocationSettings = { onIntent(SpeedometerIntent.LocationSettingsRequested) },
            )

            if (chrome.mocked) {
                MockedBanner()
            }

            SpeedReadout(
                reading = reading,
                speedUnit = chrome.speedUnit,
                fixQuality = chrome.fixQuality,
            )

            val live = state.value.reading as? SpeedometerState.Reading.Live

            if (live == null) {
                SearchingCard(chrome)
            } else {
                PositionPanel(
                    reading = live,
                    distanceUnit = chrome.distanceUnit,
                    coordinateFormat = chrome.coordinateFormat,
                    rateOfClimbMetersPerMinute = chrome.rateOfClimbMetersPerMinute,
                    onCopy = { onIntent(SpeedometerIntent.CopyCoordinatesRequested) },
                    onOpenInMaps = { onIntent(SpeedometerIntent.OpenInMapsRequested) },
                )
            }

            TripPanel(
                trip = chrome.trip,
                measuring = chrome.measuring,
                speedUnit = chrome.speedUnit,
                distanceUnit = chrome.distanceUnit,
                onStart = { onIntent(SpeedometerIntent.StartPressed) },
                onStop = { onIntent(SpeedometerIntent.StopPressed) },
                onReset = { onIntent(SpeedometerIntent.ResetPressed) },
                enabled = chrome.canMeasure,
            )
        }
    }
}

/**
 * The cold-start view: what the receiver can see while it has no fix.
 *
 * Turns a blank wait into a diagnosis, which matters here more than usual — with no network there is
 * no almanac to download, so the first fix genuinely can take minutes.
 */
@Composable
private fun SearchingCard(chrome: SpeedometerChrome, modifier: Modifier = Modifier) {
    val spacing = dimensionResource(R.dimen.speedometer_spacing)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(
            text = stringResource(R.string.speedometer_searching),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.speedometer_searching_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SatelliteView(status = chrome.satellites)
    }
}

@Composable
private fun MockedBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = stringResource(R.string.speedometer_mocked),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(dimensionResource(R.dimen.speedometer_spacing)),
        )
    }
}

private fun SpeedometerNotice.messageRes(): Int = when (this) {
    SpeedometerNotice.Copied -> R.string.speedometer_notice_copied
    SpeedometerNotice.NoMapApp -> R.string.speedometer_notice_no_map_app
    SpeedometerNotice.TripReset -> R.string.speedometer_notice_trip_reset
    SpeedometerNotice.NothingRecorded -> R.string.speedometer_notice_nothing_recorded
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
