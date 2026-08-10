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
    val step: Step = Step.First,

    /** True while a capture is averaging samples. */
    val capturing: Boolean = false,

    /** Whether the phone is currently still enough to capture from. */
    val steady: Boolean = false,

    val result: Calibration? = null,

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

internal sealed interface CalibrationIntent : UiIntent {

    /** Sensor registration follows the screen — see `LevelIntent.ScreenResumed`. */
    data object ScreenResumed : CalibrationIntent

    data object ScreenPaused : CalibrationIntent

    data object CaptureRequested : CalibrationIntent

    data object SaveRequested : CalibrationIntent

    data object Restarted : CalibrationIntent
}

internal sealed interface CalibrationEffect : UiEffect {

    /** Saved; the caller should go back to the level. */
    data object Saved : CalibrationEffect
}
