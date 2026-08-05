package com.minion.scaffold.feature.ocr.domain

import com.minion.scaffold.core.ocr.model.OcrEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reads and writes the recognition-engine preference. Mirrors `WeatherUnitUseCases`. */

internal class ObserveOcrEngineUseCase @Inject constructor(
    private val repository: OcrPreferencesRepository,
) {
    operator fun invoke(): Flow<OcrEngine> = repository.engine
}

internal class SetOcrEngineUseCase @Inject constructor(
    private val repository: OcrPreferencesRepository,
) {
    suspend operator fun invoke(engine: OcrEngine) = repository.setEngine(engine)
}
