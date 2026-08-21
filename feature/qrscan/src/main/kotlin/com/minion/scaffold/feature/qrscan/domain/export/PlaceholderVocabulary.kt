package com.minion.scaffold.feature.qrscan.domain.export

/**
 * Every value a schema template can name, and what each one is.
 *
 * Deliberately wider than the built-in contract needs. A name that is missing is a code change and
 * a release, which is the exact situation a user-supplied schema exists to escape — so anything the
 * parse already produces is offered, whether or not the default document happens to use it.
 *
 * The descriptions are English rather than `@StringRes` on purpose: they name protocol values and
 * appear beside the placeholder token itself, in a reference a template author reads while writing
 * JSON. Translating half of that pairing would make it harder to use, not easier.
 */
internal enum class PlaceholderName(
    val token: String,
    val description: String,
) {

    // What kind of code it is.
    SchemeType("scheme_type", "qris for an Indonesian code priced in rupiah, otherwise qrcrossborder"),
    InitiationType("initiation_type", "dynamic or static, from tag 01"),
    InitiationMethodCode("initiation_method_code", "Tag 01 exactly as written, e.g. 12"),
    AmountType("amount_type", "FIXED_AMOUNT for a dynamic code, INPUT_AMOUNT for a static one"),

    // The merchant.
    MerchantName("merchant_name", "Tag 59"),
    MerchantCity("merchant_city", "Tag 60"),
    MerchantCountryCode("merchant_country_code", "Tag 58"),
    MerchantPostalCode("merchant_postal_code", "Tag 61"),
    MerchantCategoryCode("merchant_category_code", "Tag 52, the four-digit ISO 18245 code"),
    MerchantCriteria("merchant_criteria", "Subtag 03 of the primary account, e.g. UMI"),

    // The accounts. Primary means the first template carrying a subtag 01, which is the acquirer
    // on a domestic code and the only account on a cross-border one.
    MerchantPaymentProviderName(
        "merchant_payment_provider_name",
        "Subtag 00 of the primary account, e.g. ID.CO.CIMBNIAGA.WWW",
    ),
    MerchantPan("merchant_pan", "Subtag 01 of the primary account"),
    MerchantId("merchant_id", "Subtag 02 of the primary account"),
    MerchantIdNational("merchant_id_national", "Subtag 02 of the other account, if there is one"),
    MerchantProviderNational("merchant_provider_national", "Subtag 00 of the other account"),

    // Money.
    Amount("amount", "Tag 54 exactly as written, never reformatted"),
    AmountCurrency("amount_currency", "Tag 53 as its letter code, e.g. IDR"),
    AmountCurrencyCode("amount_currency_code", "Tag 53 as its number, e.g. 360"),
    CurrencyAllowed("currency_allowed", "An array holding amount_currency, or empty"),
    Tips("tips", "Tag 56's amount or tag 57's rate, depending on tips_type"),
    TipsType("tips_type", "NO_TIPS, INPUT_TIPS, FIXED_TIPS or PERCENTAGE_TIPS"),
    TotalPayment("total_payment", "Amount plus a computable tip, or null when there is none"),

    // Integrity, and the whole thing.
    CrcExpected("crc_expected", "The checksum the payload carries in tag 63"),
    CrcActual("crc_actual", "The checksum recomputed over the payload"),
    CrcValid("crc_valid", "true when the two agree"),
    Payload("payload", "The entire scanned string"),
    ;

    companion object {

        private val byToken = entries.associateBy(PlaceholderName::token)

        /** The entry [token] names, or null when the vocabulary has no such value. */
        fun ofToken(token: String): PlaceholderName? = byToken[token]
    }
}

/**
 * The vocabulary as a reference, for the settings screen and for validating an imported template.
 */
internal object PlaceholderVocabulary {

    /** Every named value, in the order the reference lists them. */
    val names: List<PlaceholderName> = PlaceholderName.entries

    /**
     * Whether [placeholder] is something this app can resolve.
     *
     * A [Placeholder.TagPath] is always answerable — the answer may be null, but that is a property
     * of the code being scanned rather than of the template.
     */
    fun recognises(placeholder: Placeholder): Boolean = when (placeholder) {
        is Placeholder.TagPath -> true
        is Placeholder.Named -> PlaceholderName.ofToken(placeholder.token) != null
    }
}
