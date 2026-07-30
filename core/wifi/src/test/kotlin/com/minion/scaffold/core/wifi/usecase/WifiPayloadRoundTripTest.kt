package com.minion.scaffold.core.wifi.usecase

import com.minion.scaffold.core.wifi.model.WifiBuildResult
import com.minion.scaffold.core.wifi.model.WifiCredentials
import com.minion.scaffold.core.wifi.model.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `parse(build(c)) == c`, and the exact bytes for the cases a phone has to agree with.
 *
 * The round trip alone would pass against a writer and reader that were consistently wrong
 * together, so the payload strings are asserted literally where the format is prescriptive —
 * the escaping and quoting rules in particular, which is where an implementation that only
 * checks itself would happily produce codes nothing else can read.
 */
internal class WifiPayloadRoundTripTest {

    private val build = BuildWifiPayloadUseCase()
    private val parse = ParseWifiPayloadUseCase()

    @Test
    fun `a WPA network writes the canonical form`() {
        val credentials = WifiCredentials("Guest", WifiSecurity.WPA, "hunter2!")

        assertEquals("WIFI:T:WPA;S:Guest;P:hunter2!;;", build(credentials).payloadOrFail())
    }

    @Test
    fun `an open network writes nopass and no password field`() {
        val credentials = WifiCredentials("Cafe Free", WifiSecurity.OPEN)

        assertEquals("WIFI:T:nopass;S:Cafe Free;;", build(credentials).payloadOrFail())
    }

    @Test
    fun `a hidden network writes the hidden flag`() {
        val credentials = WifiCredentials("Backroom", WifiSecurity.WPA, "hunter2!", hidden = true)

        assertEquals(
            "WIFI:T:WPA;S:Backroom;P:hunter2!;H:true;;",
            build(credentials).payloadOrFail(),
        )
    }

    /** `H:false` is legal and pointless; leaving it out keeps the code less dense. */
    @Test
    fun `a visible network omits the hidden flag`() {
        val payload = build(WifiCredentials("Guest", WifiSecurity.WPA, "hunter2!")).payloadOrFail()

        assertTrue("H:" !in payload)
    }

    @Test
    fun `every security type round trips`() {
        val cases = listOf(
            WifiCredentials("Net", WifiSecurity.WPA, "hunter2!"),
            WifiCredentials("Net", WifiSecurity.WEP, "abcde"),
            WifiCredentials("Net", WifiSecurity.OPEN),
            WifiCredentials("Net", WifiSecurity.WPA, "hunter2!", hidden = true),
        )

        for (credentials in cases) {
            assertEquals(credentials, parse(build(credentials).payloadOrFail()))
        }
    }

    /**
     * The case that silently produces an unreadable code: an unescaped semicolon ends the field,
     * and the network name is truncated at it.
     */
    @Test
    fun `an SSID containing a semicolon is escaped and survives`() {
        val credentials = WifiCredentials("Joe's; Guest", WifiSecurity.WPA, "hunter2!")

        val payload = build(credentials).payloadOrFail()

        assertEquals("WIFI:T:WPA;S:Joe's\\; Guest;P:hunter2!;;", payload)
        assertEquals(credentials, parse(payload))
    }

    @Test
    fun `every special character survives both directions`() {
        for (character in listOf('\\', ';', ',', ':', '"')) {
            val credentials = WifiCredentials(
                ssid = "a${character}b",
                security = WifiSecurity.WPA,
                // Long enough to clear the eight-character WPA minimum, so this stays a test of
                // escaping rather than of validation.
                password = "pass${character}word!",
            )

            assertEquals(credentials, parse(build(credentials).payloadOrFail()))
        }
    }

    /**
     * A bare `S:ABCDEF` is six hexadecimal digits to a reader entitled to treat it that way, so a
     * literal SSID that spells itself in `0-9A-F` has to be quoted.
     */
    @Test
    fun `a hexadecimal SSID is quoted`() {
        val credentials = WifiCredentials("ABCDEF", WifiSecurity.WPA, "hunter2!")

        val payload = build(credentials).payloadOrFail()

        assertEquals("WIFI:T:WPA;S:\"ABCDEF\";P:hunter2!;;", payload)
        assertEquals(credentials, parse(payload))
    }

    @Test
    fun `a hexadecimal password is quoted`() {
        val credentials = WifiCredentials("Guest", WifiSecurity.WPA, "12345678")

        val payload = build(credentials).payloadOrFail()

        assertEquals("WIFI:T:WPA;S:Guest;P:\"12345678\";;", payload)
        assertEquals(credentials, parse(payload))
    }

    /** An SSID that merely starts with a quote is content, not structure. */
    @Test
    fun `a quoted-looking SSID is not mistaken for structural quoting`() {
        val credentials = WifiCredentials("\"quoted\"", WifiSecurity.OPEN)

        assertEquals(credentials, parse(build(credentials).payloadOrFail()))
    }

    // Reading is liberal where writing is strict — real generators vary on all of these.

    @Test
    fun `fields in a different order parse`() {
        assertEquals(
            WifiCredentials("Guest", WifiSecurity.WPA, "hunter2!"),
            parse("WIFI:S:Guest;P:hunter2!;T:WPA;;"),
        )
    }

    @Test
    fun `a missing terminator parses`() {
        assertEquals(
            WifiCredentials("Guest", WifiSecurity.WPA, "hunter2!"),
            parse("WIFI:T:WPA;S:Guest;P:hunter2!"),
        )
    }

    @Test
    fun `security aliases map onto the type that joins the same network`() {
        for (alias in listOf("WPA2", "WPA2-PSK", "WPA3", "SAE", "psk")) {
            assertEquals(
                WifiSecurity.WPA,
                parse("WIFI:T:$alias;S:Guest;P:hunter2!;;")?.security,
            )
        }
    }

    @Test
    fun `an absent security type reads as an open network`() {
        assertEquals(WifiSecurity.OPEN, parse("WIFI:S:Guest;;")?.security)
    }

    // Not a Wi-Fi payload is not an error.

    @Test
    fun `a payment payload is not a Wi-Fi payload`() {
        assertNull(parse("0002010102125204573253033605802ID6304ABCD"))
    }

    @Test
    fun `a URL is not a Wi-Fi payload`() {
        assertNull(parse("https://example.com"))
    }

    /** Enterprise networks need fields this model has nowhere to put, so it declines to guess. */
    @Test
    fun `a security type this tool cannot represent does not parse`() {
        assertNull(parse("WIFI:T:WPA2-EAP;S:Corp;P:hunter2!;;"))
    }

    @Test
    fun `a payload with no SSID does not parse`() {
        assertNull(parse("WIFI:T:WPA;P:hunter2!;;"))
    }

    private fun WifiBuildResult.payloadOrFail(): String = when (this) {
        is WifiBuildResult.Success -> payload
        is WifiBuildResult.Invalid -> throw AssertionError("expected Success but was $violations")
    }
}
