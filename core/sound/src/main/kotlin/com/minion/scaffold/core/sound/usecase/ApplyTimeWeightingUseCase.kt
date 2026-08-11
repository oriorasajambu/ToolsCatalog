package com.minion.scaffold.core.sound.usecase

import com.minion.scaffold.core.sound.model.TimeWeighting
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * Accumulated smoothing state. Threaded by the caller so nothing here holds it.
 *
 * `null` means "not yet seeded" — the average starts from the first real block rather than from
 * zero, or every session would open with the reading crawling up from silence for a second.
 */
data class TimeWeightingState(val meanSquare: Double? = null) {

    /** The smoothed level in dB SPL, or `null` before the first block. */
    val levelDbSpl: Double?
        get() = meanSquare?.takeIf { it > 0.0 }?.let { 10.0 * log10(it) }
}

/**
 * Fast and Slow — the exponential averaging that makes a level readable.
 *
 * ## Averaged in energy, never in decibels
 *
 * The average runs on the **mean square**, which is what IEC 61672-1 specifies and, separately, the
 * only thing that means anything. Decibels are logarithmic, so smoothing them averages exponents: a
 * signal alternating between 60 and 90 dB would settle at 75, when its actual mean power corresponds
 * to 87. Both are stable, both look plausible, and only one is the level. The conversion back to dB
 * happens once, at the end, in [TimeWeightingState.levelDbSpl].
 *
 * ## dt-based, like the level's filter
 *
 * `α = 1 − exp(−dt/τ)` is the exact discretisation of a first-order low-pass. Not `dt/(dt + τ)`,
 * which is the backward-Euler approximation, and certainly not a fixed α — block cadence follows the
 * sample rate and buffer size the device actually granted, so a fixed α would make Fast mean
 * different things on different phones.
 */
class ApplyTimeWeightingUseCase @Inject constructor() {

    /**
     * Folds one block's level into the exponential average.
     *
     * @param state         The accumulated smoothing state from the previous call.
     * @param blockDbSpl    The block's own level, from [ComputeBlockLevelUseCase].
     * @param timeWeighting Fast or Slow — the time constant to apply.
     * @param blockSeconds  How much time the block covers — `samples / sampleRate`.
     * @return The updated state whose [TimeWeightingState.levelDbSpl] is the smoothed reading.
     */
    operator fun invoke(
        state: TimeWeightingState,
        blockDbSpl: Double,
        timeWeighting: TimeWeighting,
        blockSeconds: Double,
    ): TimeWeightingState {
        // Reconstructing the mean square from the level is exact — the level is 10·log₁₀ of it — so
        // carrying decibels between the stages costs nothing and keeps the pipeline's units uniform.
        val blockMeanSquare = 10.0.pow(blockDbSpl / 10.0)
        val previous = state.meanSquare ?: return TimeWeightingState(blockMeanSquare)

        if (blockSeconds <= 0.0) return state

        val alpha = 1.0 - exp(-blockSeconds / timeWeighting.tauSeconds)
        return TimeWeightingState(previous + alpha * (blockMeanSquare - previous))
    }
}
