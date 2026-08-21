package com.minion.scaffold.feature.qrscan.presentation.settings

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.usecase.ParseEmvPayloadUseCase
import com.minion.scaffold.core.navigation.QrScanSettingsRoute
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.feature.qrscan.data.SchemaDocument
import com.minion.scaffold.feature.qrscan.data.SchemaDocumentReader
import com.minion.scaffold.feature.qrscan.domain.export.PaymentSchemaRepository
import com.minion.scaffold.feature.qrscan.domain.export.PlaceholderVocabulary
import com.minion.scaffold.feature.qrscan.domain.export.ResolvePlaceholdersUseCase
import com.minion.scaffold.feature.qrscan.domain.export.SchemaValidation
import com.minion.scaffold.feature.qrscan.domain.export.ValidateSchemaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@HiltViewModel
internal class QrScanSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val schemaRepository: PaymentSchemaRepository,
    private val validateSchema: ValidateSchemaUseCase,
    private val documentReader: SchemaDocumentReader,
    private val resolvePlaceholders: ResolvePlaceholdersUseCase,
    private val parseEmvPayload: ParseEmvPayloadUseCase,
) : MviViewModel<QrScanSettingsState, QrScanSettingsIntent, QrScanSettingsEffect>(
    QrScanSettingsState(),
) {

    /**
     * The code this screen was opened from, if any.
     *
     * Read by name rather than through `toRoute()`, which builds an `android.os.Bundle` that does
     * not exist in a JVM unit test — the reason every ViewModel in this repo reads arguments this
     * way.
     */
    private val payload = savedStateHandle
        .get<String>(QrScanSettingsRoute.ARG_PAYLOAD)
        ?.takeIf { it.isNotBlank() }

    init {
        reduce { copy(placeholders = reference()) }

        schemaRepository.activeSchema
            .onEach { schema ->
                reduce {
                    copy(
                        source = schema.source,
                        label = schema.label,
                        outdated = schema.outdated,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: QrScanSettingsIntent) {
        when (intent) {
            is QrScanSettingsIntent.SchemaPicked -> importSchema(intent.uri)
            QrScanSettingsIntent.ExportRequested -> exportSchema()
            QrScanSettingsIntent.ResetRequested -> resetSchema()
            QrScanSettingsIntent.ErrorDismissed -> reduce { copy(importError = null) }

            is QrScanSettingsIntent.CopyTokenRequested -> viewModelScope.launch {
                emitEffect(QrScanSettingsEffect.CopyToken(intent.token))
            }
        }
    }

    /**
     * Reads, checks and stores a picked template.
     *
     * Nothing is written unless the whole document passes: a half-stored schema would fail at the
     * moment somebody needed a document, which is exactly the wrong moment to find out.
     */
    private fun importSchema(uri: Uri) {
        viewModelScope.launch {
            val document = documentReader.read(uri)
            if (document !is SchemaDocument.Read) {
                reduce { copy(importError = SchemaImportError.Unreadable) }
                return@launch
            }

            when (val validation = validateSchema(document.text)) {
                is SchemaValidation.Valid -> {
                    schemaRepository.store(text = document.text, label = document.label)
                    reduce { copy(importError = null) }
                    emitEffect(QrScanSettingsEffect.SchemaImported)
                }

                SchemaValidation.NotJson ->
                    reduce { copy(importError = SchemaImportError.NotJson) }

                is SchemaValidation.UnknownPlaceholders -> reduce {
                    copy(importError = SchemaImportError.UnknownPlaceholders(validation.tokens))
                }
            }
        }
    }

    private fun exportSchema() {
        viewModelScope.launch {
            val schema = schemaRepository.activeSchema.first()
            emitEffect(QrScanSettingsEffect.ShareSchema(schema.text))
        }
    }

    private fun resetSchema() {
        viewModelScope.launch {
            schemaRepository.reset()
            // The error described an import that was refused; going back to the built-in settles
            // the question it was asking, so leaving it on screen would be describing the past.
            reduce { copy(importError = null) }
            emitEffect(QrScanSettingsEffect.SchemaReset)
        }
    }

    /**
     * The placeholder reference.
     *
     * With a payload it carries what each name is worth for *that* code, which is the only way to
     * discover whether a given payload has anything at `tag:62.05`. Without one it is the plain
     * list, because the screen is also reachable before anything has been scanned.
     */
    private fun reference(): List<PlaceholderRow> {
        val values = payload
            ?.let { parseEmvPayload(it) as? EmvParseResult.Success }
            ?.value
            ?.let(resolvePlaceholders::invoke)
            ?: return PlaceholderVocabulary.names.map { PlaceholderRow(name = it) }

        val named = values.namedValues()

        return PlaceholderVocabulary.names.map { name ->
            PlaceholderRow(
                name = name,
                value = when (val value = named[name]) {
                    null, JsonNull -> ""
                    is JsonPrimitive -> value.content
                    else -> value.toString()
                },
            )
        }
    }

}
