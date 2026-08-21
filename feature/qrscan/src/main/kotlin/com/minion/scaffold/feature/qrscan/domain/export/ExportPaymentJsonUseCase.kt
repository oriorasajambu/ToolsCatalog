package com.minion.scaffold.feature.qrscan.domain.export

import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.EmvPayloadDraft
import com.minion.scaffold.core.emv.model.MerchantAccount
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.model.TipSpec
import com.minion.scaffold.core.emv.reference.CurrencyCodes
import com.minion.scaffold.core.emv.usecase.EmvDraftFromPayloadUseCase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * A scanned payment code as the response a payment backend would have returned for it.
 *
 * Built on the draft rather than on the raw segments: [EmvDraftFromPayloadUseCase] already pulls
 * the merchant account templates, the tip mode and the amount out of the TLV tree, and its
 * definition of an account template — a tag in `26`–`51` that actually nests — picks up a domestic
 * code's tags `26` and `51` and a cross-border code's tag `32`, while correctly ignoring a flat
 * identifier at tag `04`. Re-deriving any of that here would be a second implementation of rules
 * that are already tested.
 *
 * ## What this document is not
 *
 * It is **not** a lossless rendering of the code. Tags the contract has no field for — the
 * additional data template, a merchant's size classification, an account expiry — are dropped, and
 * the text report stays the complete reading. Going the other way, a third of the contract is
 * issuer state no QR carries and comes from [PaymentResponseFields]'s sample constants. Both
 * directions are deliberate; that file explains the cost.
 */
internal class ExportPaymentJsonUseCase @Inject constructor(
    private val draftFromPayload: EmvDraftFromPayloadUseCase,
) {

    /**
     * The response document for [report], pretty-printed.
     *
     * @param report An already-decoded payment code.
     * @return The JSON, or null when the payload will not read back as a draft — unreachable from
     *         a report on screen, which by definition already parsed. Returned rather than thrown
     *         so that an export can never take the screen down.
     */
    operator fun invoke(report: QrInquiryReport): String? {
        val draft = when (val result = draftFromPayload(report.payload)) {
            is EmvParseResult.Success -> result.value
            is EmvParseResult.Failure -> return null
        }

        return json.encodeToString(JsonObject.serializer(), draft.toResponse())
    }

    private fun EmvPayloadDraft.toResponse(): JsonObject = buildJsonObject {
        put(PaymentResponseFields.TRANSACTION_ID, PaymentResponseFields.SAMPLE_TRANSACTION_ID)
        put(PaymentResponseFields.TRAN_CODE, PaymentResponseFields.SAMPLE_TRAN_CODE)
        put(PaymentResponseFields.QR_TYPE, schemeType())

        put(
            PaymentResponseFields.QR_DATA,
            buildJsonObject {
                put(PaymentResponseFields.QR_TYPE, initiationType())
                put(PaymentResponseFields.TRAN_MERCHANT_DATA, merchantData())
            },
        )

        put(PaymentResponseFields.QR_TEMPLATE_TYPE, PaymentResponseFields.SAMPLE_QR_TEMPLATE_TYPE)
        put(PaymentResponseFields.LIMIT, sampleLimit())

        put(
            PaymentResponseFields.SOF_ALLOWED,
            buildJsonArray {
                for (sourceOfFunds in PaymentResponseFields.SAMPLE_SOF_ALLOWED) add(sourceOfFunds)
            },
        )

        put(
            PaymentResponseFields.CURRENCY_ALLOWED,
            buildJsonArray {
                // Derived rather than sampled. The payload does say which currency it is priced
                // in, and an invented list sitting beside a real one is the more confusing of the
                // two options. Empty when the code carries no tag 53 at all.
                currencyCode()?.let { add(it) }
            },
        )

        put(PaymentResponseFields.BANKED_STATUS, PaymentResponseFields.SAMPLE_BANKED_STATUS)
    }

    private fun EmvPayloadDraft.merchantData(): JsonObject {
        val primary = primaryAccount()
        val currency = currencyCode()

        return buildJsonObject {
            put(PaymentResponseFields.TIPS, tipValue())
            put(PaymentResponseFields.TIPS_TYPE, tipType())
            put(PaymentResponseFields.TIPS_CURRENCY, currency)
            put(PaymentResponseFields.MERCHANT_NAME, merchantName.orNullIfBlank())
            put(PaymentResponseFields.MERCHANT_COUNTRY_CODE, countryCode.orNullIfBlank())
            put(PaymentResponseFields.MERCHANT_CITY, merchantCity.orNullIfBlank())
            put(PaymentResponseFields.MERCHANT_POSTAL_CODE, postalCode.orNullIfBlank())
            put(
                PaymentResponseFields.MERCHANT_PAYMENT_PROVIDER_NAME,
                primary?.globallyUniqueIdentifier.orNullIfBlank(),
            )
            put(PaymentResponseFields.MERCHANT_PAN, primary?.merchantPan.orNullIfBlank())
            // Raw, never reformatted: a payments figure that goes through a normaliser is one
            // trailing zero away from being wrong by a factor of ten.
            put(PaymentResponseFields.AMOUNT, amount.orNullIfBlank())
            put(PaymentResponseFields.AMOUNT_TYPE, amountType())
            put(PaymentResponseFields.AMOUNT_CURRENCY, currency)
            put(PaymentResponseFields.DYNAMIC_QR, dynamicQr(currency))
            put(PaymentResponseFields.MERCHANT_ID, primary?.merchantId.orNullIfBlank())
            put(PaymentResponseFields.MERCHANT_ID_NATIONAL, nationalMerchantId().orNullIfBlank())
            put(
                PaymentResponseFields.IS_AMOUNT_ALLOWED_DECIMAL,
                PaymentResponseFields.SAMPLE_IS_AMOUNT_ALLOWED_DECIMAL,
            )
        }
    }

    private fun EmvPayloadDraft.dynamicQr(currency: String?): JsonObject = buildJsonObject {
        put(PaymentResponseFields.PAYMENT_TYPE, PaymentResponseFields.SAMPLE_PAYMENT_TYPE)
        put(PaymentResponseFields.TOTAL_PAYMENT, totalPayment())
        put(PaymentResponseFields.TOTAL_PAYMENT_CURRENCY, currency)
    }

    private fun sampleLimit(): JsonObject = buildJsonObject {
        put(PaymentResponseFields.LIMIT_PER_DAILY, PaymentResponseFields.SAMPLE_LIMIT_PER_DAILY)
        put(
            PaymentResponseFields.REMAINING_LIMIT_PER_DAILY,
            PaymentResponseFields.SAMPLE_REMAINING_LIMIT_PER_DAILY,
        )
        put(
            PaymentResponseFields.LIMIT_PER_TRANSACTION,
            PaymentResponseFields.SAMPLE_LIMIT_PER_TRANSACTION,
        )
    }

    /**
     * Domestic or cross-border.
     *
     * Both halves are required. Country alone would call an Indonesian merchant priced in Singapore
     * dollars domestic, and currency alone would say the same of a Malaysian merchant priced in
     * rupiah.
     */
    private fun EmvPayloadDraft.schemeType(): String = if (
        countryCode == PaymentResponseFields.COUNTRY_INDONESIA &&
        currencyNumericCode == PaymentResponseFields.CURRENCY_INDONESIA
    ) {
        PaymentResponseFields.QR_TYPE_QRIS
    } else {
        PaymentResponseFields.QR_TYPE_CROSS_BORDER
    }

    private fun EmvPayloadDraft.initiationType(): String =
        if (initiationMethod == PointOfInitiationMethod.DYNAMIC) {
            PaymentResponseFields.QR_DATA_TYPE_DYNAMIC
        } else {
            PaymentResponseFields.QR_DATA_TYPE_STATIC
        }

    /**
     * Whether the payer may choose the amount.
     *
     * Read from tag `01`, **not** from whether tag `54` is present. That is a deliberate choice and
     * it has a visible consequence: a dynamic code that omits its amount exports
     * `"amount_type": "FIXED_AMOUNT"` beside `"amount": null`, and such codes do occur. Changing
     * the rule is changing this one expression.
     */
    private fun EmvPayloadDraft.amountType(): String =
        if (initiationMethod == PointOfInitiationMethod.DYNAMIC) {
            PaymentResponseFields.AMOUNT_TYPE_FIXED
        } else {
            PaymentResponseFields.AMOUNT_TYPE_INPUT
        }

    /**
     * The tip figure as the payload wrote it.
     *
     * For a percentage this is a **rate**, not money — `"5"` meaning five percent. The consumer
     * reads `tips_type` to know which it is holding, because converting the rate into an amount
     * here would assert a figure the merchant never encoded.
     */
    private fun EmvPayloadDraft.tipValue(): String? = when (val spec = tip) {
        is TipSpec.FixedFee -> spec.amount.orNullIfBlank()
        is TipSpec.PercentageFee -> spec.rate.orNullIfBlank()
        TipSpec.Prompt, null -> null
    }

    /**
     * Which tip mode the code is in.
     *
     * A tag `55` carrying a code the specification does not define already reaches here as a null
     * [TipSpec] — the draft mapper drops it rather than guessing — so it lands on `NO_TIPS` with no
     * branch of its own.
     */
    private fun EmvPayloadDraft.tipType(): String = when (tip) {
        is TipSpec.FixedFee -> PaymentResponseFields.TIPS_TYPE_FIXED
        is TipSpec.PercentageFee -> PaymentResponseFields.TIPS_TYPE_PERCENTAGE
        TipSpec.Prompt -> PaymentResponseFields.TIPS_TYPE_INPUT
        null -> PaymentResponseFields.TIPS_TYPE_NONE
    }

    /**
     * What the payer will actually be charged, when that is knowable.
     *
     * Null unless a tip both exists and is a figure: with no tip there is no total distinct from
     * the amount, and with a prompt the payer has not chosen yet. Also null when the amount will
     * not parse as a number, which a damaged payload can produce — an export must not throw.
     */
    private fun EmvPayloadDraft.totalPayment(): String? {
        val base = amount?.trim()?.toBigDecimalOrNull() ?: return null

        val total = when (val spec = tip) {
            is TipSpec.FixedFee -> spec.amount.trim().toBigDecimalOrNull()?.let(base::add)

            is TipSpec.PercentageFee -> spec.rate.trim().toBigDecimalOrNull()?.let { rate ->
                // Rounded at the amount's own scale, so a total keeps the precision the merchant
                // priced in rather than inheriting whatever the multiplication produced.
                base.add(
                    base.multiply(rate).divide(HUNDRED, base.scale(), RoundingMode.HALF_UP),
                )
            }

            TipSpec.Prompt, null -> null
        }

        return total?.toPlainString()
    }

    /**
     * The acquiring account — the one that carries a primary account number in subtag `01`.
     *
     * Identity rather than position. A domestic code's acquirer template has a subtag `01` and its
     * national switch template does not, so the two separate themselves; a cross-border code has a
     * single template that does. Keying on the tag number instead would hard-code an Indonesian
     * layout into a mapper that also has to serve Saudi codes, and keying on the reverse-domain
     * identifier would need `.CO.`, `.OR.` and `.GOV.` to mean something consistent, which across
     * those two schemes they do not.
     */
    private fun EmvPayloadDraft.primaryAccount(): MerchantAccount? =
        merchantAccounts.firstOrNull { it.merchantPan != null }

    /**
     * The national switch's merchant identifier, when the code routes through one.
     *
     * Any template other than the primary that names an identifier. Null for a cross-border code,
     * which has only the one account.
     */
    private fun EmvPayloadDraft.nationalMerchantId(): String? {
        val primaryIndex = merchantAccounts.indexOfFirst { it.merchantPan != null }

        return merchantAccounts
            .filterIndexed { index, _ -> index != primaryIndex }
            .firstOrNull { !it.merchantId.isNullOrBlank() }
            ?.merchantId
    }

    /** The alphabetic currency code, falling back to the raw number for one that is not listed. */
    private fun EmvPayloadDraft.currencyCode(): String? = currencyNumericCode
        .takeIf { it.isNotBlank() }
        ?.let { CurrencyCodes.of(it)?.alphaCode ?: it }

    /**
     * Blank becomes null.
     *
     * A field the code did not carry reads better as `null` than as `""` — the first says the QR
     * was silent, the second says it said nothing, and only one of those is true.
     */
    private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    private companion object {

        /**
         * Indented, because the document is read or pasted into an editor long before anything
         * consumes it. `JsonObject` is insertion-ordered, so the keys come out in the contract's
         * own order rather than sorted.
         */
        val json = Json { prettyPrint = true }

        val HUNDRED: BigDecimal = BigDecimal(100)
    }
}
