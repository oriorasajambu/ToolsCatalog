package com.minion.scaffold.feature.level.presentation.calibration

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.level.model.UpVector
import com.minion.scaffold.core.level.usecase.CalibrationOutcome
import com.minion.scaffold.core.level.usecase.DetectStabilityUseCase
import com.minion.scaffold.core.level.usecase.SolveFlipCalibrationUseCase
import com.minion.scaffold.core.level.usecase.StabilityState
import com.minion.scaffold.core.level.usecase.Steadiness
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.feature.level.domain.GravitySource
import com.minion.scaffold.feature.level.domain.SaveCalibrationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the two-capture flip.
 *
 * Readings are **averaged over a window** rather than sampled once. A single sample carries the
 * sensor's full noise, and this value is then applied to every reading the tool ever gives — so it
 * is worth half a second to make it a good one.
 *
 * Captures are refused unless the stability detector says the phone is still. Calibrating from a
 * moving phone bakes an acceleration into the device's permanent bias, which is the one error the
 * user has no way to see afterwards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class CalibrationViewModel @Inject constructor(
    gravitySource: GravitySource,
    private val detectStability: DetectStabilityUseCase,
    private val solveFlip: SolveFlipCalibrationUseCase,
    private val saveCalibration: SaveCalibrationUseCase,
) : MviViewModel<CalibrationState, CalibrationIntent, CalibrationEffect>(CalibrationState()) {

    /** See LevelViewModel: the sensor follows the screen, not the ViewModel's lifetime. */
    private val screenVisible = MutableStateFlow(false)

    private var stability = StabilityState()
    private var latestUp: UpVector? = null

    /** Samples accumulated during a capture, averaged when the window closes. */
    private val window = mutableListOf<UpVector>()

    private var firstCapture: UpVector? = null

    init {
        screenVisible
            .flatMapLatest { visible -> if (visible) gravitySource.samples() else emptyFlow() }
            .onEach { sample ->
                val up = sample.normalizedOrNull() ?: return@onEach
                stability = detectStability(stability, up, sample.timestampNanos)
                latestUp = up

                val steady = stability.steadiness == Steadiness.Steady
                if (currentState.steady != steady) reduce { copy(steady = steady) }

                if (currentState.capturing) collect(up)
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: CalibrationIntent) {
        when (intent) {
            CalibrationIntent.ScreenResumed -> screenVisible.value = true

            CalibrationIntent.ScreenPaused -> {
                screenVisible.value = false
                stability = StabilityState()
                // A capture interrupted by leaving the screen is abandoned rather than resumed:
                // the phone has almost certainly moved.
                window.clear()
                reduce { copy(capturing = false) }
            }

            CalibrationIntent.CaptureRequested -> beginCapture()

            CalibrationIntent.SaveRequested -> viewModelScope.launch {
                currentState.result?.let { saveCalibration(it) }
                emitEffect(CalibrationEffect.Saved)
            }

            CalibrationIntent.Restarted -> {
                firstCapture = null
                window.clear()
                reduce { CalibrationState(steady = steady) }
            }
        }
    }

    private fun beginCapture() {
        if (!currentState.steady) return
        window.clear()
        reduce { copy(capturing = true, rejection = null) }
    }

    private fun collect(up: UpVector) {
        // A capture that starts still and ends moved is worse than no capture: it looks successful.
        if (stability.steadiness != Steadiness.Steady) {
            window.clear()
            reduce { copy(capturing = false) }
            return
        }

        window += up
        if (window.size < WINDOW_SAMPLES) return

        val averaged = window.average()
        window.clear()
        reduce { copy(capturing = false) }

        when (currentState.step) {
            CalibrationState.Step.First -> {
                firstCapture = averaged
                reduce { copy(step = CalibrationState.Step.Second) }
            }

            CalibrationState.Step.Second -> solve(averaged)
            CalibrationState.Step.Done -> Unit
        }
    }

    private fun solve(second: UpVector) {
        val first = firstCapture ?: return

        when (
            val outcome = solveFlip(
                first = first,
                second = second,
                bothSteady = true,
                takenAtMillis = System.currentTimeMillis(),
            )
        ) {
            is CalibrationOutcome.Solved -> reduce {
                copy(step = CalibrationState.Step.Done, result = outcome.calibration)
            }

            is CalibrationOutcome.Rejected -> {
                // Back to the beginning rather than retrying the second capture alone: whatever
                // went wrong may have moved the phone, so the first reading is no longer trusted.
                firstCapture = null
                reduce {
                    CalibrationState(
                        step = CalibrationState.Step.First,
                        steady = steady,
                        rejection = outcome.reason,
                    )
                }
            }
        }
    }

    /** Renormalised, so the average of unit vectors is itself a unit vector. */
    private fun List<UpVector>.average(): UpVector {
        val x = sumOf { it.x } / size
        val y = sumOf { it.y } / size
        val z = sumOf { it.z } / size
        val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)

        return UpVector(x / magnitude, y / magnitude, z / magnitude)
    }

    private companion object {

        /** Roughly half a second at 50Hz — long enough to average away noise, short enough to hold. */
        const val WINDOW_SAMPLES = 25
    }
}
