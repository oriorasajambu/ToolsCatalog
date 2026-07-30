package com.minion.scaffold.core.emv.model

/**
 * What a segment's raw value *means*, for the tags whose codes are not readable as-is.
 *
 * Typed data, never a formatted sentence: `Currency("360", "IDR", "Indonesian Rupiah")` and not
 * `"360 -> IDR (Indonesian Rupiah)"`. The arrow, the brackets and the wording are presentation
 * concerns, and baking them in here would put untranslatable UI copy in the domain and make the
 * decoders untestable without string matching.
 *
 * Most tags get [None]. A merchant name, city or postal code is already the thing it represents,
 * and inventing an interpretation for it would only add a branch that renders identically.
 */
sealed interface TagInterpretation {

    /** Render the raw value as-is. */
    data object None : TagInterpretation

    /** Tag `00`. [version] has leading zeroes stripped: `"01"` becomes `"1"`. */
    data class PayloadVersion(val version: String) : TagInterpretation

    /** Tag `01`. */
    data class InitiationMethod(val method: PointOfInitiationMethod) : TagInterpretation

    /** Tag `55`. The companion fee in tag `56` or `57` renders as its own segment. */
    data class Tip(val indicator: TipIndicator) : TagInterpretation

    /** Tag `52`. [name] is null when the code is not in the catalog. */
    data class MerchantCategory(val code: String, val name: String?) : TagInterpretation

    /** Tag `53`. [alphaCode] and [name] are null when the numeric code is not in the catalog. */
    data class Currency(
        val numericCode: String,
        val alphaCode: String?,
        val name: String?,
    ) : TagInterpretation

    /** Tag `63`. Carries the verdict so the segment row can show it inline. */
    data class Checksum(val verification: CrcVerification) : TagInterpretation
}

/**
 * Tag `01`. Whether the payload encodes a fixed merchant or a single transaction.
 *
 * The distinction matters to a reader: a static payload is a printed sticker reused for every
 * customer, a dynamic one is generated per transaction and usually carries an amount.
 */
enum class PointOfInitiationMethod(val code: String?) {
    STATIC("11"),
    DYNAMIC("12"),

    /**
     * A code outside the two the specification defines. Reported rather than rejected when
     * reading, and refused when building — hence the null [code], which is what makes "this
     * cannot be written" a property of the type rather than a rule someone has to remember.
     */
    UNKNOWN(null),
    ;

    companion object {
        fun fromCode(code: String): PointOfInitiationMethod =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
