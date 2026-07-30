package com.minion.scaffold.core.emv.reference

/** An ISO 4217 currency: its numeric code as it appears in tag `53`, plus how to name it. */
data class Currency(
    val numericCode: String,
    val alphaCode: String,
    val name: String,
)

/**
 * ISO 4217 numeric code lookup for tag `53`.
 *
 * Not exhaustive — the full standard is ~180 entries, most of which cannot appear in a payload
 * this tool will ever see. An unlisted code returns null and is reported as the raw number rather
 * than guessed at.
 */
object CurrencyCodes {

    private val byNumericCode: Map<String, Currency> = listOf(
        Currency("036", "AUD", "Australian Dollar"),
        Currency("050", "BDT", "Bangladeshi Taka"),
        Currency("096", "BND", "Brunei Dollar"),
        Currency("104", "MMK", "Myanmar Kyat"),
        Currency("116", "KHR", "Cambodian Riel"),
        Currency("124", "CAD", "Canadian Dollar"),
        Currency("144", "LKR", "Sri Lankan Rupee"),
        Currency("156", "CNY", "Chinese Yuan"),
        Currency("208", "DKK", "Danish Krone"),
        Currency("344", "HKD", "Hong Kong Dollar"),
        Currency("356", "INR", "Indian Rupee"),
        Currency("360", "IDR", "Indonesian Rupiah"),
        Currency("392", "JPY", "Japanese Yen"),
        Currency("410", "KRW", "South Korean Won"),
        Currency("418", "LAK", "Lao Kip"),
        Currency("458", "MYR", "Malaysian Ringgit"),
        Currency("484", "MXN", "Mexican Peso"),
        Currency("524", "NPR", "Nepalese Rupee"),
        Currency("554", "NZD", "New Zealand Dollar"),
        Currency("578", "NOK", "Norwegian Krone"),
        Currency("586", "PKR", "Pakistani Rupee"),
        Currency("608", "PHP", "Philippine Peso"),
        Currency("643", "RUB", "Russian Ruble"),
        Currency("682", "SAR", "Saudi Riyal"),
        Currency("702", "SGD", "Singapore Dollar"),
        Currency("704", "VND", "Vietnamese Dong"),
        Currency("710", "ZAR", "South African Rand"),
        Currency("752", "SEK", "Swedish Krona"),
        Currency("756", "CHF", "Swiss Franc"),
        Currency("764", "THB", "Thai Baht"),
        Currency("784", "AED", "UAE Dirham"),
        Currency("826", "GBP", "Pound Sterling"),
        Currency("840", "USD", "US Dollar"),
        Currency("901", "TWD", "New Taiwan Dollar"),
        Currency("949", "TRY", "Turkish Lira"),
        Currency("978", "EUR", "Euro"),
    ).associateBy(Currency::numericCode)

    /**
     * Every currency in the table, ordered by numeric code.
     *
     * Exists so a currency picker is built from this table rather than a parallel list kept in
     * some feature module — adding a currency here has to be the only edit, or the two lists
     * diverge and a payload decodes to a name the form could not have produced.
     */
    val all: List<Currency> = byNumericCode.values.toList()

    /**
     * The currency for [numericCode], or null if it is not in the table.
     *
     * Pads to three digits: the code is fixed-width in the payload, but a caller holding `"36"`
     * from somewhere else should still find `IDR` rather than silently miss.
     */
    fun of(numericCode: String): Currency? = byNumericCode[numericCode.padStart(3, '0')]
}
