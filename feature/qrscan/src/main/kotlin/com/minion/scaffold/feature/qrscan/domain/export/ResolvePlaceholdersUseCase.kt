package com.minion.scaffold.feature.qrscan.domain.export

import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.EmvPayloadDraft
import com.minion.scaffold.core.emv.model.MerchantAccount
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.model.TipSpec
import com.minion.scaffold.core.emv.model.TlvNode
import com.minion.scaffold.core.emv.reference.CurrencyCodes
import com.minion.scaffold.core.emv.usecase.EmvDraftFromPayloadUseCase
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * What a scanned code offers a schema template.
 *
 * Every derivation rule the export has ever had lives here, and nowhere else: this is what the
 * template *cannot* change, because it is arithmetic and EMV semantics rather than presentation.
 * A template decides what a field is called and where it sits; this decides what it is worth.
 *
 * Built on the draft [EmvDraftFromPayloadUseCase] already produces, which does the merchant-account
 * and tip extraction — and whose definition of an account template (a tag in `26`–`51` that
 * actually nests) picks up a domestic code's tags `26` and `51` and a cross-border code's tag `32`
 * while ignoring a flat identifier at tag `04`.
 */
internal class ResolvePlaceholdersUseCase @Inject constructor(
    private val draftFromPayload: EmvDraftFromPayloadUseCase,
) {

    /**
     * Every named value for [report].
     *
     * @param report An already-decoded payment code.
     * @return The vocabulary resolved, or null when the payload will not read back as a draft —
     *         unreachable from a report on screen, which by definition already parsed.
     */
    operator fun invoke(report: QrInquiryReport): PlaceholderValues? {
        val draft = when (val result = draftFromPayload(report.payload)) {
            is EmvParseResult.Success -> result.value
            is EmvParseResult.Failure -> return null
        }

        return PlaceholderValues(named = draft.named(report), report = report)
    }

    private fun EmvPayloadDraft.named(report: QrInquiryReport): Map<PlaceholderName, JsonElement> {
        val primary = primaryAccount()
        val secondary = secondaryAccount()
        val currency = currencyAlpha()

        return buildMap {
            put(PlaceholderName.SchemeType, text(schemeType()))
            put(PlaceholderName.InitiationType, text(initiationType()))
            put(PlaceholderName.InitiationMethodCode, text(initiationMethod.code))
            put(PlaceholderName.AmountType, text(amountType()))

            put(PlaceholderName.MerchantName, text(merchantName))
            put(PlaceholderName.MerchantCity, text(merchantCity))
            put(PlaceholderName.MerchantCountryCode, text(countryCode))
            put(PlaceholderName.MerchantPostalCode, text(postalCode))
            put(PlaceholderName.MerchantCategoryCode, text(merchantCategoryCode))
            put(PlaceholderName.MerchantCriteria, text(primary?.merchantCriteria))

            put(
                PlaceholderName.MerchantPaymentProviderName,
                text(primary?.globallyUniqueIdentifier),
            )
            put(PlaceholderName.MerchantPan, text(primary?.merchantPan))
            put(PlaceholderName.MerchantId, text(primary?.merchantId))
            put(PlaceholderName.MerchantIdNational, text(secondary?.merchantId))
            put(
                PlaceholderName.MerchantProviderNational,
                text(secondary?.globallyUniqueIdentifier),
            )

            // Raw, never reformatted: a payments figure put through a normaliser is one trailing
            // zero away from being wrong by a factor of ten.
            put(PlaceholderName.Amount, text(amount))
            put(PlaceholderName.AmountCurrency, text(currency))
            put(PlaceholderName.AmountCurrencyCode, text(currencyNumericCode))
            put(
                PlaceholderName.CurrencyAllowed,
                buildJsonArray { currency?.let { add(it) } },
            )
            put(PlaceholderName.Tips, text(tipValue()))
            put(PlaceholderName.TipsType, text(tipType()))
            put(PlaceholderName.TotalPayment, text(totalPayment()))

            put(PlaceholderName.CrcExpected, text(report.crc.expected))
            put(PlaceholderName.CrcActual, text(report.crc.actual))
            put(PlaceholderName.CrcValid, JsonPrimitive(report.crc.passed))
            put(PlaceholderName.Payload, text(report.payload))
        }
    }

    /**
     * Domestic or cross-border.
     *
     * Both halves are required. Country alone would call an Indonesian merchant priced in Singapore
     * dollars domestic, and currency alone would say the same of a Malaysian merchant priced in
     * rupiah.
     */
    private fun EmvPayloadDraft.schemeType(): String = if (
        countryCode == COUNTRY_INDONESIA && currencyNumericCode == CURRENCY_INDONESIA
    ) {
        SCHEME_QRIS
    } else {
        SCHEME_CROSS_BORDER
    }

    private fun EmvPayloadDraft.initiationType(): String =
        if (initiationMethod == PointOfInitiationMethod.DYNAMIC) DYNAMIC else STATIC

    /**
     * Whether the payer may choose the amount.
     *
     * Read from tag `01`, **not** from whether tag `54` is present. That is deliberate, and it has a
     * visible consequence: a dynamic code that omits its amount reports `FIXED_AMOUNT` beside a null
     * amount, and such codes do occur. Changing the rule is changing this one expression.
     */
    private fun EmvPayloadDraft.amountType(): String =
        if (initiationMethod == PointOfInitiationMethod.DYNAMIC) AMOUNT_FIXED else AMOUNT_INPUT

    /**
     * The tip figure as the payload wrote it.
     *
     * For a percentage this is a **rate**, not money — `"5"` meaning five percent. A consumer reads
     * `tips_type` to know which it is holding, because converting the rate here would assert a
     * figure the merchant never encoded.
     */
    private fun EmvPayloadDraft.tipValue(): String? = when (val spec = tip) {
        is TipSpec.FixedFee -> spec.amount
        is TipSpec.PercentageFee -> spec.rate
        TipSpec.Prompt, null -> null
    }

    /**
     * Which tip mode the code is in.
     *
     * A tag `55` carrying a code the specification does not define arrives here as a null [TipSpec]
     * — the draft mapper drops it rather than guessing — so it lands on `NO_TIPS` with no branch of
     * its own.
     */
    private fun EmvPayloadDraft.tipType(): String = when (tip) {
        is TipSpec.FixedFee -> TIPS_FIXED
        is TipSpec.PercentageFee -> TIPS_PERCENTAGE
        TipSpec.Prompt -> TIPS_INPUT
        null -> TIPS_NONE
    }

    /**
     * What the payer will actually be charged, when that is knowable.
     *
     * Null unless a tip both exists and is a figure: with no tip there is no total distinct from the
     * amount, and with a prompt the payer has not chosen yet. Also null when the amount will not
     * parse, which a damaged payload can produce — an export must not throw.
     */
    private fun EmvPayloadDraft.totalPayment(): String? {
        val base = amount?.trim()?.toBigDecimalOrNull() ?: return null

        val total = when (val spec = tip) {
            is TipSpec.FixedFee -> spec.amount.trim().toBigDecimalOrNull()?.let(base::add)

            is TipSpec.PercentageFee -> spec.rate.trim().toBigDecimalOrNull()?.let { rate ->
                // Rounded at the amount's own scale, so a total keeps the precision the merchant
                // priced in rather than inheriting whatever the multiplication produced.
                base.add(base.multiply(rate).divide(HUNDRED, base.scale(), RoundingMode.HALF_UP))
            }

            TipSpec.Prompt, null -> null
        }

        return total?.toPlainString()
    }

    /**
     * The acquiring account — the one carrying a primary account number in subtag `01`.
     *
     * Identity rather than position. A domestic code's acquirer template has a subtag `01` and its
     * national switch template does not, so the two separate themselves; a cross-border code has a
     * single template that does.
     */
    private fun EmvPayloadDraft.primaryAccount(): MerchantAccount? =
        merchantAccounts.firstOrNull { it.merchantPan != null }

    /** Whichever other template names an identifier — the national switch, on a domestic code. */
    private fun EmvPayloadDraft.secondaryAccount(): MerchantAccount? {
        val primaryIndex = merchantAccounts.indexOfFirst { it.merchantPan != null }

        return merchantAccounts
            .filterIndexed { index, _ -> index != primaryIndex }
            .firstOrNull { !it.merchantId.isNullOrBlank() }
    }

    /** The letter code, falling back to the raw number for a currency the table does not list. */
    private fun EmvPayloadDraft.currencyAlpha(): String? = currencyNumericCode
        .takeIf { it.isNotBlank() }
        ?.let { CurrencyCodes.of(it)?.alphaCode ?: it }

    /**
     * A string value, or null.
     *
     * Blank becomes null throughout: a field the code did not carry reads better as `null` than as
     * `""`, because the first says the QR was silent and the second says it said nothing.
     */
    private fun text(value: String?): JsonElement =
        value?.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull

    private companion object {
        const val COUNTRY_INDONESIA = "ID"
        const val CURRENCY_INDONESIA = "360"
        const val SCHEME_QRIS = "qris"
        const val SCHEME_CROSS_BORDER = "qrcrossborder"
        const val DYNAMIC = "dynamic"
        const val STATIC = "static"
        const val AMOUNT_FIXED = "FIXED_AMOUNT"
        const val AMOUNT_INPUT = "INPUT_AMOUNT"
        const val TIPS_NONE = "NO_TIPS"
        const val TIPS_INPUT = "INPUT_TIPS"
        const val TIPS_FIXED = "FIXED_TIPS"
        const val TIPS_PERCENTAGE = "PERCENTAGE_TIPS"

        val HUNDRED: BigDecimal = BigDecimal(100)
    }
}

/**
 * A scanned code's answers, ready for a template to draw on.
 *
 * Holds the report as well as the named values so that a `tag:` path can be walked on demand: the
 * vocabulary is finite and worth computing once, while the set of addressable tags is whatever the
 * payload happens to contain.
 */
internal class PlaceholderValues(
    private val named: Map<PlaceholderName, JsonElement>,
    private val report: QrInquiryReport,
) {

    /**
     * What [placeholder] is worth for this code.
     *
     * @return The value, or null when the placeholder is a name this app does not know. A value
     *         the code simply does not carry is [JsonNull], which is an answer rather than a fault.
     */
    fun resolve(placeholder: Placeholder): JsonElement? = when (placeholder) {
        is Placeholder.Named ->
            PlaceholderName.ofToken(placeholder.token)?.let { named[it] ?: JsonNull }

        is Placeholder.TagPath -> tagValue(placeholder.segments)
    }

    /** Every named value, for the reference screen. */
    fun namedValues(): Map<PlaceholderName, JsonElement> = named

    /**
     * The raw value at a dotted path, or [JsonNull].
     *
     * First occurrence at each level. A repeated tag is not valid EMV, but this tool exists to be
     * pointed at payloads that are not valid EMV, and taking the first is the only answer that does
     * not depend on which one a map happened to keep.
     */
    private fun tagValue(segments: List<String>): JsonElement {
        var nodes: List<TlvNode> = report.segments.map { it.node }
        var found: TlvNode? = null

        for (segment in segments) {
            found = nodes.firstOrNull { it.tag == segment } ?: return JsonNull
            nodes = found.children
        }

        return found?.rawValue?.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull
    }
}
