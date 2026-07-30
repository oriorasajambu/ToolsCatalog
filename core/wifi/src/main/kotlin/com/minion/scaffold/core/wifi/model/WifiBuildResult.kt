package com.minion.scaffold.core.wifi.model

/** Mirrors `EmvBuildResult`: every violation at once, so a form can mark all its bad fields. */
sealed interface WifiBuildResult {

    data class Success(val payload: String) : WifiBuildResult

    data class Invalid(val violations: List<WifiViolation>) : WifiBuildResult
}

/** Carries no user-facing text; the presentation layer maps it to a `@StringRes`. */
data class WifiViolation(
    val field: WifiField,
    val reason: WifiViolationReason,
)

enum class WifiField { SSID, PASSWORD }

enum class WifiViolationReason {

    REQUIRED,

    /** An SSID over 32 characters, or a WPA passphrase over 63. */
    TOO_LONG,

    /** A WPA passphrase under 8 characters — the shortest 802.11 accepts. */
    TOO_SHORT,

    /**
     * A WEP key that is none of the four legal shapes.
     *
     * One reason rather than four, because "5 or 13 characters, or 10 or 26 hexadecimal digits" is
     * a single thing to explain and splitting it would produce four messages saying it four ways.
     */
    INVALID_WEP_KEY,
}
