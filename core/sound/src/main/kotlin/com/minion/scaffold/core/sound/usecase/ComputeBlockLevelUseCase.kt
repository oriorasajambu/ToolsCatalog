package com.minion.scaffold.core.sound.usecase

import com.minion.scaffold.core.sound.model.BlockLevel
import com.minion.scaffold.core.sound.model.SoundReference
import javax.inject.Inject
import kotlin.math.log10

/**
 * Turns one block of audio into a level, or into a reason it has none.
 *
 * Takes both the raw PCM and its weighted form, because the two questions are asked of different
 * signals and getting that backwards produces a specific, believable wrong answer:
 *
 *  - **Clipping and the noise floor are properties of the converter**, so they are judged on the raw
 *    input. A rumble loud enough to saturate the ADC is still saturating it when A-weighting later
 *    removes 40 dB of it — check clipping on the weighted signal and the meter would cheerfully
 *    report a comfortable 70 dB for an input that is destroying the waveform.
 *  - **The level itself is a property of the weighted signal**, because that is what the reading
 *    claims to be.
 */
class ComputeBlockLevelUseCase @Inject constructor() {

    /**
     * @param raw the block as captured, 16-bit PCM.
     * @param weighted the same block after [WeightingFilter.process], normalised to ±1.0.
     * @param count how many samples of each are valid.
     * @param offsetDb from [SoundReference.offsetDb] — the unknowable part, in one place.
     */
    operator fun invoke(
        raw: ShortArray,
        weighted: DoubleArray,
        count: Int,
        offsetDb: Double,
    ): BlockLevel {
        if (count <= 0) return BlockLevel.BelowFloor

        var rawSumOfSquares = 0.0

        for (index in 0 until count) {
            val sample = raw[index]

            // A single sample on either rail is enough. At ~47 blocks a second the cost of being
            // wrong in this direction is one dropped block; the cost of being wrong in the other is
            // a confident number for a level the hardware cannot represent.
            if (sample == Short.MAX_VALUE || sample == Short.MIN_VALUE) return BlockLevel.Clipped

            val normalised = sample / SoundReference.FULL_SCALE_PCM16
            rawSumOfSquares += normalised * normalised
        }

        val rawMeanSquare = rawSumOfSquares / count
        if (rawMeanSquare <= 0.0) return BlockLevel.BelowFloor
        if (10.0 * log10(rawMeanSquare) < SoundReference.NOISE_FLOOR_DBFS) {
            return BlockLevel.BelowFloor
        }

        var weightedSumOfSquares = 0.0
        for (index in 0 until count) {
            val sample = weighted[index]
            weightedSumOfSquares += sample * sample
        }

        val weightedMeanSquare = weightedSumOfSquares / count
        if (weightedMeanSquare <= 0.0) return BlockLevel.BelowFloor

        // 10·log₁₀ of a mean square, not 20·log₁₀ of an RMS — the same number, one square root
        // cheaper, and it keeps every conversion in this module in the energy domain where the
        // averaging is done.
        return BlockLevel.Measured(10.0 * log10(weightedMeanSquare) + offsetDb)
    }
}
