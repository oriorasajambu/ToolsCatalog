package com.minion.scaffold.feature.level.presentation

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.view.Surface
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.level.model.LevelPose
import com.minion.scaffold.core.level.usecase.Steadiness
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.feature.level.R
import com.minion.scaffold.feature.level.domain.GravitySensor
import com.minion.scaffold.feature.level.presentation.component.AxisReadout
import com.minion.scaffold.feature.level.presentation.component.BullseyeLevel
import com.minion.scaffold.feature.level.presentation.component.LevelActions
import com.minion.scaffold.feature.level.presentation.component.LevelReadout
import com.minion.scaffold.feature.level.presentation.component.LevelStatus
import com.minion.scaffold.feature.level.presentation.component.LevelTone
import kotlinx.coroutines.launch

/**
 * The bubble level and clinometer screen.
 *
 * @param onNavigateBack          Called when the user leaves the level.
 * @param onNavigateToCalibration Called when the user starts the guided calibration.
 * @param modifier                The [Modifier] for the screen.
 * @param viewModel               The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun LevelScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LevelViewModel = hiltViewModel(),
) {
    // Collected as State rather than destructured with `by`. Reading `state.value` here would
    // recompose the whole screen at the sensor's rate; instead the State is passed down and each
    // leaf derives only the field it needs.
    val state = viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Read in composition and used in the effect handler below. LocalContext.current.getString()
    // inside the handler would keep resolving against whichever locale was active when the screen
    // was first composed.
    val resources = LocalResources.current

    // The sensor is tied to the screen being visible, not to the ViewModel's lifetime — the
    // ViewModel is scoped to the navigation entry and outlives both.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(LevelIntent.ScreenResumed)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.onIntent(LevelIntent.ScreenPaused)
    }

    LevelScreenSetup(onDisplayRotation = { degrees ->
        viewModel.onIntent(LevelIntent.DisplayRotationChanged(degrees))
    })

    LevelTone(
        enabled = remember(state) { derivedStateOf { state.value.soundEnabled } },
        deviationDegrees = remember(state) {
            derivedStateOf { state.value.displayed.inclination }
        },
    )

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is LevelEffect.Notice -> coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = resources.getString(effect.notice.messageRes()),
                    actionLabel = effect.notice.actionRes()?.let(resources::getString),
                    duration = if (effect.notice == LevelNotice.CalibrationSuggested) {
                        SnackbarDuration.Long
                    } else {
                        SnackbarDuration.Short
                    },
                )

                if (result == SnackbarResult.ActionPerformed &&
                    effect.notice == LevelNotice.CalibrationSuggested
                ) {
                    onNavigateToCalibration()
                }
                // Either way it has been shown once and answered; do not nag again.
                if (effect.notice == LevelNotice.CalibrationSuggested) {
                    viewModel.onIntent(LevelIntent.CalibrationPromptDismissed)
                }
            }
        }
    }

    LevelContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        onNavigateToCalibration = onNavigateToCalibration,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * The two things this screen needs from the window, and one it needs from the display.
 *
 * **Portrait lock.** A phone rotated flat on a table swaps its pitch and roll axes; locking the
 * screen removes that entirely, and matches the mental model anyway, since a physical spirit level
 * also has a fixed orientation. Restoring the previous value on dispose is not optional — this is a
 * single-Activity app, so failing to restore would leave *every other screen* portrait-locked, with
 * nothing to point at as the cause.
 *
 * **Keep the screen on.** People set a level down and step back to look at it. A screen that times
 * out mid-measurement is infuriating, and the flag costs nothing once the screen is gone.
 *
 * **Natural-orientation offset.** The sensor frame is the device's *natural* orientation — portrait
 * on phones, but landscape on many tablets and foldables. On one of those, a portrait lock would
 * leave the bubble moving sideways when the user tilts forwards. Reading the rotation once and
 * passing it down as a plain angle keeps the correction in `:core:level` as a 2D rotation, rather
 * than reaching for `SensorManager.remapCoordinateSystem`, which is overkill for an in-plane turn
 * and far harder to test.
 */
@SuppressLint("SourceLockedOrientationActivity")
@Composable
private fun LevelScreenSetup(onDisplayRotation: (Double) -> Unit) {
    val activity = LocalActivity.current
    val view = LocalView.current

    DisposableEffect(activity, view) {
        val previousOrientation = activity?.requestedOrientation

        // Suppressed deliberately, and scoped to this one screen. Lint's advice is right in
        // general and wrong here: a measuring instrument has a fixed orientation, and letting the
        // frame rotate under the user mid-reading swaps which axis is pitch. The lock is undone in
        // onDispose, so nothing else in the app inherits it.
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        view.keepScreenOn = true

        // ContextCompat rather than Context.getDisplay(), which is API 30 and this module is 29.
        val rotation = ContextCompat.getDisplayOrDefault(view.context).rotation
        onDisplayRotation(
            when (rotation) {
                Surface.ROTATION_90 -> -90.0
                Surface.ROTATION_180 -> 180.0
                Surface.ROTATION_270 -> 90.0
                else -> 0.0
            },
        )

        onDispose {
            view.keepScreenOn = false
            activity?.requestedOrientation =
                previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelContent(
    state: State<LevelState>,
    onIntent: (LevelIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.level_spacing)
    val soundEnabled by remember(state) { derivedStateOf { state.value.soundEnabled } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.level_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.level_navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(LevelIntent.SoundToggled) }) {
                        Icon(
                            imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp
                            else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = stringResource(
                                if (soundEnabled) R.string.level_sound_disable
                                else R.string.level_sound_enable,
                            ),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        val sensor by remember(state) { derivedStateOf { state.value.sensor } }

        if (sensor == GravitySensor.Unavailable) {
            UnsupportedMessage(modifier = Modifier.padding(contentPadding).padding(spacing))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            BullseyeLevel(
                bubbleX = { state.value.bubbleX },
                bubbleY = { state.value.bubbleY },
                isLevel = { state.value.isLevel },
            )

            LevelReadout(
                degrees = remember(state) {
                    derivedStateOf { state.value.displayed.primaryAngle(state.value.pose) }
                },
                status = remember(state) { derivedStateOf { state.value.status() } },
            )

            val isFlat by remember(state) {
                derivedStateOf {
                    state.value.pose == LevelPose.Flat || state.value.pose == LevelPose.FaceDown
                }
            }
            if (isFlat) {
                AxisReadout(
                    tiltX = remember(state) { derivedStateOf { state.value.displayed.tiltX } },
                    tiltY = remember(state) { derivedStateOf { state.value.displayed.tiltY } },
                )
            }

            LevelActions(
                state = state,
                onIntent = onIntent,
                onNavigateToCalibration = onNavigateToCalibration,
            )
        }
    }
}

@Composable
private fun UnsupportedMessage(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.level_unsupported),
        modifier = modifier,
    )
}

/**
 * Which angle the big number shows, given the pose.
 *
 * Flat wants the total tilt of the surface; standing on an edge wants deviation from plumb. Same
 * reading, different question.
 */
private fun com.minion.scaffold.core.level.model.Tilt.primaryAngle(pose: LevelPose): Double =
    when (pose) {
        is LevelPose.Edge -> signedEdgeDeviation
        else -> inclination
    }

private fun LevelNotice.messageRes(): Int = when (this) {
    LevelNotice.ReferenceNotSteady -> R.string.level_reference_not_steady
    LevelNotice.ReferenceCaptured -> R.string.level_reference_captured
    LevelNotice.CalibrationCleared -> R.string.level_calibration_cleared
    LevelNotice.UsingAccelerometer -> R.string.level_accelerometer_fallback
    LevelNotice.CalibrationSuggested -> R.string.level_calibration_prompt
}

private fun LevelNotice.actionRes(): Int? = when (this) {
    LevelNotice.CalibrationSuggested -> R.string.level_calibrate
    else -> null
}

/**
 * Which of the four things the status line says, in priority order.
 *
 * Held first, always: a stale reading presented as live is the worst confusion this screen can
 * cause. Moving next, because it says the number means nothing right now, where Relative only says
 * what it is measured against.
 */
private fun LevelState.status(): LevelStatus = when {
    frozen != null -> LevelStatus.Held
    steadiness == Steadiness.Moving -> LevelStatus.Moving
    reference != null -> LevelStatus.Relative
    else -> LevelStatus.Live
}
