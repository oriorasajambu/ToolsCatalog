package com.minion.scaffold.feature.qrscan.domain.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

/** What came of checking a template offered for import. */
internal sealed interface SchemaValidation {

    /**
     * The template is usable.
     *
     * @property template The parsed document, so a caller need not parse it a second time.
     */
    data class Valid(val template: JsonElement) : SchemaValidation

    /** The text does not parse as JSON at all. */
    data object NotJson : SchemaValidation

    /**
     * The template names things this app cannot resolve.
     *
     * All of them, not the first: fixing a typo only to be told about the next one is a bad way to
     * spend an afternoon with a 1.5 KB document.
     *
     * @property tokens The offending placeholders, in the order they appear.
     */
    data class UnknownPlaceholders(val tokens: List<String>) : SchemaValidation
}

/**
 * Checks a template before it is allowed to become the active schema.
 *
 * Catching a bad name here rather than at export is what keeps a broken schema from being
 * discovered at the moment somebody actually needs the document. The one failure this cannot
 * prevent is a name that was valid at import and removed by a later app update, which is why
 * rendering checks again.
 */
internal class ValidateSchemaUseCase @Inject constructor() {

    /**
     * Validates [text] as a schema template.
     *
     * @param text The imported document.
     * @return Whether it can be stored, and why not when it cannot.
     */
    operator fun invoke(text: String): SchemaValidation {
        val template = try {
            Json.parseToJsonElement(text.trim())
        } catch (_: Exception) {
            // kotlinx throws SerializationException and IllegalArgument* subtypes on malformed
            // input; to somebody holding a file they are all "this is not JSON".
            return SchemaValidation.NotJson
        }

        val unknown = buildList { collectUnknown(template, this) }

        return if (unknown.isEmpty()) {
            SchemaValidation.Valid(template)
        } else {
            SchemaValidation.UnknownPlaceholders(unknown.distinct())
        }
    }

    private fun collectUnknown(element: JsonElement, into: MutableList<String>) {
        when (element) {
            // Keys are never substituted, so only values are walked.
            is JsonObject -> element.values.forEach { collectUnknown(it, into) }

            is JsonArray -> element.forEach { collectUnknown(it, into) }

            is JsonPrimitive -> if (element.isString) collectUnknownIn(element.content, into)
        }
    }

    /**
     * Collects the unrecognised placeholder tokens in one string value.
     *
     * A token is unknown when it does not parse as a placeholder at all, or parses into one the
     * vocabulary does not define — both are things the user typed that will not be substituted, and
     * both have to be named rather than silently left in the output.
     */
    private fun collectUnknownIn(text: String, into: MutableList<String>) {
        for (token in PlaceholderSyntax.tokensIn(text)) {
            val placeholder = PlaceholderSyntax.parse(token)
            if (placeholder == null || !PlaceholderVocabulary.recognises(placeholder)) {
                into += token
            }
        }
    }
}
