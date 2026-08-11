package com.minion.scaffold.feature.level.presentation.calibration

import androidx.compose.runtime.Immutable
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.level.model.Calibration
import com.minion.scaffold.core.level.usecase.CalibrationRejection

/**
 * The guided flip.
 *
 * Two captures, 180° apart on the same surface, from which the device's own bias falls out. Modelled
 * as a small state machine rather than a pair of booleans so that "captured the second reading
 * without having captured the first" is not a state that can exist.
 */
@Immutable
internal data class CalibrationState(
    /** Which step of the guided flip is active. */
    val step: Step = Step.First,

    /** True while a capture is averaging samples. */
    val capturing: Boolean = false,

    /** Whether the phone is currently still enough to capture from. */
    val steady: Boolean = false,

    /** The solved calibration once the flip completes, or `null`. */
    val result: Calibration? = null,

    /** Why the flip was rejected, or `null` when it has not been rejected. */
    val rejection: CalibrationRejection? = null,
) : UiState {

    enum class Step {

        /** Waiting for the first reading. */
        First,

        /** First reading taken; waiting for the user to turn the phone and capture again. */
        Second,

        /** Solved, waiting to be saved or discarded. */
        Done,
    }
}

/** Everything the user (or the system) can do during calibration. */
internal sealed interface CalibrationIntent : UiIntent {

    /** Sensor registration follows the screen — see `LevelIntent.ScreenResumed`. */
    data object ScreenResumed : CalibrationIntent

    /** The screen stopped being visible. */
    data object ScreenPaused : CalibrationIntent

    /** Capture the current reading for this step. */
    data object CaptureRequested : CalibrationIntent

    /** Save the solved calibration. */
    data object SaveRequested : CalibrationIntent

    /** Discard progress and start the flip again. */
    data object Restarted : CalibrationIntent
}

/** One-shot events from the calibration screen. */
internal sealed interface CalibrationEffect : UiEffect {

    /** Saved; the caller should go back to the level. */
    data object Saved : CalibrationEffect
}
