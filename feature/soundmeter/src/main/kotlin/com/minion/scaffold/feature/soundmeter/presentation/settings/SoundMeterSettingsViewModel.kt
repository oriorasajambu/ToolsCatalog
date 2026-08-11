package com.minion.scaffold.feature.soundmeter.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.feature.soundmeter.domain.ObserveSoundPreferencesUseCase
import com.minion.scaffold.feature.soundmeter.domain.SetOffsetDbUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The settings screen's state.
 *
 * Not an `MviViewModel`: there is one value, one control and nothing one-shot to emit. The MVI
 * scaffolding exists to keep complex screens honest, and wrapping a single slider in a State, an
 * Intent and an Effect would be ceremony rather than structure — the same call `:feature:weather`
 * makes for its units toggle.
 */
internal data class SoundMeterSettingsState(val offsetDb: Double = 0.0)

@HiltViewModel
internal class SoundMeterSettingsViewModel @Inject constructor(
    observeSoundPreferences: ObserveSoundPreferencesUseCase,
    private val setOffsetDb: SetOffsetDbUseCase,
) : ViewModel() {

    val state: StateFlow<SoundMeterSettingsState> = observeSoundPreferences.offsetDb
        .map(::SoundMeterSettingsState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SoundMeterSettingsState(),
        )

    /**
     * Writes on every drag position.
     *
     * DataStore coalesces writes to the same key, and the alternative — holding a local value and
     * committing on release — means the slider and the store disagree for as long as a finger is
     * down. On a screen whose entire purpose is one number, that gap is where a lost edit would
     * hide.
     */
    fun onOffsetChange(offsetDb: Double) {
        viewModelScope.launch { setOffsetDb(offsetDb) }
    }

    fun onOffsetReset() {
        viewModelScope.launch { setOffsetDb(0.0) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
