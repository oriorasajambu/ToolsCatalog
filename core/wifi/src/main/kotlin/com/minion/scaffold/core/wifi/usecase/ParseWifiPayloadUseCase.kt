package com.minion.scaffold.core.wifi.usecase

import com.minion.scaffold.core.wifi.format.WifiPayloadFormat
import com.minion.scaffold.core.wifi.format.WifiValueCodec
import com.minion.scaffold.core.wifi.model.WifiCredentials
import com.minion.scaffold.core.wifi.model.WifiSecurity
import javax.inject.Inject

/**
 * Reads a Wi-Fi credential payload, or reports that it is not one.
 *
 * Null is not a failure. A payment code is not a broken Wi-Fi code, and treating "different
 * format" as an error would make the scanner tell someone their perfectly good QR is damaged.
 * That is the same reason `QrScanError` wraps `QrParseError` rather than extending it.
 *
 * Liberal in what it accepts — any field order, a missing terminator, `WPA2` where the writer
 * would emit `WPA` — because generators in the wild are inconsistent about all three and a code
 * that a phone can join should not be one this tool refuses to read.
 */
class ParseWifiPayloadUseCase @Inject constructor() {

    /**
     * Reads [payload] into credentials, or returns null when it is not a Wi-Fi payload.
     *
     * @param payload The scanned or pasted payload, whitespace and all.
     * @return The parsed [WifiCredentials], or `null` if [payload] is not a `WIFI:` code or names
     *         a security type this tool cannot represent.
     */
    operator fun invoke(payload: String): WifiCredentials? {
        val trimmed = payload.trim()
        if (!trimmed.startsWith(WifiPayloadFormat.PREFIX, ignoreCase = true)) return null

        val fields = trimmed
            .drop(WifiPayloadFormat.PREFIX.length)
            .let { WifiValueCodec.splitUnescaped(it, WifiPayloadFormat.FIELD_SEPARATOR) }
            .filter { it.isNotEmpty() }
            .mapNotNull(WifiValueCodec::splitKeyValue)
            .associate { (key, value) -> key.uppercase() to value }

        // An absent T means an open network; a T this tool cannot represent means the payload is
        // unreadable rather than guessed at. Being wrong about a network's security is not the
        // kind of wrong to be helpful about.
        val security = when (val code = fields[WifiPayloadFormat.KEY_SECURITY]) {
            null -> WifiSecurity.OPEN
            else -> WifiSecurity.fromCode(code) ?: return null
        }

        val ssid = fields[WifiPayloadFormat.KEY_SSID]
            ?.let(WifiValueCodec::decode)
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return WifiCredentials(
            ssid = ssid,
            security = security,
            password = fields[WifiPayloadFormat.KEY_PASSWORD]
                ?.let(WifiValueCodec::decode)
                .orEmpty(),
            hidden = fields[WifiPayloadFormat.KEY_HIDDEN]
                ?.equals(WifiPayloadFormat.HIDDEN_TRUE, ignoreCase = true) == true,
        )
    }
}
