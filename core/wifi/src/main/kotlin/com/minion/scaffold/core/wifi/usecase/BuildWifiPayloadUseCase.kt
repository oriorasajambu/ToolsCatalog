package com.minion.scaffold.core.wifi.usecase

import com.minion.scaffold.core.wifi.format.WifiPayloadFormat
import com.minion.scaffold.core.wifi.format.WifiValueCodec
import com.minion.scaffold.core.wifi.model.WifiBuildResult
import com.minion.scaffold.core.wifi.model.WifiCredentials
import com.minion.scaffold.core.wifi.model.WifiField
import com.minion.scaffold.core.wifi.model.WifiSecurity
import com.minion.scaffold.core.wifi.model.WifiViolation
import com.minion.scaffold.core.wifi.model.WifiViolationReason
import javax.inject.Inject

/**
 * Writes a Wi-Fi credential payload.
 *
 * Always emits the canonical form — `T`, `S`, `P`, `H` in that order, terminated `;;` — even
 * though [ParseWifiPayloadUseCase] accepts any order. Being liberal in what is read and strict in
 * what is written is what makes the round-trip test meaningful: if writing varied too, the test
 * would only prove the two halves agree with each other.
 */
class BuildWifiPayloadUseCase @Inject constructor() {

    operator fun invoke(credentials: WifiCredentials): WifiBuildResult {
        val violations = credentials.validate()

        return if (violations.isEmpty()) {
            WifiBuildResult.Success(credentials.assemble())
        } else {
            WifiBuildResult.Invalid(violations)
        }
    }

    private fun WifiCredentials.assemble(): String = buildString {
        append(WifiPayloadFormat.PREFIX)
        appendField(WifiPayloadFormat.KEY_SECURITY, security.code, encode = false)
        appendField(WifiPayloadFormat.KEY_SSID, ssid)

        // Omitted rather than written empty for an open network: `P:;` claims the network has a
        // password that happens to be blank, which is a different statement from having none.
        if (security != WifiSecurity.OPEN) {
            appendField(WifiPayloadFormat.KEY_PASSWORD, password)
        }

        // `H:false` is legal and pointless. Leaving it out keeps the payload shorter, which keeps
        // the QR less dense and easier to scan from a wall.
        if (hidden) {
            appendField(WifiPayloadFormat.KEY_HIDDEN, WifiPayloadFormat.HIDDEN_TRUE, encode = false)
        }

        // The terminator. Combined with the last field's own separator this is the `;;` every
        // reader looks for.
        append(WifiPayloadFormat.FIELD_SEPARATOR)
    }

    private fun StringBuilder.appendField(key: String, value: String, encode: Boolean = true) {
        append(key)
        append(WifiPayloadFormat.KEY_VALUE_SEPARATOR)
        append(if (encode) WifiValueCodec.encode(value) else value)
        append(WifiPayloadFormat.FIELD_SEPARATOR)
    }

    private fun WifiCredentials.validate(): List<WifiViolation> = buildList {
        when {
            ssid.isBlank() -> add(WifiViolation(WifiField.SSID, WifiViolationReason.REQUIRED))
            ssid.length > MAX_SSID_LENGTH ->
                add(WifiViolation(WifiField.SSID, WifiViolationReason.TOO_LONG))
        }

        addAll(passwordViolations())
    }

    private fun WifiCredentials.passwordViolations(): List<WifiViolation> = when (security) {
        // Anything typed before switching to an open network is simply not written out.
        WifiSecurity.OPEN -> emptyList()

        WifiSecurity.WPA -> when {
            password.isEmpty() ->
                listOf(WifiViolation(WifiField.PASSWORD, WifiViolationReason.REQUIRED))

            password.length < MIN_WPA_PASSPHRASE ->
                listOf(WifiViolation(WifiField.PASSWORD, WifiViolationReason.TOO_SHORT))

            password.length > MAX_WPA_PASSPHRASE ->
                listOf(WifiViolation(WifiField.PASSWORD, WifiViolationReason.TOO_LONG))

            else -> emptyList()
        }

        WifiSecurity.WEP -> when {
            password.isEmpty() ->
                listOf(WifiViolation(WifiField.PASSWORD, WifiViolationReason.REQUIRED))

            !password.isValidWepKey() ->
                listOf(WifiViolation(WifiField.PASSWORD, WifiViolationReason.INVALID_WEP_KEY))

            else -> emptyList()
        }
    }

    /** 5 or 13 characters of text, or 10 or 26 hexadecimal digits — 40-bit and 104-bit WEP. */
    private fun String.isValidWepKey(): Boolean = when (length) {
        WEP_TEXT_40, WEP_TEXT_104 -> true
        WEP_HEX_40, WEP_HEX_104 -> all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        else -> false
    }

    private companion object {
        /** The 802.11 limit. */
        const val MAX_SSID_LENGTH = 32

        const val MIN_WPA_PASSPHRASE = 8
        const val MAX_WPA_PASSPHRASE = 63

        const val WEP_TEXT_40 = 5
        const val WEP_TEXT_104 = 13
        const val WEP_HEX_40 = 10
        const val WEP_HEX_104 = 26
    }
}
