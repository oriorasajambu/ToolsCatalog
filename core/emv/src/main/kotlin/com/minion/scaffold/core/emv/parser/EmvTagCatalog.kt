package com.minion.scaffold.core.emv.parser

import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.TagInterpretation
import com.minion.scaffold.core.emv.model.TipIndicator
import com.minion.scaffold.core.emv.reference.CurrencyCodes
import com.minion.scaffold.core.emv.reference.MerchantCategoryCodes

/**
 * What the EMVCo Merchant Presented Mode specification says about each tag: which ones nest, and
 * how to read the ones whose values are codes rather than words.
 *
 * Separate from [EmvTlvParser] so that framing and meaning can change independently — teaching
 * the tool a new tag touches this object alone.
 *
 * Human-readable *labels* are deliberately absent. Those are `@StringRes` in the presentation
 * layer, so they stay translatable and this stays a pure lookup.
 */
internal object EmvTagCatalog {

    const val TAG_PAYLOAD_FORMAT_INDICATOR = "00"
    const val TAG_POINT_OF_INITIATION_METHOD = "01"
    const val TAG_MERCHANT_CATEGORY_CODE = "52"
    const val TAG_TRANSACTION_CURRENCY = "53"
    const val TAG_TRANSACTION_AMOUNT = "54"
    const val TAG_TIP_INDICATOR = "55"
    const val TAG_CONVENIENCE_FEE_FIXED = "56"
    const val TAG_CONVENIENCE_FEE_PERCENTAGE = "57"
    const val TAG_COUNTRY_CODE = "58"
    const val TAG_MERCHANT_NAME = "59"
    const val TAG_MERCHANT_CITY = "60"
    const val TAG_POSTAL_CODE = "61"
    const val TAG_CRC = "63"

    /** Subtags inside a merchant account template. */
    const val SUBTAG_GLOBALLY_UNIQUE_IDENTIFIER = "00"
    const val SUBTAG_MERCHANT_PAN = "01"
    const val SUBTAG_MERCHANT_ID = "02"
    const val SUBTAG_MERCHANT_CRITERIA = "03"

    /** The only payload format the specification defines. */
    const val PAYLOAD_FORMAT_VERSION = "01"

    /** Tag `63` always holds exactly four hexadecimal characters. */
    const val CRC_VALUE_LENGTH = 4

    /**
     * The longest value any segment can carry.
     *
     * The length field is two decimal digits, so a hundred-character value cannot be expressed —
     * it would wrap to `00` and the payload would frame as something else entirely rather than
     * failing loudly. The builder rejects it; the parser can never encounter it.
     */
    const val MAX_VALUE_LENGTH = 99

    /** The tags a merchant account template may occupy. */
    val MERCHANT_ACCOUNT_TAGS = 26..51

    private val UNRESERVED_TEMPLATES = 80..99
    private const val TAG_ADDITIONAL_DATA = 62
    private const val TAG_MERCHANT_NAME_ALTERNATE_LANGUAGE = 64

    /**
     * Whether [tag] is a template — a tag whose value is itself a sequence of TLV segments.
     *
     * Tags `02`–`25` are excluded even though they are also Merchant Account Information. Those
     * carry a plain network identifier: tag `04` in a live payload holds a bare
     * `539199000000190`, and treating it as a template would be an attempt to find structure in a
     * number. Only `26`–`51` nest.
     *
     * A true answer here starts an *attempt* at nesting, not a commitment — see
     * `EmvTlvParser.readChildren`.
     *
     * @param tag The two-character tag to classify.
     * @return `true` if [tag] is a template tag whose value may itself be TLV segments.
     */
    fun isTemplate(tag: String): Boolean {
        val numericTag = tag.toIntOrNull() ?: return false
        return numericTag in MERCHANT_ACCOUNT_TAGS ||
            numericTag == TAG_ADDITIONAL_DATA ||
            numericTag == TAG_MERCHANT_NAME_ALTERNATE_LANGUAGE ||
            numericTag in UNRESERVED_TEMPLATES
    }

    /**
     * Decodes [rawValue] for the tags that carry a code rather than readable text.
     *
     * Everything else returns [TagInterpretation.None]: a merchant name, city or postal code is
     * already what it represents.
     *
     * Tag `63` is not handled here — its interpretation needs the recomputed checksum, which only
     * the caller holding the whole payload can produce.
     *
     * @param tag      The two-character tag whose value to decode.
     * @param rawValue The segment's raw value, exactly as it appears in the payload.
     * @return The decoded [TagInterpretation], or [TagInterpretation.None] for tags that carry
     *         readable text rather than a code.
     */
    fun interpret(tag: String, rawValue: String): TagInterpretation = when (tag) {
        TAG_PAYLOAD_FORMAT_INDICATOR -> TagInterpretation.PayloadVersion(
            version = rawValue.trimStart('0').ifEmpty { "0" },
        )

        TAG_POINT_OF_INITIATION_METHOD -> TagInterpretation.InitiationMethod(
            method = PointOfInitiationMethod.fromCode(rawValue),
        )

        TAG_TIP_INDICATOR -> TagInterpretation.Tip(TipIndicator.fromCode(rawValue))

        TAG_MERCHANT_CATEGORY_CODE -> TagInterpretation.MerchantCategory(
            code = rawValue,
            name = MerchantCategoryCodes.nameOf(rawValue),
        )

        TAG_TRANSACTION_CURRENCY -> {
            val currency = CurrencyCodes.of(rawValue)
            TagInterpretation.Currency(
                numericCode = rawValue,
                alphaCode = currency?.alphaCode,
                name = currency?.name,
            )
        }

        else -> TagInterpretation.None
    }
}
