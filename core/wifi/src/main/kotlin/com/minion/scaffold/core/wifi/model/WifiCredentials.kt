package com.minion.scaffold.core.wifi.model

/**
 * What a Wi-Fi QR code carries: enough for a device to join a network without being told anything.
 *
 * [password] is empty for an open network rather than null — there is exactly one way to say "no
 * password", and a nullable field would allow two.
 */
data class WifiCredentials(
    val ssid: String,
    val security: WifiSecurity,
    val password: String = "",
    val hidden: Boolean = false,
)

/**
 * The security types this tool writes.
 *
 * WPA3 devices read a `WPA` code, so no separate case: emitting `SAE` produces a code that works
 * on recent phones and silently fails on older ones, which is worse than one that works
 * everywhere. Enterprise (`WPA2-EAP`) is absent because it needs an identity, an anonymous
 * identity, an EAP method and a phase-2 method that this model has nowhere to put.
 */
enum class WifiSecurity(val code: String) {
    WPA("WPA"),
    WEP("WEP"),
    OPEN("nopass"),
    ;

    companion object {

        /**
         * The security a scanned `T:` value names, or null for one this tool cannot represent.
         *
         * The aliases exist because generators are inconsistent: `WPA2`, `WPA2-PSK`, `WPA3` and
         * `SAE` all describe networks a `WPA` code joins. Null — for `WPA2-EAP` and anything
         * unknown — makes the whole payload unreadable rather than guessing at a network's
         * security, which is not a thing to be wrong about.
         */
        fun fromCode(code: String): WifiSecurity? = when (code.uppercase()) {
            "WPA", "WPA2", "WPA2-PSK", "WPA-PSK", "WPA3", "SAE", "PSK" -> WPA
            "WEP" -> WEP
            "NOPASS", "NONE", "" -> OPEN
            else -> null
        }
    }
}
