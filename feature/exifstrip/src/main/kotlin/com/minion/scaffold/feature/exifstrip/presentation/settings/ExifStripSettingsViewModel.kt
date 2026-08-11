package com.minion.scaffold.feature.exifstrip.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.feature.exifstrip.domain.ObserveKeepColourProfileUseCase
import com.minion.scaffold.feature.exifstrip.domain.SetKeepColourProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Not an `MviViewModel`: one boolean, one control, nothing one-shot to emit.
 *
 * The MVI scaffolding exists to keep complex screens honest, and wrapping a single switch in a
 * State, an Intent and an Effect would be ceremony rather than structure — the same call
 * `:feature:soundmeter` makes for its offset.
 */
@HiltViewModel
internal class ExifStripSettingsViewModel @Inject constructor(
    observeKeepColourProfile: ObserveKeepColourProfileUseCase,
    private val setKeepColourProfile: SetKeepColourProfileUseCase,
) : ViewModel() {

    val keepColourProfile: StateFlow<Boolean> = observeKeepColourProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = true,
        )

    fun onKeepColourProfileChange(keep: Boolean) {
        viewModelScope.launch { setKeepColourProfile(keep) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
