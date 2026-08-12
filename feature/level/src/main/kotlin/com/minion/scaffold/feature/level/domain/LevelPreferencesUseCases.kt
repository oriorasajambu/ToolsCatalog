package com.minion.scaffold.feature.level.domain

import com.minion.scaffold.core.level.model.Calibration
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reads and writes what the level remembers. Mirrors `OcrEngineUseCases`. */

/** Observes the stored device calibration. */
internal class ObserveCalibrationUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    /** @return A [Flow] of the current [Calibration], starting at [Calibration.NONE]. */
    operator fun invoke(): Flow<Calibration> = repository.calibration
}

/** Stores a completed calibration. */
internal class SaveCalibrationUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    /** @param calibration The device bias to remember. */
    suspend operator fun invoke(calibration: Calibration) = repository.setCalibration(calibration)
}

/** Clears the stored calibration, returning the device to uncalibrated. */
internal class ClearCalibrationUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    suspend operator fun invoke() = repository.clearCalibration()
}

/** Observes whether the level's beeper is enabled. */
internal class ObserveSoundEnabledUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    /** @return A [Flow] of the beeper toggle, `false` by default. */
    operator fun invoke(): Flow<Boolean> = repository.soundEnabled
}

/** Turns the level's beeper on or off. */
internal class SetSoundEnabledUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    /** @param enabled Whether the beeper should sound. */
    suspend operator fun invoke(enabled: Boolean) = repository.setSoundEnabled(enabled)
}

/** Observes whether the first-use calibration prompt has been dismissed. */
internal class ObserveCalibrationPromptSeenUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    /** @return A [Flow] of whether the prompt has been seen. */
    operator fun invoke(): Flow<Boolean> = repository.calibrationPromptSeen
}

/** Records that the first-use calibration prompt has been dismissed. */
internal class DismissCalibrationPromptUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    suspend operator fun invoke() = repository.setCalibrationPromptSeen()
}
