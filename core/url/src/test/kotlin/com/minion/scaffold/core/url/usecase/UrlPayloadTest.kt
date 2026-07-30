package com.minion.scaffold.core.url.usecase

import com.minion.scaffold.core.url.model.UrlBuildResult
import com.minion.scaffold.core.url.model.UrlViolationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class UrlPayloadTest {

    private val build = BuildUrlPayloadUseCase()
    private val parse = ParseUrlPayloadUseCase()

    @Test
    fun `a full URL is written unchanged`() {
        assertEquals(
            "https://example.com/a?b=c#d",
            build("https://example.com/a?b=c#d").payloadOrFail(),
        )
    }

    /** Typing a bare host into a field labelled *Link* has said what it means. */
    @Test
    fun `a missing scheme becomes https`() {
        assertEquals("https://example.com", build("example.com").payloadOrFail())
    }

    /**
     * A dot is excluded from the scheme pattern precisely so this reads as a host and a port rather
     * than as a scheme called `example.com`.
     */
    @Test
    fun `a host with a port and no scheme becomes https`() {
        assertEquals("https://example.com:8080/x", build("example.com:8080/x").payloadOrFail())
    }

    @Test
    fun `http is left alone rather than upgraded`() {
        assertEquals("http://example.com", build("http://example.com").payloadOrFail())
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("https://example.com", build("  https://example.com  ").payloadOrFail())
    }

    /** A QR carries UTF-8, so a unicode path is a link this tool can write and read. */
    @Test
    fun `a non-ASCII path survives`() {
        val payload = build("https://example.com/café").payloadOrFail()

        assertEquals("https://example.com/café", payload)
        assertEquals(payload, parse(payload))
    }

    @Test
    fun `every written payload reads back identically`() {
        for (input in listOf("example.com", "http://example.com/x", "https://a.b.c/d?e=f")) {
            val payload = build(input).payloadOrFail()

            assertEquals(payload, parse(payload))
        }
    }

    // Violations.

    @Test
    fun `a blank link is required`() {
        assertEquals(UrlViolationReason.REQUIRED, build("   ").reasonOrFail())
    }

    @Test
    fun `a scheme other than http is refused`() {
        assertEquals(UrlViolationReason.UNSUPPORTED_SCHEME, build("mailto:a@b.com").reasonOrFail())
        assertEquals(UrlViolationReason.UNSUPPORTED_SCHEME, build("tel:+62811").reasonOrFail())
    }

    /** A space ends the URL for most readers, so the code would open something shorter. */
    @Test
    fun `an internal space is malformed`() {
        assertEquals(UrlViolationReason.MALFORMED, build("https://example.com/a b").reasonOrFail())
    }

    @Test
    fun `a scheme with no host is malformed`() {
        assertEquals(UrlViolationReason.MALFORMED, build("https://").reasonOrFail())
    }

    // Reading is stricter than writing: no scheme means not a link.

    @Test
    fun `a bare host is not a scanned link`() {
        assertNull(parse("example.com"))
    }

    @Test
    fun `a scheme in upper case is still a scanned link`() {
        assertEquals("HTTPS://EXAMPLE.COM", parse("HTTPS://EXAMPLE.COM"))
    }

    @Test
    fun `other formats are not links`() {
        assertNull(parse("WIFI:T:WPA;S:Guest;P:hunter2!;;"))
        assertNull(parse("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jane\r\nEND:VCARD"))
        assertNull(parse("0002010102125204573253033605802ID63043D58"))
        assertNull(parse("just some text"))
    }

    @Test
    fun `a scheme with nothing after it is not a scanned link`() {
        assertNull(parse("https://"))
    }

    private fun UrlBuildResult.payloadOrFail(): String = when (this) {
        is UrlBuildResult.Success -> payload
        is UrlBuildResult.Invalid -> throw AssertionError("expected Success but was $reason")
    }

    private fun UrlBuildResult.reasonOrFail(): UrlViolationReason = when (this) {
        is UrlBuildResult.Success -> throw AssertionError("expected Invalid but built $payload")
        is UrlBuildResult.Invalid -> reason
    }
}
