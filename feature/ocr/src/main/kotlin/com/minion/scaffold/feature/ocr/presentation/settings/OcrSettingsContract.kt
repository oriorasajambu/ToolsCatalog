package com.minion.scaffold.feature.ocr.presentation.settings

import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.ocr.model.OcrEngine

/**
 * The engine picker.
 *
 * No `ContentState` here, matching `WeatherSettingsState`: there is exactly one field, it always
 * has a value, and it is read from disk rather than the network — so there is no loading, empty or
 * failure phase for a sealed hierarchy to make unrepresentable.
 */
internal data class OcrSettingsState(
    /** The currently selected recognition engine. */
    val engine: OcrEngine = OcrEngine.DEFAULT,
) : UiState

/** Everything the user can do on the engine picker. */
internal sealed interface OcrSettingsIntent : UiIntent {

    /**
     * A recognition engine was selected.
     *
     * @property engine The newly selected engine.
     */
    data class EngineSelected(val engine: OcrEngine) : OcrSettingsIntent
}

/** Declared for the contract's shape; this screen has no one-shot events. */
internal sealed interface OcrSettingsEffect : UiEffect
