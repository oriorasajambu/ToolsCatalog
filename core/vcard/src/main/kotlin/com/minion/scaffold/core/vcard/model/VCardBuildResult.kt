package com.minion.scaffold.core.vcard.model

/** Mirrors the other formats: every violation at once, so a form can mark all its bad fields. */
sealed interface VCardBuildResult {

    /**
     * The card was valid and produced a payload.
     *
     * @property payload The complete vCard 3.0 payload string.
     */
    data class Success(val payload: String) : VCardBuildResult

    /**
     * The card was rejected. Carries every violation so a form can mark all bad fields.
     *
     * @property violations One entry per rejected field. Never empty — an empty list is [Success].
     */
    data class Invalid(val violations: List<VCardViolation>) : VCardBuildResult
}

/**
 * One rejected field. Carries no user-facing text; the presentation layer maps it to a `@StringRes`.
 *
 * @property field  The input that was rejected.
 * @property reason Why [field] was rejected.
 */
data class VCardViolation(
    val field: VCardField,
    val reason: VCardViolationReason,
)

/** The inputs a card can be wrong about. */
enum class VCardField {

    /** The `FN` display name. */
    DISPLAY_NAME,

    /** The `TEL` phone number. */
    PHONE,

    /** The `EMAIL` address. */
    EMAIL,
}

/** Why a field was rejected. */
enum class VCardViolationReason {

    /** Only the display name is required — vCard 3.0 refuses a card without `FN`. */
    REQUIRED,

    /** No `@`, or nothing either side of it. */
    INVALID_EMAIL,

    /**
     * Not a digit in sight.
     *
     * Deliberately the only check on a phone number. Numbers are written with spaces, dashes,
     * brackets, `+` and extensions in every combination, and a stricter rule rejects more real
     * numbers than fake ones.
     */
    INVALID_PHONE,
}
