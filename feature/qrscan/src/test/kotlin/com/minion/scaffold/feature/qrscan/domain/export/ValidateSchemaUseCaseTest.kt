package com.minion.scaffold.feature.qrscan.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a template has to survive before it is allowed to become the active schema.
 *
 * Catching a bad name here is what stops it being discovered at the moment somebody actually needs
 * the document.
 */
internal class ValidateSchemaUseCaseTest {

    private val validate = ValidateSchemaUseCase()

    @Test
    fun `the shipped default passes`() {
        // The one that must never regress: the template the app itself carries has to satisfy the
        // rules the app imposes on everybody else's.
        assertTrue(validate(FakePaymentSchemaRepository.builtInAsset) is SchemaValidation.Valid)
    }

    @Test
    fun `text that is not JSON is refused`() {
        assertEquals(SchemaValidation.NotJson, validate("merchant_pan = 1"))
    }

    @Test
    fun `a truncated document is refused`() {
        assertEquals(SchemaValidation.NotJson, validate("""{ "a": "b" """))
    }

    @Test
    fun `every unknown name is reported at once`() {
        // All of them, not the first: fixing a typo only to be told about the next one is a bad way
        // to spend an afternoon with a 1.5 KB document.
        val result = validate(
            """{ "a": "{{merchant_nmae}}", "b": { "c": "{{amont}}" } }""",
        )

        assertEquals(
            SchemaValidation.UnknownPlaceholders(listOf("merchant_nmae", "amont")),
            result,
        )
    }

    @Test
    fun `a name repeated twice is reported once`() {
        val result = validate("""{ "a": "{{nope}}", "b": "{{nope}}" }""")

        assertEquals(SchemaValidation.UnknownPlaceholders(listOf("nope")), result)
    }

    @Test
    fun `a malformed tag path is refused`() {
        val result = validate("""{ "a": "{{tag:260.1}}" }""")

        assertEquals(SchemaValidation.UnknownPlaceholders(listOf("tag:260.1")), result)
    }

    @Test
    fun `a well-formed tag path is accepted even for a tag nothing has scanned`() {
        // Whether a code carries tag 62 is the code's business. A template may address it freely.
        assertTrue(validate("""{ "a": "{{tag:62.05}}" }""") is SchemaValidation.Valid)
    }

    @Test
    fun `a key that looks like a placeholder is not one`() {
        // Keys are never substituted, so they are never validated either.
        assertTrue(validate("""{ "{{nope}}": "x" }""") is SchemaValidation.Valid)
    }

    @Test
    fun `a document with no placeholders at all is valid`() {
        assertTrue(validate("""{ "constant": "CASA", "flag": true }""") is SchemaValidation.Valid)
    }
}
