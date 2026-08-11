package com.minion.scaffold.core.wifi.model

/** Mirrors `EmvBuildResult`: every violation at once, so a form can mark all its bad fields. */
sealed interface WifiBuildResult {

    /**
     * The credentials were valid and produced a payload.
     *
     * @property payload The complete `WIFI:` payload string.
     */
    data class Success(val payload: String) : WifiBuildResult

    /**
     * The credentials were rejected. Carries every violation so a form can mark all bad fields.
     *
     * @property violations One entry per rejected field. Never empty — an empty list is [Success].
     */
    data class Invalid(val violations: List<WifiViolation>) : WifiBuildResult
}

/**
 * One rejected field. Carries no user-facing text; the presentation layer maps it to a `@StringRes`.
 *
 * @property field  The input that was rejected.
 * @property reason Why [field] was rejected.
 */
data class WifiViolation(
    val field: WifiField,
    val reason: WifiViolationReason,
)

/** The inputs a set of credentials can be wrong about. */
enum class WifiField {

    /** The network name. */
    SSID,

    /** The passphrase or key. */
    PASSWORD,
}

/** Why a field was rejected. */
enum class WifiViolationReason {

    /** Left blank when the security type requires a value. */
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
