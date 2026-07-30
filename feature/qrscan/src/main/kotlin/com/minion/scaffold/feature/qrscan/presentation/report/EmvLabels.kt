package com.minion.scaffold.feature.qrscan.presentation.report

import android.content.res.Resources
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.core.emv.model.TagInterpretation
import com.minion.scaffold.core.emv.model.TipIndicator
import com.minion.scaffold.feature.qrscan.presentation.QrScanError

/**
 * Everything that turns decoded structure into words.
 *
 * These take a [Resources] rather than being `@Composable` and calling `stringResource`, because
 * the same labels are needed off the composition to build the shareable plain-text report. Two
 * implementations of the same mapping would drift the first time a label was reworded, and the
 * one nobody looks at — the shared text — would be the one that went stale.
 *
 * Read `LocalResources.current` at the call site, never `LocalContext.current.getString()`: a
 * `Context` read is not invalidated by a configuration change, so the labels would keep the old
 * locale after a language switch.
 */

/**
 * The specification's name for [tag].
 *
 * Ranges rather than an entry per tag: `02`–`51` are all Merchant Account Information and differ
 * only by which payment network claimed the slot, so the tag number goes in the label instead of
 * fifty near-identical strings.
 */
internal fun tagLabel(resources: Resources, tag: String): String =
    when (val numericTag = tag.toIntOrNull()) {
        null -> resources.getString(R.string.qrscan_tag_unknown, tag)
        TAG_PAYLOAD_FORMAT_INDICATOR -> resources.getString(R.string.qrscan_tag_payload_format)
        TAG_POINT_OF_INITIATION -> resources.getString(R.string.qrscan_tag_initiation_method)
        in MERCHANT_ACCOUNT_RANGE -> resources.getString(R.string.qrscan_tag_merchant_account, tag)
        TAG_MERCHANT_CATEGORY -> resources.getString(R.string.qrscan_tag_merchant_category)
        TAG_TRANSACTION_CURRENCY -> resources.getString(R.string.qrscan_tag_currency)
        TAG_TRANSACTION_AMOUNT -> resources.getString(R.string.qrscan_tag_amount)
        TAG_TIP_INDICATOR -> resources.getString(R.string.qrscan_tag_tip_indicator)
        TAG_CONVENIENCE_FEE_FIXED -> resources.getString(R.string.qrscan_tag_fee_fixed)
        TAG_CONVENIENCE_FEE_PERCENTAGE -> resources.getString(R.string.qrscan_tag_fee_percentage)
        TAG_COUNTRY_CODE -> resources.getString(R.string.qrscan_tag_country)
        TAG_MERCHANT_NAME -> resources.getString(R.string.qrscan_tag_merchant_name)
        TAG_MERCHANT_CITY -> resources.getString(R.string.qrscan_tag_merchant_city)
        TAG_POSTAL_CODE -> resources.getString(R.string.qrscan_tag_postal_code)
        TAG_ADDITIONAL_DATA -> resources.getString(R.string.qrscan_tag_additional_data)
        TAG_CRC -> resources.getString(R.string.qrscan_tag_crc)
        TAG_ALTERNATE_LANGUAGE -> resources.getString(R.string.qrscan_tag_alternate_language)
        in RFU_RANGE -> resources.getString(R.string.qrscan_tag_rfu, tag)
        in UNRESERVED_RANGE -> resources.getString(R.string.qrscan_tag_unreserved, tag)
        else -> resources.getString(R.string.qrscan_tag_unknown, numericTag.toString())
    }

/**
 * What a segment's value means, or null when the raw value already says it.
 *
 * Tag `63` returns null deliberately: its checksum is rendered by the integrity section, where
 * the expected and computed values can sit next to each other.
 */
internal fun TagInterpretation.describe(resources: Resources): String? = when (this) {
    TagInterpretation.None -> null
    is TagInterpretation.Checksum -> null

    is TagInterpretation.PayloadVersion ->
        resources.getString(R.string.qrscan_value_payload_version, version)

    is TagInterpretation.InitiationMethod -> resources.getString(
        when (method) {
            PointOfInitiationMethod.STATIC -> R.string.qrscan_value_initiation_static
            PointOfInitiationMethod.DYNAMIC -> R.string.qrscan_value_initiation_dynamic
            PointOfInitiationMethod.UNKNOWN -> R.string.qrscan_value_initiation_unknown
        },
    )

    is TagInterpretation.Tip -> resources.getString(
        when (indicator) {
            TipIndicator.PROMPT -> R.string.qrscan_value_tip_prompt
            TipIndicator.FIXED_FEE -> R.string.qrscan_value_tip_fixed_fee
            TipIndicator.PERCENTAGE_FEE -> R.string.qrscan_value_tip_percentage_fee
            TipIndicator.UNKNOWN -> R.string.qrscan_value_tip_unknown
        },
    )

    // The code is already shown as the raw value, so an unknown category adds the fact that it is
    // unrecognized rather than repeating the number back.
    is TagInterpretation.MerchantCategory ->
        name ?: resources.getString(R.string.qrscan_value_category_unknown)

    is TagInterpretation.Currency -> if (alphaCode != null && name != null) {
        resources.getString(R.string.qrscan_value_currency, alphaCode, name)
    } else {
        resources.getString(R.string.qrscan_value_currency_unknown)
    }
}

/** Why the screen has nothing to show. */
internal fun QrScanError.describe(resources: Resources): String = when (this) {
    is QrScanError.Parse -> error.describe(resources)
    QrScanError.UnrecognisedFormat -> resources.getString(R.string.qrscan_error_unrecognised)
    QrScanError.NoBarcodeInImage -> resources.getString(R.string.qrscan_error_no_barcode)
    QrScanError.ImageUnreadable -> resources.getString(R.string.qrscan_error_image_unreadable)
}

/** Why a payload could not be read. */
internal fun QrParseError.describe(resources: Resources): String = when (this) {
    QrParseError.EmptyPayload -> resources.getString(R.string.qrscan_error_empty)
    QrParseError.NotAnEmvPayload -> resources.getString(R.string.qrscan_error_not_emv)
    QrParseError.MissingPayloadFormatIndicator ->
        resources.getString(R.string.qrscan_error_missing_format_indicator)

    QrParseError.MissingCrc -> resources.getString(R.string.qrscan_error_missing_crc)
    is QrParseError.MalformedTlv -> resources.getString(R.string.qrscan_error_malformed, offset)
    is QrParseError.LengthOverrun ->
        resources.getString(R.string.qrscan_error_overrun, tag, declaredLength, available)
}

private const val TAG_PAYLOAD_FORMAT_INDICATOR = 0
private const val TAG_POINT_OF_INITIATION = 1
private const val TAG_MERCHANT_CATEGORY = 52
private const val TAG_TRANSACTION_CURRENCY = 53
private const val TAG_TRANSACTION_AMOUNT = 54
private const val TAG_TIP_INDICATOR = 55
private const val TAG_CONVENIENCE_FEE_FIXED = 56
private const val TAG_CONVENIENCE_FEE_PERCENTAGE = 57
private const val TAG_COUNTRY_CODE = 58
private const val TAG_MERCHANT_NAME = 59
private const val TAG_MERCHANT_CITY = 60
private const val TAG_POSTAL_CODE = 61
private const val TAG_ADDITIONAL_DATA = 62
private const val TAG_CRC = 63
private const val TAG_ALTERNATE_LANGUAGE = 64

private val MERCHANT_ACCOUNT_RANGE = 2..51
private val RFU_RANGE = 65..79
private val UNRESERVED_RANGE = 80..99
