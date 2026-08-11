package com.minion.scaffold.core.sound.model

/**
 * One block of audio's verdict: a level, or a reason there isn't one.
 *
 * A sealed type rather than a nullable `Double` with a flag beside it, because the two failures mean
 * opposite things to the reader — one is "louder than this can measure", the other "quieter" — and a
 * caller that forgets to check would print a number that looks entirely ordinary. That is the exact
 * failure this feature exists to avoid: of the three ways a phone sound meter lies, two produce a
 * plausible figure rather than an obvious error.
 */
sealed interface BlockLevel {

    /** A level this device can actually claim to have measured, in dB SPL. */
    data class Measured(val dbSpl: Double) : BlockLevel

    /**
     * The converter saturated, so the true level is *at least* the number that would be reported.
     *
     * Worth being blunt about: clipping does not cap the reading, it **collapses** it. A clipped
     * waveform's RMS stops rising, so a chainsaw and a stadium both come out around the same
     * plausible-looking figure — under-reporting in precisely the situation where the number matters
     * most. There is no arithmetic that recovers the real level from a clipped block, so the meter
     * declines to print one.
     */
    data object Clipped : BlockLevel

    /** Below the converter's own noise. Not a quiet room measured well; not a measurement at all. */
    data object BelowFloor : BlockLevel
}
