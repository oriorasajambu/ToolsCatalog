package com.minion.scaffold.feature.qrscan.domain.export

/**
 * The payment response contract: its key names, its enumerated values, and the fields a QR code
 * cannot supply.
 *
 * ## Why none of this is in `strings.xml`
 *
 * Everything here is **protocol**, not copy. `"FIXED_TIPS"` is a value another system matches on
 * and `"merchant_pan"` is a field it looks up by name; running either through a string resource
 * would mean a device set to Bahasa exported a document with different field names, and the
 * failure would appear at whatever consumes it rather than here. This file is the one place in
 * this feature where the no-literals rule does not apply, and it is scoped to exactly that.
 *
 * ## Why the constants exist at all
 *
 * A third of the contract describes issuer state — a daily limit, a transaction identifier, which
 * sources of funds are permitted. None of it is in an EMV payload and none of it can be derived
 * from one. They are emitted as fixed sample values so the document drops into a stub server
 * without editing, which means a **fabricated limit sits beside a real merchant PAN with nothing
 * marking them apart**. That is a deliberate trade for a developer tool, and the share sheet says
 * so before anything leaves the device.
 */
internal object PaymentResponseFields {

    // Top level.
    const val TRANSACTION_ID = "transaction_id"
    const val TRAN_CODE = "tran_code"
    const val QR_TYPE = "qr_type"
    const val QR_DATA = "qr_data"
    const val QR_TEMPLATE_TYPE = "qr_template_type"
    const val LIMIT = "limit"
    const val SOF_ALLOWED = "sof_allowed"
    const val CURRENCY_ALLOWED = "currency_allowed"
    const val BANKED_STATUS = "banked_status"

    // qr_data.
    const val TRAN_MERCHANT_DATA = "tran_merchant_data"

    // tran_merchant_data.
    const val TIPS = "tips"
    const val TIPS_TYPE = "tips_type"
    const val TIPS_CURRENCY = "tips_currency"
    const val MERCHANT_NAME = "merchant_name"
    const val MERCHANT_COUNTRY_CODE = "merchant_country_code"
    const val MERCHANT_CITY = "merchant_city"
    const val MERCHANT_POSTAL_CODE = "merchant_postal_code"
    const val MERCHANT_PAYMENT_PROVIDER_NAME = "merchant_payment_provider_name"
    const val MERCHANT_PAN = "merchant_pan"
    const val AMOUNT = "amount"
    const val AMOUNT_TYPE = "amount_type"
    const val AMOUNT_CURRENCY = "amount_currency"
    const val DYNAMIC_QR = "dynamic_qr"
    const val MERCHANT_ID = "merchant_id"
    const val MERCHANT_ID_NATIONAL = "merchant_id_national"
    const val IS_AMOUNT_ALLOWED_DECIMAL = "is_amount_allowed_decimal"

    // dynamic_qr.
    const val PAYMENT_TYPE = "payment_type"
    const val TOTAL_PAYMENT = "total_payment"
    const val TOTAL_PAYMENT_CURRENCY = "total_payment_currency"

    // limit.
    const val LIMIT_PER_DAILY = "limit_per_daily"
    const val REMAINING_LIMIT_PER_DAILY = "remaining_limit_per_daily"
    const val LIMIT_PER_TRANSACTION = "limit_per_transaction"

    /** Domestic Indonesian QRIS: country `ID` **and** currency `360`. */
    const val QR_TYPE_QRIS = "qris"

    /** Anything else. */
    const val QR_TYPE_CROSS_BORDER = "qrcrossborder"

    /** Tag `01` = `12`. */
    const val QR_DATA_TYPE_DYNAMIC = "dynamic"

    /** Tag `01` = `11`, and the fallback when the tag is missing or carries an unknown code. */
    const val QR_DATA_TYPE_STATIC = "static"

    /** Derived from tag `01`, not from the presence of tag `54` — see the use case's note. */
    const val AMOUNT_TYPE_FIXED = "FIXED_AMOUNT"
    const val AMOUNT_TYPE_INPUT = "INPUT_AMOUNT"

    /** No tag `55`, or one carrying a code the specification does not define. */
    const val TIPS_TYPE_NONE = "NO_TIPS"

    /** Tag `55` = `01`. The payer will be asked, so there is no figure yet. */
    const val TIPS_TYPE_INPUT = "INPUT_TIPS"

    /** Tag `55` = `02`, with the amount in tag `56`. */
    const val TIPS_TYPE_FIXED = "FIXED_TIPS"

    /** Tag `55` = `03`, with a whole-percentage rate in tag `57`. */
    const val TIPS_TYPE_PERCENTAGE = "PERCENTAGE_TIPS"

    /**
     * Values no payload carries.
     *
     * Fixed rather than randomised so two exports of two different codes differ only where the
     * codes do — a diff of them is then a diff of the QRs, which is the only reason to compare two
     * of these documents at all.
     */
    const val SAMPLE_TRANSACTION_ID = "000000012687"
    const val SAMPLE_TRAN_CODE = "6012"
    const val SAMPLE_QR_TEMPLATE_TYPE = "QRMerchant"
    const val SAMPLE_PAYMENT_TYPE = "Payment with QR"
    const val SAMPLE_BANKED_STATUS = "CASA"
    const val SAMPLE_IS_AMOUNT_ALLOWED_DECIMAL = false
    const val SAMPLE_LIMIT_PER_DAILY = "25000000.00"
    const val SAMPLE_REMAINING_LIMIT_PER_DAILY = "25000000.00"
    const val SAMPLE_LIMIT_PER_TRANSACTION = "10000000.00"

    val SAMPLE_SOF_ALLOWED = listOf("CASA", "CC", "RPN", "LOCRC")

    /** Tag `58` and tag `53` for a domestic QRIS code. */
    const val COUNTRY_INDONESIA = "ID"
    const val CURRENCY_INDONESIA = "360"
}
