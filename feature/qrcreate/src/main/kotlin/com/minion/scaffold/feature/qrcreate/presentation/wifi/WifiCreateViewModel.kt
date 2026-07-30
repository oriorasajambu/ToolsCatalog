package com.minion.scaffold.feature.qrcreate.presentation.wifi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.navigation.WifiCreateRoute
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.core.wifi.model.WifiBuildResult
import com.minion.scaffold.core.wifi.model.WifiField
import com.minion.scaffold.core.wifi.model.WifiSecurity
import com.minion.scaffold.core.wifi.usecase.BuildWifiPayloadUseCase
import com.minion.scaffold.core.wifi.usecase.ParseWifiPayloadUseCase
import com.minion.scaffold.feature.qrcreate.data.QrImageExporter
import com.minion.scaffold.feature.qrcreate.presentation.preview.ExportOutcome
import com.minion.scaffold.feature.qrcreate.presentation.preview.QrExportEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class WifiCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    parseWifiPayload: ParseWifiPayloadUseCase,
    private val buildWifiPayload: BuildWifiPayloadUseCase,
    private val imageExporter: QrImageExporter,
) : MviViewModel<WifiCreateState, WifiCreateIntent, QrExportEffect>(
    initialState(savedStateHandle, parseWifiPayload),
) {

    override fun onIntent(intent: WifiCreateIntent) {
        when (intent) {
            is WifiCreateIntent.SsidChanged -> editForm(WifiField.SSID) {
                it.copy(ssid = intent.value)
            }

            is WifiCreateIntent.PasswordChanged -> editForm(WifiField.PASSWORD) {
                it.copy(password = intent.value)
            }

            is WifiCreateIntent.SecurityChanged -> editForm(WifiField.PASSWORD) {
                // Switching to an open network drops the password rather than keeping one the
                // payload will not carry — a value the user can no longer see or clear is a value
                // that comes back the moment they switch away again.
                val open = intent.security == WifiSecurity.OPEN
                it.copy(
                    security = intent.security,
                    password = if (open) "" else it.password,
                )
            }

            is WifiCreateIntent.HiddenChanged -> editForm(clearing = null) {
                it.copy(hidden = intent.hidden)
            }

            WifiCreateIntent.GenerateRequested -> generate()

            WifiCreateIntent.CopyPayloadRequested -> currentState.payload?.let { payload ->
                viewModelScope.launch { emitEffect(QrExportEffect.CopyText(payload)) }
            }

            WifiCreateIntent.ShareImageRequested -> export { exporter, payload ->
                exporter.writeShareableImage(payload)?.let(QrExportEffect::ShareImage)
                    ?: QrExportEffect.ShowExportMessage(ExportOutcome.EXPORT_FAILED)
            }

            WifiCreateIntent.SaveImageRequested -> export { exporter, payload ->
                QrExportEffect.ShowExportMessage(
                    if (exporter.saveToGallery(payload)) {
                        ExportOutcome.SAVED_TO_GALLERY
                    } else {
                        ExportOutcome.EXPORT_FAILED
                    },
                )
            }
        }
    }

    /**
     * Applies a form change and drops what it invalidates: the generated payload always, and the
     * edited field's violation when there is one.
     *
     * A null [clearing] means the edit cannot have been wrong — toggling *hidden* has no violation
     * to clear — and `field == null` is false for every violation, so nothing is removed.
     */
    private fun editForm(
        clearing: WifiField?,
        transform: (WifiFormState) -> WifiFormState,
    ) {
        reduce {
            copy(
                form = transform(form),
                payload = null,
                violations = violations.filterNot { it.field == clearing },
            )
        }
    }

    private fun generate() {
        when (val result = buildWifiPayload(currentState.form.toCredentials())) {
            is WifiBuildResult.Success -> reduce {
                copy(payload = result.payload, violations = emptyList())
            }

            is WifiBuildResult.Invalid -> reduce {
                copy(payload = null, violations = result.violations)
            }
        }
    }

    private fun export(action: suspend (QrImageExporter, String) -> QrExportEffect) {
        val payload = currentState.payload ?: return
        if (currentState.exporting) return

        reduce { copy(exporting = true) }
        viewModelScope.launch {
            val effect = action(imageExporter, payload)
            reduce { copy(exporting = false) }
            emitEffect(effect)
        }
    }
}

/**
 * The form as it should first appear, filled from the route when there is a code to edit.
 *
 * Computed before construction so the first frame already shows the scanned network — see
 * `QrCreateViewModel` for why that matters.
 */
private fun initialState(
    savedStateHandle: SavedStateHandle,
    parseWifiPayload: ParseWifiPayloadUseCase,
): WifiCreateState {
    val payload = savedStateHandle.get<String>(WifiCreateRoute.ARG_PAYLOAD)
        ?: return WifiCreateState()

    val credentials = parseWifiPayload(payload)
        ?: return WifiCreateState(editing = true, prefillFailed = true)

    return WifiCreateState(form = credentials.toFormState(), editing = true)
}
