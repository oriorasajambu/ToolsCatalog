package com.minion.scaffold.feature.qrcreate.presentation.url

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.navigation.UrlCreateRoute
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.core.url.model.UrlBuildResult
import com.minion.scaffold.core.url.usecase.BuildUrlPayloadUseCase
import com.minion.scaffold.core.url.usecase.ParseUrlPayloadUseCase
import com.minion.scaffold.feature.qrcreate.data.QrImageExporter
import com.minion.scaffold.feature.qrcreate.presentation.preview.ExportOutcome
import com.minion.scaffold.feature.qrcreate.presentation.preview.QrExportEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class UrlCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    parseUrlPayload: ParseUrlPayloadUseCase,
    private val buildUrlPayload: BuildUrlPayloadUseCase,
    private val imageExporter: QrImageExporter,
) : MviViewModel<UrlCreateState, UrlCreateIntent, QrExportEffect>(
    initialState(savedStateHandle, parseUrlPayload),
) {

    override fun onIntent(intent: UrlCreateIntent) {
        when (intent) {
            is UrlCreateIntent.LinkChanged -> reduce {
                // The generated payload goes with any edit: a QR beside a link it no longer matches
                // would open somewhere other than what is written next to it.
                copy(link = intent.value, violation = null, payload = null)
            }

            UrlCreateIntent.GenerateRequested -> generate()

            UrlCreateIntent.CopyPayloadRequested -> currentState.payload?.let { payload ->
                viewModelScope.launch { emitEffect(QrExportEffect.CopyText(payload)) }
            }

            UrlCreateIntent.ShareImageRequested -> export { exporter, payload ->
                exporter.writeShareableImage(payload)?.let(QrExportEffect::ShareImage)
                    ?: QrExportEffect.ShowExportMessage(ExportOutcome.EXPORT_FAILED)
            }

            UrlCreateIntent.SaveImageRequested -> export { exporter, payload ->
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
     * Validates, then writes the payload if it passes.
     *
     * On success the field is replaced with the normalised payload, so someone who typed
     * `example.com` can see that the code says `https://example.com` rather than having to trust it.
     */
    private fun generate() {
        when (val result = buildUrlPayload(currentState.link)) {
            is UrlBuildResult.Success -> reduce {
                copy(link = result.payload, payload = result.payload, violation = null)
            }

            is UrlBuildResult.Invalid -> reduce {
                copy(payload = null, violation = result.reason)
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

/** See `QrCreateViewModel` for why the first frame is built before construction. */
private fun initialState(
    savedStateHandle: SavedStateHandle,
    parseUrlPayload: ParseUrlPayloadUseCase,
): UrlCreateState {
    val payload = savedStateHandle.get<String>(UrlCreateRoute.ARG_PAYLOAD)
        ?: return UrlCreateState()

    val link = parseUrlPayload(payload)
        ?: return UrlCreateState(editing = true, prefillFailed = true)

    return UrlCreateState(link = link, editing = true)
}
