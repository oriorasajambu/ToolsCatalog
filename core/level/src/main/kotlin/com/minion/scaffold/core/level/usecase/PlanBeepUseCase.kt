package com.minion.scaffold.core.level.usecase

import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** What the beeper should be doing. */
sealed interface BeepPlan {

    /** Not beeping — the feature is off. */
    data object Silent : BeepPlan

    /** Repeating at [intervalMillis]. Shorter means closer to level. */
    data class Pulse(val intervalMillis: Long) : BeepPlan

    /** A continuous tone: inside tolerance. */
    data object Steady : BeepPlan
}

/**
 * Turns "how far off level" into a beep rhythm.
 *
 * The parking-sensor language: pulses that quicken as you approach, resolving into a steady tone
 * once you arrive. Everyone already knows it from reversing a car, and unlike a single confirmation
 * beep the *rate* tells you how far off you still are — which is what makes it usable with the phone
 * somewhere you cannot see it.
 *
 * The mapping is **logarithmic** because the perception of tempo is: a linear map spends most of its
 * resolution on angles nobody is trying to hit, and feels dead over the last degree where all the
 * adjusting happens.
 *
 * Pure, so the rhythm is unit-testable without an audio device. The loop that acts on it lives in
 * the composable — see the feature's `rememberLevelTone`.
 */
class PlanBeepUseCase @Inject constructor() {

    /**
     * @param deviationDegrees how far from level, unsigned
     * @param wasSteady whether the previous plan was [BeepPlan.Steady], for the hysteresis below
     */
    operator fun invoke(
        deviationDegrees: Double,
        enabled: Boolean,
        wasSteady: Boolean,
    ): BeepPlan {
        if (!enabled) return BeepPlan.Silent

        val deviation = abs(deviationDegrees)

        // Hysteresis, or the tone chatters between steady and pulsing while the user hovers at the
        // boundary — which is precisely where they will spend their time.
        val threshold = if (wasSteady) STEADY_EXIT_DEGREES else STEADY_ENTER_DEGREES
        if (deviation <= threshold) return BeepPlan.Steady

        return BeepPlan.Pulse(intervalMillis = intervalFor(deviation))
    }

    private fun intervalFor(deviation: Double): Long {
        // Never fall silent past the far end: silence reads as a broken app, not as "very far off".
        val clamped = deviation.coerceIn(STEADY_ENTER_DEGREES, MAX_DEVIATION_DEGREES)

        val progress = ln(clamped / STEADY_ENTER_DEGREES) /
            ln(MAX_DEVIATION_DEGREES / STEADY_ENTER_DEGREES)

        val interval = MIN_INTERVAL_MILLIS *
            (MAX_INTERVAL_MILLIS / MIN_INTERVAL_MILLIS).pow(progress)

        return interval.toLong().coerceIn(MIN_INTERVAL_MILLIS.toLong(), MAX_INTERVAL_MILLIS.toLong())
    }

    companion object {

        /** Inside this, it is level. Matches the tolerance the display goes green at. */
        const val STEADY_ENTER_DEGREES = 0.2

        /** Must get this far out before pulsing resumes. */
        const val STEADY_EXIT_DEGREES = 0.35

        /** Beyond this the interval stops growing. */
        const val MAX_DEVIATION_DEGREES = 20.0

        /**
         * Fastest pulse. Kept comfortably above the tone's own duration, or beeps overlap and
         * stack into a buzz instead of quickening.
         */
        const val MIN_INTERVAL_MILLIS = 90.0

        const val MAX_INTERVAL_MILLIS = 900.0

        /** How long each pulse sounds for. */
        const val TONE_DURATION_MILLIS = 40
    }
}
