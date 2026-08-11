package com.minion.scaffold.feature.soundmeter.presentation.component

import androidx.annotation.StringRes
import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.feature.soundmeter.R

/**
 * The coloured regions of the gauge, and what they are based on.
 *
 * **Where the boundaries go is a claim, so they are not invented.** Colouring a band "harmful"
 * without a source would be making a health statement quietly, which is worse than making one
 * openly. Each boundary below is a published figure, the source is named on screen, and the one that
 * matters most carries the caveat that turns it from a number into a statement someone can act on:
 * 85 dB is a limit *over an eight-hour day*, not a level that is dangerous the moment it is reached.
 *
 * The comparison labels do the other half of the job. "72 dB" means nothing to most people; "busy
 * street" means something immediately, and it lets someone sanity-check a reading against their own
 * ears — which on an uncalibrated microphone is a genuinely useful check.
 */
internal enum class NoiseBand(
    /** Upper bound in dB, exclusive. The last band has none. */
    val upperDb: Double?,
    @param:StringRes val labelRes: Int,
) {

    /** Below the WHO's community-annoyance guideline. */
    Quiet(upperDb = 55.0, labelRes = R.string.soundmeter_band_quiet),

    /** Between the annoyance guideline and the WHO's 70 dB 24-hour hearing-protection figure. */
    Moderate(upperDb = 70.0, labelRes = R.string.soundmeter_band_moderate),

    /** Above 70 dB but below the NIOSH exposure limit. */
    Loud(upperDb = SoundReference.EXPOSURE_THRESHOLD_DB, labelRes = R.string.soundmeter_band_loud),

    /**
     * At or above NIOSH's recommended exposure limit of 85 dB(A) as an 8-hour average.
     *
     * The permissible duration halves for every 3 dB above it, which is why the screen states
     * duration alongside the number rather than treating the threshold as a simple line.
     */
    Harmful(upperDb = null, labelRes = R.string.soundmeter_band_harmful),
    ;

    companion object {

        fun of(dbSpl: Double): NoiseBand =
            entries.firstOrNull { it.upperDb != null && dbSpl < it.upperDb } ?: Harmful
    }
}
