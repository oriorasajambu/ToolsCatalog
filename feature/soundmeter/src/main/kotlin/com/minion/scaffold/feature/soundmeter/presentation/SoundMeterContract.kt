package com.minion.scaffold.feature.soundmeter.presentation

import androidx.compose.runtime.Immutable
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.sound.model.SessionStats
import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.core.sound.model.TimeWeighting
import com.minion.scaffold.core.sound.model.Weighting
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.feature.soundmeter.domain.CaptureFailure
import com.minion.scaffold.feature.soundmeter.domain.CaptureQuality

/**
 * The meter's state.
 *
 * `@Immutable` and free of arrays, like `LevelState` and for the same reason: this updates at the
 * audio block rate — roughly 47 times a second — so Compose's ability to skip work rests entirely on
 * comparing instances cheaply and correctly.
 *
 * [history] is the one collection here, and it is deliberately updated at a *tenth* of that rate.
 * The number and the gauge want every block; a chart of the last minute does not, and rebuilding a
 * 600-element list 47 times a second to move it by one pixel would be the most expensive thing on
 * the screen by a wide margin.
 */
@Immutable
internal data class SoundMeterState(
    /** The microphone permission state. */
    val permission: PermissionState = PermissionState.Unknown,

    /** How the input was opened, and so how much to trust the reading. */
    val quality: CaptureQuality = CaptureQuality.Unprocessed,

    /** Set once the capture has actually opened, so the UI can tell "starting" from "broken". */
    val capturing: Boolean = false,

    /** Another app holds the microphone and this one is being fed silence. */
    val silenced: Boolean = false,

    /**
     * Why the capture is not running, or null when nothing has gone wrong.
     *
     * The reason is carried rather than a bare `failed` flag because the two failures need different
     * words. "The microphone stopped responding" is accurate for a recorder that died mid-session
     * and actively misleading for a device with no usable input — and pointing someone at the wrong
     * cause is worse than saying nothing.
     */
    val failure: CaptureFailure? = null,

    /** What the big number shows, or why it shows nothing. */
    val reading: Reading = Reading.Waiting,

    /** The frequency weighting (A/C/Z) in effect. */
    val weighting: Weighting = Weighting.A,

    /** The time weighting (Fast/Slow) in effect. */
    val timeWeighting: TimeWeighting = TimeWeighting.Fast,

    /** The calibration offset in dB. */
    val offsetDb: Double = 0.0,

    /**
     * Whether the statistics are accumulating.
     *
     * The live reading runs whenever the screen is on show; this gates only the session. Deliberate
     * rather than automatic, so that "the Leq of this room over five minutes" is something the user
     * asked for rather than an accident of when they happened to open the app.
     */
    val measuring: Boolean = false,

    /** The running session statistics. */
    val stats: SessionStats = SessionStats.EMPTY,

    /**
     * The last minute, oldest first, one point per 100 ms.
     *
     * `null` marks a stretch that could not be measured. Drawn as a gap rather than dropped, because
     * a chart that silently closes over the loudest part of a session is worse than one with a hole
     * in it.
     */
    val history: List<Double?> = emptyList(),
) : UiState {

    /** Whether the microphone is producing anything the screen can show. */
    val isLive: Boolean get() = capturing && !silenced && failure == null

    /** Whether a session can be started — permission granted and no capture failure. */
    val canMeasure: Boolean get() = permission == PermissionState.Granted && failure == null

    /** Whether the session has anything worth copying or sharing. */
    val hasSummary: Boolean get() = stats.hasMeasurement

    /**
     * What the big number shows.
     *
     * A sealed type rather than a nullable Double with flags beside it: two of the three non-values
     * mean opposite things — one is "louder than this can measure", the other "quieter" — and a
     * screen that rendered either as a number would be lying in precisely the situation the user
     * most needs the truth.
     */
    @Immutable
    sealed interface Reading {

        /**
         * A measurable level.
         *
         * @property dbSpl The level in dB SPL.
         */
        data class Level(val dbSpl: Double) : Reading

        /**
         * The converter is saturating. The true level is higher than anything that could be shown.
         *
         * Worth restating where the UI can see it: clipping does not cap a reading, it collapses
         * one. So this state is not "the number would be a bit off" — it is "a chainsaw and a
         * stadium would both read about the same here".
         */
        data object TooLoud : Reading

        /** Below the converter's noise floor — quieter than this can measure. */
        data object TooQuiet : Reading

        /** The microphone is open and nothing has arrived yet. */
        data object Waiting : Reading

        /**
         * Not listening, and not about to be — no permission, or no usable input.
         *
         * Distinct from [Waiting] because "Listening…" under a screen that has just been refused
         * microphone access is a straightforward lie, and the sort that erodes trust in every other
         * number the tool shows.
         */
        data object Idle : Reading
    }

    companion object {

        /** Where the gauge's arc starts and ends. Wide enough to hold everything a phone can read. */
        const val MIN_DISPLAY_DB = 20.0
        const val MAX_DISPLAY_DB = SoundReference.FULL_SCALE_DB_SPL + SoundReference.MAX_USER_OFFSET_DB

        /** One chart point per 100 ms, sixty seconds of them. */
        const val HISTORY_INTERVAL_SECONDS = 0.1
        const val HISTORY_POINTS = 600
    }
}

/**
 * Everything on the screen that is *not* the live reading.
 *
 * The gauge wants every block; the status lines, the buttons and the session figures do not. Pulling
 * them into one slice lets the whole lower half of the screen sit behind a single `derivedStateOf`
 * and recompose only when something in it actually changed.
 *
 * The statistics are **quantised** on the way in — decibels to a tenth, seconds to whole numbers —
 * because they otherwise change on every block for reasons the eye cannot resolve. The duration
 * ticking from 12.34 to 12.36 seconds is not a display update, it is a wasted frame.
 *
 * @property permission    The microphone permission state.
 * @property quality       How the input was opened.
 * @property silenced      Whether another app is holding the microphone.
 * @property failure       Why the capture is not running, or `null`.
 * @property weighting     The frequency weighting in effect.
 * @property timeWeighting The time weighting in effect.
 * @property measuring     Whether a session is accumulating.
 * @property canMeasure    Whether a session can be started.
 * @property hasSummary    Whether the session has anything worth copying or sharing.
 * @property stats         The quantised session statistics.
 */
@Immutable
internal data class MeterChrome(
    val permission: PermissionState,
    val quality: CaptureQuality,
    val silenced: Boolean,
    val failure: CaptureFailure?,
    val weighting: Weighting,
    val timeWeighting: TimeWeighting,
    val measuring: Boolean,
    val canMeasure: Boolean,
    val hasSummary: Boolean,
    val stats: SessionStats,
)

/**
 * Projects the full state onto the [MeterChrome] slice, quantising the statistics.
 *
 * @receiver The full meter state.
 * @return The chrome slice for the non-reading parts of the screen.
 */
internal fun SoundMeterState.toChrome(): MeterChrome = MeterChrome(
    permission = permission,
    quality = quality,
    silenced = silenced,
    failure = failure,
    weighting = weighting,
    timeWeighting = timeWeighting,
    measuring = measuring,
    canMeasure = canMeasure,
    hasSummary = hasSummary,
    stats = stats.quantised(),
)

private fun SessionStats.quantised() = copy(
    minDbSpl = minDbSpl?.toTenth(),
    maxDbSpl = maxDbSpl?.toTenth(),
    leqDbSpl = leqDbSpl?.toTenth(),
    durationSeconds = durationSeconds.toWholeSeconds(),
    secondsAboveThreshold = secondsAboveThreshold.toWholeSeconds(),
    unmeasurableSeconds = unmeasurableSeconds.toWholeSeconds(),
)

private fun Double.toTenth(): Double = kotlin.math.round(this * 10.0) / 10.0

private fun Double.toWholeSeconds(): Double = kotlin.math.floor(this)

internal sealed interface SoundMeterIntent : UiIntent {

    /**
     * The screen became visible, or stopped being visible.
     *
     * Drives the microphone. The ViewModel is scoped to the navigation entry and outlives the
     * screen, so collecting in `init` would leave the recorder open with the phone in a pocket —
     * which for this feature is a privacy incident rather than a battery cost.
     */
    data object ScreenResumed : SoundMeterIntent

    /** The screen stopped being visible. */
    data object ScreenPaused : SoundMeterIntent

    /**
     * The microphone permission request returned.
     *
     * @property granted            Whether the permission is granted.
     * @property shouldShowRationale The system's rationale flag.
     */
    data class PermissionResult(
        val granted: Boolean,
        val shouldShowRationale: Boolean,
    ) : SoundMeterIntent

    /** Open the app's system settings, to grant a permanently denied permission. */
    data object AppSettingsRequested : SoundMeterIntent

    /** Start a measurement session. */
    data object StartPressed : SoundMeterIntent

    /** Stop the measurement session. */
    data object StopPressed : SoundMeterIntent

    /** Reset the session statistics. */
    data object ResetPressed : SoundMeterIntent

    /**
     * The frequency weighting changed.
     *
     * @property weighting The newly selected weighting.
     */
    data class WeightingChanged(val weighting: Weighting) : SoundMeterIntent

    /**
     * The time weighting changed.
     *
     * @property timeWeighting The newly selected time weighting.
     */
    data class TimeWeightingChanged(val timeWeighting: TimeWeighting) : SoundMeterIntent

    /** Copy the session summary. */
    data object CopySummaryRequested : SoundMeterIntent

    /** Share the session summary. */
    data object ShareSummaryRequested : SoundMeterIntent
}

/** One-shot events from the meter screen. */
internal sealed interface SoundMeterEffect : UiEffect {

    /** Open the app's system settings. */
    data object OpenAppSettings : SoundMeterEffect

    /**
     * Carries the finished summary rather than the stats.
     *
     * The ViewModel cannot build the text — it needs `Resources` — so the screen resolves it and
     * these two are the request. Both go through the same builder as the visible readout, so what
     * gets pasted and what is on screen cannot drift apart.
     */
    data object CopySummary : SoundMeterEffect

    /** Share the session summary, resolved to text by the screen. */
    data object ShareSummary : SoundMeterEffect

    /**
     * Show a transient message.
     *
     * @property notice What to tell the user.
     */
    data class Notice(val notice: SoundMeterNotice) : SoundMeterEffect
}

/**
 * One-shot messages, shown as a snackbar.
 *
 * Only genuinely transient things belong here. Persistent conditions — silenced, out of range,
 * running on a processed input — go to the always-present status line instead, because a message
 * that dismisses itself cannot be trusted to represent a state the user is still in. That
 * distinction is the one `:feature:level` arrived at the hard way.
 */
internal enum class SoundMeterNotice {

    /** The session statistics were reset. */
    SessionReset,

    /** Stopped with nothing measurable in it — usually the microphone was silenced throughout. */
    NothingMeasured,

    /** Running on a processed input, so gain control may be shaping the reading. */
    ProcessedInput,
}
