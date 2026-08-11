package com.minion.scaffold.core.sound.usecase

import com.minion.scaffold.core.sound.model.BlockLevel
import com.minion.scaffold.core.sound.model.SessionState
import com.minion.scaffold.core.sound.model.SoundReference
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.max
import kotlin.math.pow

/**
 * Folds one block into the session totals.
 *
 * ## Two levels go in, and they are not interchangeable
 *
 * [block] is the block's own level, with no time weighting. [displayedDbSpl] is the smoothed value
 * on screen. Leq takes the former and min/max take the latter, which is what a real instrument
 * reports: `LAeq` is an energy average of the signal, while `LAFmax` is explicitly the maximum of
 * the *Fast-weighted* level. Feeding Leq the smoothed value would average it twice and quietly
 * shave the peaks off the answer.
 *
 * ## Out-of-range blocks are counted, never valued
 *
 * A clipped block has a level — it is just wrong, and wrong downwards. Letting one set the session
 * maximum would put the meter's most prominent number at its least trustworthy moment. Clipped and
 * below-floor blocks therefore contribute to [SessionState.unmeasurableSeconds] and to nothing else.
 */
class AccumulateSessionUseCase @Inject constructor() {

    /**
     * Folds one block into the running session totals.
     *
     * @param state          The accumulated session state from the previous call.
     * @param block          The block's own level, or the reason it has none.
     * @param displayedDbSpl The time-weighted level, or `null` before the smoothing has seeded.
     * @param blockSeconds   How much time this block covers.
     * @return The updated [SessionState].
     */
    operator fun invoke(
        state: SessionState,
        block: BlockLevel,
        displayedDbSpl: Double?,
        blockSeconds: Double,
    ): SessionState {
        if (blockSeconds <= 0.0) return state

        val elapsed = state.copy(durationSeconds = state.durationSeconds + blockSeconds)

        if (block !is BlockLevel.Measured) {
            return elapsed.copy(
                unmeasurableSeconds = elapsed.unmeasurableSeconds + blockSeconds,
            )
        }

        val withEnergy = elapsed.copy(
            energySeconds = elapsed.energySeconds +
                10.0.pow(block.dbSpl / 10.0) * blockSeconds,
            measuredSeconds = elapsed.measuredSeconds + blockSeconds,
        )

        val displayed = displayedDbSpl ?: return withEnergy

        return withEnergy.copy(
            minDbSpl = withEnergy.minDbSpl?.let { min(it, displayed) } ?: displayed,
            maxDbSpl = withEnergy.maxDbSpl?.let { max(it, displayed) } ?: displayed,
            secondsAboveThreshold = withEnergy.secondsAboveThreshold +
                if (displayed >= SoundReference.EXPOSURE_THRESHOLD_DB) blockSeconds else 0.0,
        )
    }
}
