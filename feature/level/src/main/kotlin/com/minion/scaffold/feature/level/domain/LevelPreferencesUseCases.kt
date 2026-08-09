package com.minion.scaffold.feature.level.domain

import com.minion.scaffold.core.level.model.Calibration
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reads and writes what the level remembers. Mirrors `OcrEngineUseCases`. */

internal class ObserveCalibrationUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    operator fun invoke(): Flow<Calibration> = repository.calibration
}

internal class SaveCalibrationUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    suspend operator fun invoke(calibration: Calibration) = repository.setCalibration(calibration)
}

internal class ClearCalibrationUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    suspend operator fun invoke() = repository.clearCalibration()
}

internal class ObserveSoundEnabledUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.soundEnabled
}

internal class SetSoundEnabledUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setSoundEnabled(enabled)
}

internal class ObserveCalibrationPromptSeenUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.calibrationPromptSeen
}

internal class DismissCalibrationPromptUseCase @Inject constructor(
    private val repository: LevelPreferencesRepository,
) {
    suspend operator fun invoke() = repository.setCalibrationPromptSeen()
}
