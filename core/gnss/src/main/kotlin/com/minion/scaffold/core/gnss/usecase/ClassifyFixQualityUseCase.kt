package com.minion.scaffold.core.gnss.usecase

import com.minion.scaffold.core.gnss.model.FixQuality
import com.minion.scaffold.core.gnss.model.GnssFix
import javax.inject.Inject

/**
 * How much to trust the screen right now, in one word.
 *
 * ## From the accuracy figures, not from the satellite count
 *
 * Satellite count is the number every GPS app shows and it is a proxy for the thing that matters.
 * Eight satellites in a street canyon, all arriving by reflection, give a worse fix than five in the
 * open — and the receiver has already worked out which situation it is in and published the answer as
 * an accuracy estimate. Grading on the count would be re-deriving a worse version of something
 * already known.
 *
 * The satellite view still earns its place on the screen, but as a *diagnosis* during a cold start —
 * whether to keep waiting or go outside — rather than as a measure of quality once there is a fix.
 *
 * ## Graded on horizontal accuracy
 *
 * Position accuracy is the figure every receiver reports, on every API level. Speed accuracy would be
 * the more directly relevant number for a speedometer, but it arrived in API 26 and is not always
 * populated, so grading on it would leave some devices permanently ungraded.
 */
class ClassifyFixQualityUseCase @Inject constructor() {

    operator fun invoke(fix: GnssFix?): FixQuality {
        val accuracy = fix?.horizontalAccuracyMeters ?: return FixQuality.None

        return when {
            accuracy <= GOOD_METERS -> FixQuality.Good
            accuracy <= USABLE_METERS -> FixQuality.Usable
            else -> FixQuality.Poor
        }
    }

    private companion object {
        /** A clear sky with a modern multi-constellation chip. Speed is trustworthy here. */
        const val GOOD_METERS = 10.0

        /**
         * Workable for position and speed, visibly noisy for altitude.
         *
         * Past this — under trees, between buildings, indoors near a window — the numbers are still
         * numbers, and the user should know not to act on them.
         */
        const val USABLE_METERS = 30.0
    }
}
