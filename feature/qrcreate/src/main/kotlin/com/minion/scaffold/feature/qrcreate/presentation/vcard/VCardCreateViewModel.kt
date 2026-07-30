package com.minion.scaffold.feature.qrcreate.presentation.vcard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.navigation.VCardCreateRoute
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.core.vcard.model.VCardBuildResult
import com.minion.scaffold.core.vcard.usecase.BuildVCardPayloadUseCase
import com.minion.scaffold.core.vcard.usecase.ParseVCardPayloadUseCase
import com.minion.scaffold.feature.qrcreate.data.QrImageExporter
import com.minion.scaffold.feature.qrcreate.presentation.preview.ExportOutcome
import com.minion.scaffold.feature.qrcreate.presentation.preview.QrExportEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class VCardCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    parseVCardPayload: ParseVCardPayloadUseCase,
    private val buildVCardPayload: BuildVCardPayloadUseCase,
    private val imageExporter: QrImageExporter,
) : MviViewModel<VCardCreateState, VCardCreateIntent, QrExportEffect>(
    initialState(savedStateHandle, parseVCardPayload),
) {

    override fun onIntent(intent: VCardCreateIntent) {
        when (intent) {
            is VCardCreateIntent.FieldChanged -> editForm(intent.field) {
                it.withField(intent.field, intent.value)
            }

            VCardCreateIntent.GenerateRequested -> generate()

            VCardCreateIntent.CopyPayloadRequested -> currentState.payload?.let { payload ->
                viewModelScope.launch { emitEffect(QrExportEffect.CopyText(payload)) }
            }

            VCardCreateIntent.ShareImageRequested -> export { exporter, payload ->
                exporter.writeShareableImage(payload)?.let(QrExportEffect::ShareImage)
                    ?: QrExportEffect.ShowExportMessage(ExportOutcome.EXPORT_FAILED)
            }

            VCardCreateIntent.SaveImageRequested -> export { exporter, payload ->
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
     * Applies a field change, discards the generated payload, and clears that field's violation.
     *
     * A field with no domain violation to clear — a given name, an organisation — filters nothing,
     * because `it.field == null` is false for every violation.
     */
    private fun editForm(
        field: VCardFormField,
        transform: (VCardFormState) -> VCardFormState,
    ) {
        reduce {
            copy(
                form = transform(form),
                payload = null,
                violations = violations.filterNot { it.field == field.domainField },
            )
        }
    }

    private fun generate() {
        when (val result = buildVCardPayload(currentState.form.toCard())) {
            is VCardBuildResult.Success -> reduce {
                copy(payload = result.payload, violations = emptyList())
            }

            is VCardBuildResult.Invalid -> reduce {
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
 * Sets a field, keeping the display name in step with the name components until it is edited.
 *
 * `FN` is mandatory, and deriving it means the common case needs no thought — while still letting
 * someone write a display name the components could never produce.
 */
private fun VCardFormState.withField(field: VCardFormField, value: String): VCardFormState =
    when (field) {
        VCardFormField.DISPLAY_NAME -> copy(displayName = value, displayNameEdited = true)
        VCardFormField.GIVEN_NAME -> copy(givenName = value).withDerivedDisplayName()
        VCardFormField.FAMILY_NAME -> copy(familyName = value).withDerivedDisplayName()
        VCardFormField.ORGANIZATION -> copy(organization = value)
        VCardFormField.TITLE -> copy(title = value)
        VCardFormField.PHONE -> copy(phone = value)
        VCardFormField.EMAIL -> copy(email = value)
    }

private fun VCardFormState.withDerivedDisplayName(): VCardFormState =
    if (displayNameEdited) this else copy(displayName = "$givenName $familyName".trim())

/** See `QrCreateViewModel` for why the first frame is built before construction. */
private fun initialState(
    savedStateHandle: SavedStateHandle,
    parseVCardPayload: ParseVCardPayloadUseCase,
): VCardCreateState {
    val payload = savedStateHandle.get<String>(VCardCreateRoute.ARG_PAYLOAD)
        ?: return VCardCreateState()

    val card = parseVCardPayload(payload)
        ?: return VCardCreateState(editing = true, prefillFailed = true)

    return VCardCreateState(form = card.toFormState(), editing = true)
}
