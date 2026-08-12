package com.minion.scaffold.feature.ocr.domain

import com.minion.scaffold.core.ocr.model.OcrEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reads and writes the recognition-engine preference. Mirrors `WeatherUnitUseCases`. */

/** Observes the selected recognition engine. */
internal class ObserveOcrEngineUseCase @Inject constructor(
    private val repository: OcrPreferencesRepository,
) {
    /** @return A [Flow] of the selected [OcrEngine], starting at [OcrEngine.DEFAULT]. */
    operator fun invoke(): Flow<OcrEngine> = repository.engine
}

/** Sets the recognition engine. */
internal class SetOcrEngineUseCase @Inject constructor(
    private val repository: OcrPreferencesRepository,
) {
    /** @param engine The engine to select. */
    suspend operator fun invoke(engine: OcrEngine) = repository.setEngine(engine)
}
