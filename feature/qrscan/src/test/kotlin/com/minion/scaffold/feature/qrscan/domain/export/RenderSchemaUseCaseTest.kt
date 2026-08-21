package com.minion.scaffold.feature.qrscan.domain.export

import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.usecase.EmvDraftFromPayloadUseCase
import com.minion.scaffold.core.emv.usecase.ParseEmvPayloadUseCase
import com.minion.scaffold.feature.qrscan.presentation.ScanSamples
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The substitution rules, against a real code so that the values are real ones.
 *
 * The single rule worth internalising: a string that is *entirely* one placeholder takes the
 * value's own type, and a placeholder inside a longer string interpolates as text. Everything else
 * here follows from that.
 */
internal class RenderSchemaUseCaseTest {

    private val parse = ParseEmvPayloadUseCase()
    private val resolve = ResolvePlaceholdersUseCase(EmvDraftFromPayloadUseCase(parse))
    private val render = RenderSchemaUseCase()

    @Test
    fun `a lone placeholder keeps the value's own type`() {
        val result = renderOf("""{ "name": "{{merchant_name}}" }""")

        assertEquals("PAK BOS QR 1", result.string("name"))
    }

    @Test
    fun `a lone placeholder with nothing behind it is null, not an empty string`() {
        // The static-code case the contract depends on: an absent amount has to be null.
        val result = renderOf("""{ "amount": "{{tips}}" }""")

        assertTrue(result.getValue("amount") is JsonNull)
    }

    @Test
    fun `a lone placeholder can produce a boolean`() {
        val result = renderOf("""{ "ok": "{{crc_valid}}" }""")

        assertEquals("true", result.getValue("ok").jsonPrimitive.content)
        assertTrue(result.getValue("ok").jsonPrimitive.booleanOrNullCompat() == true)
    }

    @Test
    fun `a lone placeholder can produce an array`() {
        val result = renderOf("""{ "currencies": "{{currency_allowed}}" }""")

        assertEquals(listOf("IDR"), result.getValue("currencies").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `an embedded placeholder interpolates as text`() {
        val result = renderOf("""{ "ref": "REF-{{merchant_id}}-X" }""")

        assertEquals("REF-000008160012605-X", result.string("ref"))
    }

    @Test
    fun `an embedded placeholder with nothing behind it reads as empty`() {
        val result = renderOf("""{ "ref": "REF-{{tips}}-X" }""")

        assertEquals("REF--X", result.string("ref"))
    }

    @Test
    fun `literals are copied through untouched`() {
        val result = renderOf(
            """{ "flag": false, "count": 4, "list": ["CASA", "CC"], "text": "QRMerchant" }""",
        )

        assertEquals("false", result.getValue("flag").jsonPrimitive.content)
        assertEquals("4", result.getValue("count").jsonPrimitive.content)
        assertEquals(listOf("CASA", "CC"), result.getValue("list").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("QRMerchant", result.string("text"))
    }

    @Test
    fun `object keys are never substituted`() {
        // A key that looks like a placeholder stays exactly as written — only values are rewritten,
        // so a contract can carry a field genuinely named that way.
        val result = renderOf("""{ "{{merchant_name}}": "x" }""")

        assertEquals("x", result.string("{{merchant_name}}"))
    }

    @Test
    fun `nesting is walked to the bottom`() {
        val result = renderOf(
            """{ "a": { "b": [ { "c": "{{merchant_city}}" } ] } }""",
        )

        assertEquals(
            "Bekasi",
            result.obj("a").getValue("b").jsonArray.first().jsonObject.string("c"),
        )
    }

    @Test
    fun `a raw tag path resolves against the payload`() {
        val result = renderOf("""{ "name": "{{tag:59}}", "pan": "{{tag:26.01}}" }""")

        assertEquals("PAK BOS QR 1", result.string("name"))
        assertEquals("936000220000000282", result.string("pan"))
    }

    @Test
    fun `a raw tag path that finds nothing is null rather than a failure`() {
        // Tag 62 is absent from a domestic code. That is the code's business, not the template's.
        val result = renderOf("""{ "ref": "{{tag:62.05}}" }""")

        assertTrue(result.getValue("ref") is JsonNull)
    }

    @Test
    fun `an unknown name fails, and says which`() {
        val outcome = render(
            Json.parseToJsonElement("""{ "name": "{{merchant_nmae}}" }"""),
            values(),
        )

        assertTrue(outcome is SchemaRenderResult.UnknownPlaceholder)
        assertEquals(
            "merchant_nmae",
            (outcome as SchemaRenderResult.UnknownPlaceholder).token,
        )
    }

    @Test
    fun `a malformed tag path fails rather than resolving to nothing`() {
        val outcome = render(
            Json.parseToJsonElement("""{ "x": "{{tag:260.1}}" }"""),
            values(),
        )

        assertTrue(outcome is SchemaRenderResult.UnknownPlaceholder)
    }

    private fun renderOf(template: String): JsonObject {
        val outcome = render(Json.parseToJsonElement(template), values())
        return (outcome as SchemaRenderResult.Rendered).document.jsonObject
    }

    private fun values(): PlaceholderValues {
        val report = (parse(ScanSamples.QRIS_DYNAMIC) as EmvParseResult.Success).value
        return resolve(report)!!
    }

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    /** `booleanOrNull` is an extension the test source can reach without extra imports noise. */
    private fun kotlinx.serialization.json.JsonPrimitive.booleanOrNullCompat(): Boolean? =
        content.toBooleanStrictOrNull()
}
