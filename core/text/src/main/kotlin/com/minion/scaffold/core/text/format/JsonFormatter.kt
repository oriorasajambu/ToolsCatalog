package com.minion.scaffold.core.text.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Reformats JSON without changing what it means.
 *
 * Goes through `JsonElement`, whose object type is insertion-ordered, so keys come out in the order
 * they went in rather than reshuffled — a formatter that sorts keys has quietly changed the document
 * for anyone diffing it. A string that will not parse is a failure, reported, never a thrown
 * exception reaching the UI.
 */
internal object JsonFormatter {

    private val pretty = Json { prettyPrint = true }
    private val compact = Json

    /**
     * [input] reformatted with indentation, or null when it is not valid JSON.
     *
     * @param input The JSON text to prettify.
     * @return The indented JSON, or `null` when [input] does not parse.
     */
    fun prettify(input: String): String? = reformat(input, pretty)

    /**
     * [input] reformatted onto a single compact line, or null when it is not valid JSON.
     *
     * @param input The JSON text to minify.
     * @return The compact JSON, or `null` when [input] does not parse.
     */
    fun minify(input: String): String? = reformat(input, compact)

    private fun reformat(input: String, json: Json): String? = try {
        val element = Json.parseToJsonElement(input.trim())
        json.encodeToString(JsonElement.serializer(), element)
    } catch (_: Exception) {
        // kotlinx throws SerializationException (and IllegalArgument* subtypes) on malformed input;
        // to a user they are all "this is not JSON".
        null
    }
}
