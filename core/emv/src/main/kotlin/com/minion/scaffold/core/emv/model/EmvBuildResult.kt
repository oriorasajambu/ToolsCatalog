package com.minion.scaffold.core.emv.model

/**
 * The outcome of writing a payload.
 *
 * [Invalid] carries *every* violation rather than the first, so a form can mark all its bad fields
 * in one pass instead of making the user fix them one submit at a time.
 */
sealed interface EmvBuildResult {

    data class Success(val payload: String) : EmvBuildResult

    data class Invalid(val violations: List<FieldViolation>) : EmvBuildResult
}

/**
 * One rejected field.
 *
 * Carries no user-facing text — the presentation layer maps this to a `@StringRes`, the same way
 * it already does for `QrParseError`. A message built here would be untranslatable and would put
 * copy in a module that has no resources.
 */
data class FieldViolation(
    val field: EmvField,
    val reason: ViolationReason,
    /**
     * Which merchant account the violation belongs to, or null for a top-level field.
     *
     * Without it a form showing two account templates could not tell which one to mark, and would
     * have to highlight both.
     */
    val accountIndex: Int? = null,
)

/** The inputs a draft can be wrong about. */
enum class EmvField {
    INITIATION_METHOD,
    MERCHANT_ACCOUNTS,
    ACQUIRER_TAG,
    ACQUIRER_IDENTIFIER,
    ACQUIRER_MERCHANT_PAN,
    ACQUIRER_MERCHANT_ID,
    ACQUIRER_CRITERIA,
    MERCHANT_CATEGORY_CODE,
    TRANSACTION_CURRENCY,
    TRANSACTION_AMOUNT,

    /**
     * The fixed fee or percentage rate, whichever the chosen tip mode uses.
     *
     * One field rather than two: the modes are mutually exclusive and the form shows a single
     * value box, so a violation can only ever belong to the mode currently selected.
     */
    CONVENIENCE_FEE,
    COUNTRY_CODE,
    MERCHANT_NAME,
    MERCHANT_CITY,
    POSTAL_CODE,
}

/**
 * Why a field was rejected.
 *
 * Kept small and *meaningful to whoever is filling the form*, not a mirror of the specification's
 * clauses: the test for a separate case is whether the UI would say something different.
 */
enum class ViolationReason {

    /** Left blank when the specification requires it. */
    REQUIRED,

    /** Longer than the tag allows. */
    TOO_LONG,

    /** Not the exact length the tag demands — currency, country and MCC are fixed-width. */
    WRONG_LENGTH,

    /** Contains something other than digits where only digits are allowed. */
    NOT_NUMERIC,

    /** Not a decimal amount: more than two decimal places, a stray sign, a second point. */
    NOT_AN_AMOUNT,

    /** Present when the specification says it must not be — an amount on a static payload. */
    NOT_ALLOWED,

    /** A number outside the range the field allows, such as a fee above 100 percent. */
    OUT_OF_RANGE,

    /** A value the writer cannot express, such as an unrecognised point of initiation. */
    UNSUPPORTED,
}
