package com.minion.scaffold.feature.qrscan.domain.export

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

/** What came of rendering a template against a scanned code. */
internal sealed interface SchemaRenderResult {

    /**
     * The finished document.
     *
     * @property document The rendered JSON.
     */
    data class Rendered(val document: JsonElement) : SchemaRenderResult

    /**
     * The template names something this app cannot resolve.
     *
     * Reachable only for a template stored before an app update removed a name — an import would
     * have rejected it. Reported rather than nulled, because a null in a payments document is
     * indistinguishable from a field the scanned code genuinely lacked.
     *
     * @property token The offending placeholder, as written in the template.
     */
    data class UnknownPlaceholder(val token: String) : SchemaRenderResult
}

/**
 * Substitutes a scanned code's values into a schema template.
 *
 * Substitution and nothing else — no conditionals, no loops. A template is a JSON document that
 * stays a JSON document, which is what makes it something a person can write in an editor and a
 * reviewer can read in a diff.
 *
 * Two rules, and the difference is only whether a placeholder has the string to itself:
 *
 * - `"{{amount}}"` is replaced by the value **with its own type** — a string, `null`, a boolean or
 *   an array. This is what lets an absent amount be `null` rather than `""`, and what lets
 *   `currency_allowed` be a real array.
 * - `"REF-{{merchant_id}}"` interpolates the value's text, with `null` reading as empty.
 *
 * Object keys are never substituted, and anything that is not a string is copied through — so the
 * constants a contract carries stay literal text in the template.
 */
internal class RenderSchemaUseCase @Inject constructor() {

    /**
     * Renders [template] using [values].
     *
     * @param template The parsed schema template.
     * @param values   What the scanned code is worth.
     * @return The document, or the first unrecognised placeholder found.
     */
    operator fun invoke(
        template: JsonElement,
        values: PlaceholderValues,
    ): SchemaRenderResult {
        val unknown = mutableListOf<String>()
        val document = render(template, values, unknown)

        return unknown.firstOrNull()
            ?.let(SchemaRenderResult::UnknownPlaceholder)
            ?: SchemaRenderResult.Rendered(document)
    }

    private fun render(
        element: JsonElement,
        values: PlaceholderValues,
        unknown: MutableList<String>,
    ): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.mapValues { (_, value) -> render(value, values, unknown) },
        )

        is JsonArray -> JsonArray(element.map { render(it, values, unknown) })

        // JsonNull is a JsonPrimitive too, so a null in a template copies straight through.
        is JsonPrimitive -> if (element.isString) {
            renderString(element.content, values, unknown)
        } else {
            element
        }
    }

    private fun renderString(
        text: String,
        values: PlaceholderValues,
        unknown: MutableList<String>,
    ): JsonElement {
        val sole = PlaceholderSyntax.soleToken(text)
        if (sole != null) {
            return resolve(sole, values, unknown) ?: JsonPrimitive(text)
        }

        if (PlaceholderSyntax.tokensIn(text).isEmpty()) return JsonPrimitive(text)

        val interpolated = PlaceholderSyntax.interpolate(text) { token ->
            val value = resolve(token, values, unknown)
            when {
                value == null || value is JsonNull -> ""
                value is JsonPrimitive -> value.content
                // An array or object landing inside a longer string has no sensible text form, so
                // it goes in as the JSON it is rather than as a Kotlin toString.
                else -> value.toString()
            }
        }

        return JsonPrimitive(interpolated)
    }

    /** The value for [token], recording it when the vocabulary does not know the name. */
    private fun resolve(
        token: String,
        values: PlaceholderValues,
        unknown: MutableList<String>,
    ): JsonElement? {
        val placeholder = PlaceholderSyntax.parse(token)
        if (placeholder == null) {
            unknown += token
            return null
        }

        val value = values.resolve(placeholder)
        if (value == null) unknown += token

        return value
    }
}
