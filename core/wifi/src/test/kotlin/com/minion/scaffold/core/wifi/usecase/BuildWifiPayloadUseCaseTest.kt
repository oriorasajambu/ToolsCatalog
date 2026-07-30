package com.minion.scaffold.core.wifi.usecase

import com.minion.scaffold.core.wifi.model.WifiBuildResult
import com.minion.scaffold.core.wifi.model.WifiCredentials
import com.minion.scaffold.core.wifi.model.WifiField
import com.minion.scaffold.core.wifi.model.WifiSecurity
import com.minion.scaffold.core.wifi.model.WifiViolation
import com.minion.scaffold.core.wifi.model.WifiViolationReason
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BuildWifiPayloadUseCaseTest {

    private val build = BuildWifiPayloadUseCase()

    @Test
    fun `an SSID is required`() {
        assertTrue(
            WifiViolation(WifiField.SSID, WifiViolationReason.REQUIRED)
                in build(WifiCredentials("  ", WifiSecurity.OPEN)).violationsOrFail(),
        )
    }

    /** 32 characters is the 802.11 limit; 33 cannot be broadcast, let alone joined. */
    @Test
    fun `an SSID over thirty-two characters is rejected`() {
        val credentials = WifiCredentials("A".repeat(33), WifiSecurity.OPEN)

        assertTrue(
            WifiViolation(WifiField.SSID, WifiViolationReason.TOO_LONG)
                in build(credentials).violationsOrFail(),
        )
    }

    @Test
    fun `an SSID of exactly thirty-two characters builds`() {
        val credentials = WifiCredentials("A".repeat(32), WifiSecurity.OPEN)

        assertTrue(build(credentials) is WifiBuildResult.Success)
    }

    @Test
    fun `a WPA network requires a passphrase`() {
        assertTrue(
            WifiViolation(WifiField.PASSWORD, WifiViolationReason.REQUIRED)
                in build(WifiCredentials("Guest", WifiSecurity.WPA)).violationsOrFail(),
        )
    }

    @Test
    fun `a WPA passphrase under eight characters is rejected`() {
        val credentials = WifiCredentials("Guest", WifiSecurity.WPA, "short7")

        assertTrue(
            WifiViolation(WifiField.PASSWORD, WifiViolationReason.TOO_SHORT)
                in build(credentials).violationsOrFail(),
        )
    }

    @Test
    fun `a WPA passphrase over sixty-three characters is rejected`() {
        val credentials = WifiCredentials("Guest", WifiSecurity.WPA, "p".repeat(64))

        assertTrue(
            WifiViolation(WifiField.PASSWORD, WifiViolationReason.TOO_LONG)
                in build(credentials).violationsOrFail(),
        )
    }

    @Test
    fun `both WPA passphrase boundaries build`() {
        for (length in listOf(8, 63)) {
            val credentials = WifiCredentials("Guest", WifiSecurity.WPA, "p".repeat(length))

            assertTrue(build(credentials) is WifiBuildResult.Success)
        }
    }

    @Test
    fun `every legal WEP key shape builds`() {
        val keys = listOf(
            "abcde",                      // 5 characters of text
            "abcdefghijklm",              // 13 characters of text
            "0123456789",                 // 10 hexadecimal digits
            "0123456789ABCDEF0123456789", // 26 hexadecimal digits
        )

        for (key in keys) {
            val credentials = WifiCredentials("Guest", WifiSecurity.WEP, key)

            assertTrue("expected $key to build", build(credentials) is WifiBuildResult.Success)
        }
    }

    @Test
    fun `a ten-character WEP key that is not hexadecimal is rejected`() {
        val credentials = WifiCredentials("Guest", WifiSecurity.WEP, "notahexkey")

        assertTrue(
            WifiViolation(WifiField.PASSWORD, WifiViolationReason.INVALID_WEP_KEY)
                in build(credentials).violationsOrFail(),
        )
    }

    @Test
    fun `a WEP key of the wrong length is rejected`() {
        val credentials = WifiCredentials("Guest", WifiSecurity.WEP, "abcdefg")

        assertTrue(
            WifiViolation(WifiField.PASSWORD, WifiViolationReason.INVALID_WEP_KEY)
                in build(credentials).violationsOrFail(),
        )
    }

    /** Anything typed before switching to an open network is simply not written out. */
    @Test
    fun `an open network ignores a password rather than complaining about it`() {
        val credentials = WifiCredentials("Guest", WifiSecurity.OPEN, "leftover")

        val result = build(credentials)

        assertTrue(result is WifiBuildResult.Success)
        assertTrue("P:" !in (result as WifiBuildResult.Success).payload)
    }

    @Test
    fun `every violation is reported, not just the first`() {
        val credentials = WifiCredentials("", WifiSecurity.WPA, "")

        val violations = build(credentials).violationsOrFail()

        assertTrue(violations.any { it.field == WifiField.SSID })
        assertTrue(violations.any { it.field == WifiField.PASSWORD })
    }

    private fun WifiBuildResult.violationsOrFail(): List<WifiViolation> = when (this) {
        is WifiBuildResult.Success -> throw AssertionError("expected Invalid but built $payload")
        is WifiBuildResult.Invalid -> violations
    }
}
