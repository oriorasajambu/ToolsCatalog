package com.minion.scaffold.feature.soundmeter.domain

import com.minion.scaffold.core.sound.model.TimeWeighting
import com.minion.scaffold.core.sound.model.Weighting
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * What the meter remembers between visits.
 *
 * All three are settings rather than session state: someone who works in dB(C) wants dB(C) next
 * time, and an offset that had to be re-entered on every launch would be worse than no offset at
 * all.
 */
internal interface SoundMeterPreferencesRepository {

    /**
     * The calibration offset in dB, defaulting to zero.
     *
     * Shifts [com.minion.scaffold.core.sound.model.SoundReference.FULL_SCALE_DB_SPL]. Setting it
     * does **not** make the reading calibrated and the UI never says it does — a slider establishes
     * nothing, so the approximate-reading notice is permanent rather than conditional on this being
     * non-zero.
     */
    val offsetDb: Flow<Double>

    /** The frequency weighting (A/C/Z) to apply. */
    val weighting: Flow<Weighting>

    /** The time weighting (Fast/Slow) to apply. */
    val timeWeighting: Flow<TimeWeighting>

    /**
     * Sets the calibration offset.
     *
     * @param offsetDb The offset in dB.
     */
    suspend fun setOffsetDb(offsetDb: Double)

    /**
     * Sets the frequency weighting.
     *
     * @param weighting The weighting curve to apply.
     */
    suspend fun setWeighting(weighting: Weighting)

    /**
     * Sets the time weighting.
     *
     * @param timeWeighting The time weighting to apply.
     */
    suspend fun setTimeWeighting(timeWeighting: TimeWeighting)
}

/** Observes the meter's three preferences. */
internal class ObserveSoundPreferencesUseCase @Inject constructor(
    private val repository: SoundMeterPreferencesRepository,
) {
    /** The calibration offset in dB. */
    val offsetDb: Flow<Double> get() = repository.offsetDb

    /** The frequency weighting. */
    val weighting: Flow<Weighting> get() = repository.weighting

    /** The time weighting. */
    val timeWeighting: Flow<TimeWeighting> get() = repository.timeWeighting
}

/** Sets the calibration offset. */
internal class SetOffsetDbUseCase @Inject constructor(
    private val repository: SoundMeterPreferencesRepository,
) {
    /** @param offsetDb The offset in dB. */
    suspend operator fun invoke(offsetDb: Double) = repository.setOffsetDb(offsetDb)
}

/** Sets the frequency weighting. */
internal class SetWeightingUseCase @Inject constructor(
    private val repository: SoundMeterPreferencesRepository,
) {
    /** @param weighting The weighting curve to apply. */
    suspend operator fun invoke(weighting: Weighting) = repository.setWeighting(weighting)
}

/** Sets the time weighting. */
internal class SetTimeWeightingUseCase @Inject constructor(
    private val repository: SoundMeterPreferencesRepository,
) {
    /** @param timeWeighting The time weighting to apply. */
    suspend operator fun invoke(timeWeighting: TimeWeighting) =
        repository.setTimeWeighting(timeWeighting)
}
