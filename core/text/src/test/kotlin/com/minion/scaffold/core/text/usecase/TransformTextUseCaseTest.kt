package com.minion.scaffold.core.text.usecase

import com.minion.scaffold.core.text.model.TextError
import com.minion.scaffold.core.text.model.TextOperation
import com.minion.scaffold.core.text.model.TextResult
import org.junit.Assert.assertEquals
import org.junit.Test

internal class TransformTextUseCaseTest {

    private val transform = TransformTextUseCase()

    // Known vectors — so the encoder and decoder cannot be consistently wrong together.

    @Test
    fun `base64 matches the published vector`() {
        assertEquals("TWFu", output(TextOperation.BASE64_ENCODE, "Man"))
    }

    @Test
    fun `sha-256 matches the published vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            output(TextOperation.SHA256, "abc"),
        )
    }

    @Test
    fun `md5 matches the published vector`() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", output(TextOperation.MD5, "abc"))
    }

    /** The footgun: a space is `%20`, not `+`, and the ampersand is escaped. */
    @Test
    fun `url encoding uses percent-twenty for a space`() {
        assertEquals("a%20b%26c", output(TextOperation.URL_ENCODE, "a b&c"))
    }

    @Test
    fun `hex encoding is lowercase over utf-8 bytes`() {
        assertEquals("4142", output(TextOperation.HEX_ENCODE, "AB"))
    }

    @Test
    fun `html encoding escapes the five characters`() {
        assertEquals(
            "&lt;a href=&quot;x&quot;&gt;A&amp;B&#39;s&lt;/a&gt;",
            output(TextOperation.HTML_ENCODE, "<a href=\"x\">A&B's</a>"),
        )
    }

    // Round trips.

    @Test
    fun `base64 round trips including non-ascii`() {
        assertRoundTrips(TextOperation.BASE64_ENCODE, TextOperation.BASE64_DECODE)
    }

    @Test
    fun `hex round trips including non-ascii`() {
        assertRoundTrips(TextOperation.HEX_ENCODE, TextOperation.HEX_DECODE)
    }

    @Test
    fun `url round trips including non-ascii`() {
        assertRoundTrips(TextOperation.URL_ENCODE, TextOperation.URL_DECODE)
    }

    @Test
    fun `html round trips`() {
        for (sample in listOf("A & B", "<tag>", "\"quoted\"", "it's")) {
            val encoded = output(TextOperation.HTML_ENCODE, sample)
            assertEquals(sample, output(TextOperation.HTML_DECODE, encoded))
        }
    }

    // Liberal decoding.

    @Test
    fun `base64 decodes url-safe unpadded input`() {
        // The standard "sure." is "c3VyZS4="; url-safe and unpadded it is "c3VyZS4".
        assertEquals("sure.", output(TextOperation.BASE64_DECODE, "c3VyZS4"))
    }

    @Test
    fun `url decoding accepts both a plus and percent-twenty as a space`() {
        assertEquals("a b", output(TextOperation.URL_DECODE, "a+b"))
        assertEquals("a b", output(TextOperation.URL_DECODE, "a%20b"))
    }

    @Test
    fun `html decoding resolves named and numeric references and leaves unknowns alone`() {
        assertEquals("< & '", output(TextOperation.HTML_DECODE, "&lt; &amp; &#39;"))
        assertEquals("A", output(TextOperation.HTML_DECODE, "&#65;"))
        assertEquals("A", output(TextOperation.HTML_DECODE, "&#x41;"))
        assertEquals("&unknown;", output(TextOperation.HTML_DECODE, "&unknown;"))
    }

    // JSON keeps its key order.

    @Test
    fun `json minify of prettified input preserves key order`() {
        val original = """{"b":1,"a":2,"c":{"z":9,"y":8}}"""

        val pretty = output(TextOperation.JSON_PRETTIFY, original)
        val back = output(TextOperation.JSON_MINIFY, pretty)

        assertEquals(original, back)
    }

    @Test
    fun `json prettify indents`() {
        val pretty = output(TextOperation.JSON_PRETTIFY, """{"a":1}""")

        assertEquals("{\n    \"a\": 1\n}", pretty)
    }

    // Case conversion — one input, three targets, including a camelCase and an acronym.

    @Test
    fun `case conversion tokenises separators and camel boundaries alike`() {
        for (input in listOf("get user id", "get_user_id", "get-user-id", "getUserId")) {
            assertEquals("getUserId", output(TextOperation.CAMEL_CASE, input))
            assertEquals("get_user_id", output(TextOperation.SNAKE_CASE, input))
            assertEquals("get-user-id", output(TextOperation.KEBAB_CASE, input))
        }
    }

    @Test
    fun `case conversion splits an acronym from the word after it`() {
        assertEquals("get_id_card", output(TextOperation.SNAKE_CASE, "getIDCard"))
    }

    // Failures.

    @Test
    fun `invalid base64 is a typed failure`() {
        assertEquals(TextError.NOT_VALID_BASE64, failure(TextOperation.BASE64_DECODE, "not base64!!"))
    }

    @Test
    fun `odd-length hex is a typed failure`() {
        assertEquals(TextError.NOT_VALID_HEX, failure(TextOperation.HEX_DECODE, "abc"))
    }

    @Test
    fun `non-hex characters are a typed failure`() {
        assertEquals(TextError.NOT_VALID_HEX, failure(TextOperation.HEX_DECODE, "zz"))
    }

    @Test
    fun `malformed json is a typed failure`() {
        assertEquals(TextError.NOT_VALID_JSON, failure(TextOperation.JSON_PRETTIFY, "{not json"))
    }

    @Test
    fun `a malformed percent escape is a typed failure`() {
        assertEquals(
            TextError.NOT_VALID_URL_ENCODING,
            failure(TextOperation.URL_DECODE, "a%2"),
        )
    }

    private fun assertRoundTrips(encode: TextOperation, decode: TextOperation) {
        for (sample in listOf("hello", "a b & c", "Ünïcödé — café", "", "1234")) {
            val encoded = output(encode, sample)
            assertEquals(sample, output(decode, encoded))
        }
    }

    private fun output(operation: TextOperation, input: String): String =
        when (val result = transform(operation, input)) {
            is TextResult.Success -> result.output
            is TextResult.Failure -> throw AssertionError("expected Success but was ${result.reason}")
        }

    private fun failure(operation: TextOperation, input: String): TextError =
        when (val result = transform(operation, input)) {
            is TextResult.Success -> throw AssertionError("expected Failure but was ${result.output}")
            is TextResult.Failure -> result.reason
        }
}
