package com.minion.scaffold.feature.ocr.domain

import com.minion.scaffold.core.ocr.model.OcrEngine
import kotlinx.coroutines.flow.Flow

/**
 * The user's recognition-engine choice.
 *
 * A [Flow] rather than a one-shot read because two things watch it at once: the settings screen
 * that writes it, and the OCR screen sitting behind it on the back stack — which re-runs
 * recognition on the capture it is already holding the moment the choice changes.
 */
internal interface OcrPreferencesRepository {

    /** Defaults to [OcrEngine.DEFAULT]. */
    val engine: Flow<OcrEngine>

    /**
     * Stores the selected recognition engine.
     *
     * @param engine The engine to select.
     */
    suspend fun setEngine(engine: OcrEngine)
}
