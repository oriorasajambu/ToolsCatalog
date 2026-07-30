package com.minion.scaffold.core.emv.model

/**
 * What a payload asks for on top of the transaction amount — tags `55`, `56` and `57`.
 *
 * Sealed rather than an indicator plus a nullable value, because the specification pairs each
 * indicator with exactly one companion tag: `02` requires a fixed fee in tag `56`, `03` requires a
 * rate in tag `57`, and `01` requires neither. Modelling it as `(indicator, value?)` would make
 * "prompt for a tip, and also here is a percentage" representable, and something would then have
 * to decide what that means.
 *
 * Absence of a tip is `null`, not a case here: tag `55` is simply not written.
 */
sealed interface TipSpec {

    /** Tag `55` = `01`. The payer's app asks them to enter a tip. */
    data object Prompt : TipSpec

    /** Tag `55` = `02`, with the amount in tag `56`. */
    data class FixedFee(val amount: String) : TipSpec

    /** Tag `55` = `03`, with the rate in tag `57`. A whole percentage, so `5` means 5%. */
    data class PercentageFee(val rate: String) : TipSpec
}

/**
 * Tag `55` as read from a payload.
 *
 * Separate from [TipSpec] because reading and writing are not symmetric: a scanned payload can
 * carry a code outside the three the specification defines, and the tool reports that rather than
 * refusing the payload. [TipSpec] has no equivalent case — there is nothing to write.
 */
enum class TipIndicator(val code: String?) {
    PROMPT("01"),
    FIXED_FEE("02"),
    PERCENTAGE_FEE("03"),

    /** A code the specification does not define. Reported, not rejected. */
    UNKNOWN(null),
    ;

    companion object {
        fun fromCode(code: String): TipIndicator =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
