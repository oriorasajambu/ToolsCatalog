package com.minion.scaffold.core.sound.model

/**
 * The constants that turn a digital amplitude into a claim about the world, and the honest limits
 * on that claim.
 *
 * Every number here is a property of typical phone hardware rather than of this device, because
 * Android exposes nothing about the microphone that would let it be otherwise. They are gathered in
 * one place so that the size of the assumption is visible, rather than spread across the code as
 * innocuous-looking literals.
 */
object SoundReference {

    /**
     * The sound pressure level a full-scale digital signal is taken to represent, in dB.
     *
     * **This is the whole calibration problem in one constant.** Converting dBFS to dB SPL requires
     * the microphone's sensitivity; Android has no API for it and it varies by 10–20 dB across
     * devices. 105 dB is a representative full-scale figure for a phone microphone on an
     * unprocessed input — which is also, by construction, the level at which the reading runs out of
     * range, and that self-consistency is the point: the meter cannot report a number above the
     * level at which its converter saturates.
     *
     * The user offset shifts this. Nothing in the app ever claims the result is calibrated, because
     * nothing the app can do would make that true.
     */
    const val FULL_SCALE_DB_SPL = 105.0

    /**
     * Below this, the signal is the preamp's own noise rather than the room.
     *
     * Deliberately conservative and stated in dBFS, since it describes the converter rather than any
     * particular acoustic level. A reading here is not a quiet room measured accurately; it is the
     * absence of a measurement, and the UI says so rather than printing a number.
     */
    const val NOISE_FLOOR_DBFS = -75.0

    /** How far the user may shift [FULL_SCALE_DB_SPL], in dB, in either direction. */
    const val MAX_USER_OFFSET_DB = 20.0

    /**
     * The exposure threshold marked on the gauge and shaded on the history chart, in dB(A).
     *
     * NIOSH's recommended exposure limit: 85 dB(A) as an 8-hour time-weighted average, with the
     * permissible duration halving for every 3 dB above it. The duration half of that is why the UI
     * states the caveat beside the number — a momentary 85 dB is not the same claim as 85 dB
     * sustained for a working day, and a gauge that coloured one like the other would be making a
     * health assertion it cannot support.
     */
    const val EXPOSURE_THRESHOLD_DB = 85.0

    /**
     * Full-scale magnitude for 16-bit PCM.
     *
     * 32768 rather than 32767, so that [Short.MIN_VALUE] normalises to exactly −1.0 and the two
     * rails are symmetric. The 0.003% asymmetry it introduces at the positive rail is three orders
     * of magnitude below anything this measures.
     */
    const val FULL_SCALE_PCM16 = 32768.0

    /**
     * The total offset applied to a dBFS figure, clamped.
     *
     * Clamped here rather than in the UI so that a value restored from a store written by an older
     * build, or by a slider whose range later changes, cannot silently exceed what the screen would
     * have allowed.
     *
     * @param userOffsetDb The user's calibration offset in dB; clamped to ±[MAX_USER_OFFSET_DB].
     * @return The full-scale dB SPL reference shifted by the clamped offset.
     */
    fun offsetDb(userOffsetDb: Double): Double =
        FULL_SCALE_DB_SPL + userOffsetDb.coerceIn(-MAX_USER_OFFSET_DB, MAX_USER_OFFSET_DB)
}
