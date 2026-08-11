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

    val weighting: Flow<Weighting>

    val timeWeighting: Flow<TimeWeighting>

    suspend fun setOffsetDb(offsetDb: Double)

    suspend fun setWeighting(weighting: Weighting)

    suspend fun setTimeWeighting(timeWeighting: TimeWeighting)
}

internal class ObserveSoundPreferencesUseCase @Inject constructor(
    private val repository: SoundMeterPreferencesRepository,
) {
    val offsetDb: Flow<Double> get() = repository.offsetDb
    val weighting: Flow<Weighting> get() = repository.weighting
    val timeWeighting: Flow<TimeWeighting> get() = repository.timeWeighting
}

internal class SetOffsetDbUseCase @Inject constructor(
    private val repository: SoundMeterPreferencesRepository,
) {
    suspend operator fun invoke(offsetDb: Double) = repository.setOffsetDb(offsetDb)
}

internal class SetWeightingUseCase @Inject constructor(
    private val repository: SoundMeterPreferencesRepository,
) {
    suspend operator fun invoke(weighting: Weighting) = repository.setWeighting(weighting)
}

internal class SetTimeWeightingUseCase @Inject constructor(
    private val repository: SoundMeterPreferencesRepository,
) {
    suspend operator fun invoke(timeWeighting: TimeWeighting) =
        repository.setTimeWeighting(timeWeighting)
}
