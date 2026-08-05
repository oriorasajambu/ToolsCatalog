package com.minion.scaffold.feature.ocr.presentation.settings

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.feature.ocr.domain.ObserveOcrEngineUseCase
import com.minion.scaffold.feature.ocr.domain.SetOcrEngineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OcrSettingsViewModel @Inject constructor(
    observeOcrEngine: ObserveOcrEngineUseCase,
    private val setOcrEngine: SetOcrEngineUseCase,
) : MviViewModel<OcrSettingsState, OcrSettingsIntent, OcrSettingsEffect>(OcrSettingsState()) {

    init {
        // The screen renders what DataStore says, not what was tapped: the write below is the only
        // way the value changes, and it comes back through here. One source of truth, so a failed
        // write can never leave the radio group showing an engine that was not saved.
        observeOcrEngine()
            .onEach { engine -> reduce { copy(engine = engine) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: OcrSettingsIntent) {
        when (intent) {
            is OcrSettingsIntent.EngineSelected -> viewModelScope.launch {
                setOcrEngine(intent.engine)
            }
        }
    }
}
