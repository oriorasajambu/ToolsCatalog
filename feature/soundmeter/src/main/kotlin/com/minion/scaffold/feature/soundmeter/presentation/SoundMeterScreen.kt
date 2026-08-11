package com.minion.scaffold.feature.soundmeter.presentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.feature.soundmeter.R
import com.minion.scaffold.feature.soundmeter.presentation.component.HistoryChart
import com.minion.scaffold.feature.soundmeter.presentation.component.MeterControls
import com.minion.scaffold.feature.soundmeter.presentation.component.MeterStatus
import com.minion.scaffold.feature.soundmeter.presentation.component.MicrophonePermissionGate
import com.minion.scaffold.feature.soundmeter.presentation.component.SessionPanel
import com.minion.scaffold.feature.soundmeter.presentation.component.SoundGauge
import kotlinx.coroutines.launch

@Composable
internal fun SoundMeterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SoundMeterViewModel = hiltViewModel(),
) {
    // Collected as State rather than destructured with `by`. Reading `state.value` here would
    // recompose the whole screen at the block rate; instead the State is passed down and the gauge
    // reads it in the draw phase.
    val state = viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val clipboardLabel = stringResource(R.string.soundmeter_clipboard_label)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onIntent(
            SoundMeterIntent.PermissionResult(
                granted = granted,
                shouldShowRationale = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.RECORD_AUDIO,
                    ),
            ),
        )
    }

    // Re-checked on every resume, not once on first composition. The "open settings" path leaves the
    // app and comes back with the permission changed; without this the screen would still be showing
    // the blocked message over a microphone it is now allowed to use.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        when {
            granted -> viewModel.onIntent(
                SoundMeterIntent.PermissionResult(granted = true, shouldShowRationale = false),
            )
            // Ask once, on arrival. Re-asking on every resume would trap someone who declined in a
            // dialog they cannot get past.
            state.value.permission == PermissionState.Unknown ->
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        viewModel.onIntent(SoundMeterIntent.ScreenResumed)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.onIntent(SoundMeterIntent.ScreenPaused)
    }

    KeepScreenOnWhileMeasuring()

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            SoundMeterEffect.CopySummary -> coroutineScope.launch {
                clipboard.copy(clipboardLabel, state.value.toSummaryText(resources))
                snackbarHostState.showSnackbar(
                    resources.getString(R.string.soundmeter_summary_copied),
                )
            }

            SoundMeterEffect.ShareSummary -> {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = MIME_TYPE_PLAIN_TEXT
                    putExtra(Intent.EXTRA_TEXT, state.value.toSummaryText(resources))
                }
                context.startActivity(Intent.createChooser(share, null))
            }

            SoundMeterEffect.OpenAppSettings -> context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )

            is SoundMeterEffect.Notice -> coroutineScope.launch {
                snackbarHostState.showSnackbar(resources.getString(effect.notice.messageRes()))
            }
        }
    }

    SoundMeterContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        onNavigateToSettings = onNavigateToSettings,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Keeps the display awake while the screen is open, and gives it back on the way out.
 *
 * People put a phone down and step away from it to measure a room without their own movement in the
 * signal, so a screen that times out mid-session is worse than an inconvenience — it ends the
 * measurement. Deliberately **not** paired with an orientation lock: unlike the level, no axis of
 * this reading depends on which way up the device is.
 */
@Composable
private fun KeepScreenOnWhileMeasuring() {
    val view = LocalView.current

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundMeterContent(
    state: State<SoundMeterState>,
    onIntent: (SoundMeterIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onRequestPermission: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.soundmeter_spacing)

    // Each of these narrows the state to something that changes far more slowly than the reading
    // does, so the panels below do not recompose with every block.
    val reading = remember(state) { derivedStateOf { state.value.reading } }
    val history = remember(state) { derivedStateOf { state.value.history } }
    val chrome by remember(state) { derivedStateOf { state.value.toChrome() } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.soundmeter_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.soundmeter_navigate_back,
                            ),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = stringResource(R.string.soundmeter_settings),
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
            MicrophonePermissionGate(
                permission = chrome.permission,
                onRequest = onRequestPermission,
                onOpenSettings = { onIntent(SoundMeterIntent.AppSettingsRequested) },
            )

            SoundGauge(reading = reading)

            MeterStatus(chrome = chrome)

            HistoryChart(history = history)

            SessionPanel(stats = chrome.stats, measuring = chrome.measuring)

            MeterControls(
                weighting = chrome.weighting,
                timeWeighting = chrome.timeWeighting,
                measuring = chrome.measuring,
                canMeasure = chrome.canMeasure,
                hasSummary = chrome.hasSummary,
                onWeightingChange = { onIntent(SoundMeterIntent.WeightingChanged(it)) },
                onTimeWeightingChange = { onIntent(SoundMeterIntent.TimeWeightingChanged(it)) },
                onStart = { onIntent(SoundMeterIntent.StartPressed) },
                onStop = { onIntent(SoundMeterIntent.StopPressed) },
                onReset = { onIntent(SoundMeterIntent.ResetPressed) },
                onCopy = { onIntent(SoundMeterIntent.CopySummaryRequested) },
            )
        }
    }
}

private fun SoundMeterNotice.messageRes(): Int = when (this) {
    SoundMeterNotice.SessionReset -> R.string.soundmeter_notice_reset
    SoundMeterNotice.NothingMeasured -> R.string.soundmeter_notice_nothing_measured
    SoundMeterNotice.ProcessedInput -> R.string.soundmeter_notice_processed_input
}

private suspend fun Clipboard.copy(label: String, text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
}

private const val MIME_TYPE_PLAIN_TEXT = "text/plain"
