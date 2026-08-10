package com.minion.scaffold.feature.level.presentation

import androidx.compose.runtime.Immutable
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.level.model.Calibration
import com.minion.scaffold.core.level.model.LevelPose
import com.minion.scaffold.core.level.model.Tilt
import com.minion.scaffold.core.level.usecase.Steadiness
import com.minion.scaffold.feature.level.domain.GravitySensor

/**
 * The level's state.
 *
 * `@Immutable` and free of arrays on purpose. This updates at roughly 50Hz — far faster than any
 * other screen in the app — so Compose's ability to skip work depends entirely on being able to
 * compare instances cheaply and correctly. A `FloatArray` in here would compare by reference and
 * make every single emission read as a change.
 */
@Immutable
internal data class LevelState(
    val sensor: GravitySensor = GravitySensor.Fused,

    /** The current reading. Already calibrated, smoothed and rotated into the display's frame. */
    val tilt: Tilt = Tilt.LEVEL,

    val pose: LevelPose = LevelPose.Transitional,

    val steadiness: Steadiness = Steadiness.Settling,

    /**
     * Where the bubble sits, as the in-plane part of the up-vector, each component −1..1.
     *
     * Carried as the raw vector rather than as an offset in pixels because a real vial's bubble
     * displaces by `R·sin θ`, which *is* this — so the drawing needs no trigonometry and cannot
     * drift from the numbers beside it.
     */
    val bubbleX: Double = 0.0,
    val bubbleY: Double = 0.0,

    val calibration: Calibration = Calibration.NONE,

    val soundEnabled: Boolean = false,

    /**
     * A held reading, or null when live.
     *
     * The eyes-free half of the feature: put the phone somewhere you cannot see it, freeze, pull it
     * out and read. Deliberately obvious in the UI, because a frozen level that looks live is worse
     * than no level at all.
     */
    val frozen: Tilt? = null,

    /**
     * The reference for relative measurement, or null when measuring against horizontal.
     *
     * Never persisted, and visibly distinct from calibration in the UI, because the two are the
     * same arithmetic with opposite lifetimes: one describes the device forever, the other is a
     * throwaway. A reference surviving into tomorrow would make every reading silently wrong.
     */
    val reference: ReferenceCapture? = null,
) : UiState {

    val isLevel: Boolean get() = displayed.inclination <= LEVEL_TOLERANCE_DEGREES

    /** What the user is actually looking at — the held reading if there is one, else the live one. */
    val displayed: Tilt get() = frozen ?: tilt

    val isCalibrated: Boolean get() = calibration.measuredMask != 0

    companion object {

        /**
         * The band the display calls level, in degrees.
         *
         * Roughly what a phone can actually resolve once calibrated, and tight enough to be worth
         * something. Worth knowing: absolute accuracy *before* calibration is more like half a
         * degree, so this verdict only means what it says once a flip has been done — which is why
         * the UI hedges it until then.
         */
        const val LEVEL_TOLERANCE_DEGREES = 0.2
    }
}

/** A captured reference surface, held only for as long as the screen is open. */
@Immutable
internal data class ReferenceCapture(
    val upX: Double,
    val upY: Double,
    val upZ: Double,
)

internal sealed interface LevelIntent : UiIntent {

    /**
     * The screen became visible, or stopped being visible.
     *
     * Drives sensor registration. The ViewModel outlives the screen — it is scoped to the
     * navigation entry — so collecting in `init` would leave the accelerometer powered with the
     * phone in someone's pocket. These two are what tie the hardware to what is actually on screen.
     */
    data object ScreenResumed : LevelIntent

    data object ScreenPaused : LevelIntent

    data object FreezeToggled : LevelIntent

    data object ReferenceCaptured : LevelIntent

    data object ReferenceCleared : LevelIntent

    data object SoundToggled : LevelIntent

    data object CalibrationPromptDismissed : LevelIntent

    data object CalibrationCleared : LevelIntent

    /** The device's natural orientation differs from portrait — see `LevelScreen`. */
    data class DisplayRotationChanged(val degrees: Double) : LevelIntent
}

internal sealed interface LevelEffect : UiEffect {

    /** Something happened worth a word — a reference captured, a calibration cleared. */
    data class Notice(val notice: LevelNotice) : LevelEffect
}

/**
 * The one-shot messages, shown as a snackbar.
 *
 * Only genuinely transient things belong here. Persistent modes — held, measuring against a
 * reference — go to the always-present status line instead, because a message that dismisses itself
 * cannot be trusted to represent a state the user is still in.
 */
internal enum class LevelNotice {

    /** A reference was taken while the phone was moving, so it was not captured. */
    ReferenceNotSteady,

    ReferenceCaptured,

    CalibrationCleared,

    /** No fused gravity sensor on this device, so readings settle more slowly. */
    UsingAccelerometer,

    /** Uncalibrated, and the user has not yet dismissed the suggestion. Carries an action. */
    CalibrationSuggested,
}
