package com.minion.scaffold.feature.qrscan.domain.export

import com.minion.scaffold.core.emv.model.QrInquiryReport
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

/** What came of building a JSON export. */
internal sealed interface PaymentJsonExport {

    /**
     * The finished document.
     *
     * @property json   Pretty-printed, ready to share.
     * @property source Which schema produced it, so the caller can say which contract it is.
     */
    data class Ready(val json: String, val source: PaymentSchemaSource) : PaymentJsonExport

    /**
     * The active template names something this app cannot resolve.
     *
     * @property token The offending placeholder.
     */
    data class UnknownPlaceholder(val token: String) : PaymentJsonExport

    /**
     * The active template was stored under an older format and is no longer trusted.
     *
     * Refused rather than rendered on a guess, and refused rather than quietly falling back to the
     * built-in — an export that silently produces a *different contract* from the one configured
     * looks like it worked, which is worse than failing.
     */
    data object Outdated : PaymentJsonExport

    /** The template will not parse. Unreachable for anything that went through validation. */
    data object Unusable : PaymentJsonExport
}

/**
 * A scanned payment code, rendered through the active schema template.
 *
 * The contract is data now, not code: what the document is called, how it nests and which value
 * feeds what all come from a template that can be replaced on the device. What stays here is the
 * pipeline — load, resolve, render — and what stays in [ResolvePlaceholdersUseCase] is the
 * arithmetic and the EMV semantics, which a template has no business deciding.
 */
internal class ExportPaymentJsonUseCase @Inject constructor(
    private val schemaRepository: PaymentSchemaRepository,
    private val resolvePlaceholders: ResolvePlaceholdersUseCase,
    private val renderSchema: RenderSchemaUseCase,
) {

    /**
     * Renders [report] through the active schema.
     *
     * Suspending, unlike the version this replaces: the template now comes from DataStore and, the
     * first time, from an asset. Neither is work the main thread should be doing.
     *
     * @param report An already-decoded payment code.
     * @return The document, or why there is not one.
     */
    suspend operator fun invoke(report: QrInquiryReport): PaymentJsonExport {
        val schema = schemaRepository.activeSchema.first()
        if (schema.outdated) return PaymentJsonExport.Outdated

        val template = parse(schema.text) ?: return PaymentJsonExport.Unusable
        val values = resolvePlaceholders(report) ?: return PaymentJsonExport.Unusable

        return when (val rendered = renderSchema(template, values)) {
            is SchemaRenderResult.Rendered -> PaymentJsonExport.Ready(
                json = json.encodeToString(JsonElement.serializer(), rendered.document),
                source = schema.source,
            )

            is SchemaRenderResult.UnknownPlaceholder ->
                PaymentJsonExport.UnknownPlaceholder(rendered.token)
        }
    }

    private fun parse(text: String): JsonElement? = try {
        Json.parseToJsonElement(text.trim())
    } catch (_: Exception) {
        null
    }

    private companion object {

        /**
         * Indented, because the document is read or pasted into an editor long before anything
         * consumes it. Objects are insertion-ordered, so the keys come out in the template's own
         * order rather than sorted.
         */
        val json = Json { prettyPrint = true }
    }
}
