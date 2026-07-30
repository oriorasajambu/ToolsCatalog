package com.minion.scaffold.core.vcard.model

/** Mirrors the other formats: every violation at once, so a form can mark all its bad fields. */
sealed interface VCardBuildResult {

    data class Success(val payload: String) : VCardBuildResult

    data class Invalid(val violations: List<VCardViolation>) : VCardBuildResult
}

/** Carries no user-facing text; the presentation layer maps it to a `@StringRes`. */
data class VCardViolation(
    val field: VCardField,
    val reason: VCardViolationReason,
)

enum class VCardField { DISPLAY_NAME, PHONE, EMAIL }

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
