package com.minion.scaffold.core.emv.usecase

import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.EmvPayloadDraft
import com.minion.scaffold.core.emv.model.MerchantAccount
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.model.TipIndicator
import com.minion.scaffold.core.emv.model.TipSpec
import com.minion.scaffold.core.emv.model.TlvNode
import com.minion.scaffold.core.emv.parser.EmvTagCatalog
import javax.inject.Inject

/**
 * Reads a payload back into the draft that would write it.
 *
 * The inverse of [BuildEmvPayloadUseCase], and the reason editing a scanned code is safe: anything
 * the draft has no field for is carried into [EmvPayloadDraft.passthrough] rather than dropped.
 * A real QRIS code has several such tags, so a mapper that only kept what it recognised would let
 * someone change a merchant's name and silently delete the identifier that routes the money.
 *
 * `build(fromPayload(p)) == p` for any payload this app can parse. That equality is the contract,
 * and it is what the round-trip test asserts.
 */
class EmvDraftFromPayloadUseCase @Inject constructor(
    private val parseEmvPayload: ParseEmvPayloadUseCase,
) {

    operator fun invoke(payload: String): EmvParseResult<EmvPayloadDraft> =
        when (val parsed = parseEmvPayload(payload)) {
            is EmvParseResult.Failure -> parsed
            is EmvParseResult.Success -> EmvParseResult.Success(parsed.value.toDraft())
        }

    private fun QrInquiryReport.toDraft(): EmvPayloadDraft {
        val nodes = segments.map { it.node }
        val byTag = nodes.associateBy { it.tag }

        val accounts = nodes.filter { it.isMerchantAccountTemplate() }
        // Derived from what was actually mapped, not from the tag range. A tag inside 26–51 that
        // carries a flat value is not an account here, and deciding "represented" by range would
        // drop it from both the accounts and the passthrough.
        val consumed = REPRESENTED_TAGS + accounts.map { it.tag }

        return EmvPayloadDraft(
            initiationMethod = byTag[EmvTagCatalog.TAG_POINT_OF_INITIATION_METHOD]
                ?.let { PointOfInitiationMethod.fromCode(it.rawValue) }
                ?: PointOfInitiationMethod.STATIC,
            merchantAccounts = accounts.map { it.toMerchantAccount() },
            merchantCategoryCode = byTag.valueOf(EmvTagCatalog.TAG_MERCHANT_CATEGORY_CODE),
            currencyNumericCode = byTag.valueOf(EmvTagCatalog.TAG_TRANSACTION_CURRENCY),
            amount = byTag[EmvTagCatalog.TAG_TRANSACTION_AMOUNT]?.rawValue,
            countryCode = byTag.valueOf(EmvTagCatalog.TAG_COUNTRY_CODE),
            merchantName = byTag.valueOf(EmvTagCatalog.TAG_MERCHANT_NAME),
            merchantCity = byTag.valueOf(EmvTagCatalog.TAG_MERCHANT_CITY),
            postalCode = byTag[EmvTagCatalog.TAG_POSTAL_CODE]?.rawValue,
            tip = byTag.toTipSpec(),
            passthrough = nodes.filterNot { it.tag in consumed },
        )
    }

    /**
     * Tag `55` and whichever fee tag it points at.
     *
     * An indicator this app does not recognise produces no [TipSpec] — the tag then falls through
     * to passthrough and survives the edit untouched, which is better than guessing at a mode and
     * rewriting it as something else.
     */
    private fun Map<String, TlvNode>.toTipSpec(): TipSpec? {
        val indicator = this[EmvTagCatalog.TAG_TIP_INDICATOR]?.rawValue
            ?.let(TipIndicator::fromCode)

        return when (indicator) {
            TipIndicator.PROMPT -> TipSpec.Prompt
            TipIndicator.FIXED_FEE -> TipSpec.FixedFee(
                valueOf(EmvTagCatalog.TAG_CONVENIENCE_FEE_FIXED),
            )

            TipIndicator.PERCENTAGE_FEE -> TipSpec.PercentageFee(
                valueOf(EmvTagCatalog.TAG_CONVENIENCE_FEE_PERCENTAGE),
            )

            TipIndicator.UNKNOWN, null -> null
        }
    }

    private fun TlvNode.toMerchantAccount(): MerchantAccount {
        val bySubtag = children.associateBy { it.tag }

        return MerchantAccount(
            tag = tag,
            globallyUniqueIdentifier = bySubtag
                .valueOf(EmvTagCatalog.SUBTAG_GLOBALLY_UNIQUE_IDENTIFIER),
            merchantPan = bySubtag[EmvTagCatalog.SUBTAG_MERCHANT_PAN]?.rawValue,
            merchantId = bySubtag[EmvTagCatalog.SUBTAG_MERCHANT_ID]?.rawValue,
            merchantCriteria = bySubtag[EmvTagCatalog.SUBTAG_MERCHANT_CRITERIA]?.rawValue,
            passthroughSubtags = children.filterNot { it.tag in REPRESENTED_SUBTAGS },
        )
    }

    /**
     * A merchant account *template* — one whose value the parser broke into subtags.
     *
     * The children check is what separates tag `26` in the `26`–`51` range, which nests, from a
     * flat identifier at tag `04`. The flat one has no subtags to map onto fields, so it goes
     * through passthrough intact rather than being forced into a shape it does not have.
     */
    private fun TlvNode.isMerchantAccountTemplate(): Boolean {
        val numericTag = tag.toIntOrNull() ?: return false
        return numericTag in EmvTagCatalog.MERCHANT_ACCOUNT_TAGS && children.isNotEmpty()
    }

    private fun Map<String, TlvNode>.valueOf(tag: String): String = this[tag]?.rawValue.orEmpty()

    private companion object {
        /**
         * Tags the draft's own fields write.
         *
         * `00` and `63` are here so they are never carried through: the format indicator is a
         * constant and the checksum is recomputed, so re-emitting a scanned one would either be
         * redundant or actively wrong.
         */
        val REPRESENTED_TAGS = setOf(
            EmvTagCatalog.TAG_PAYLOAD_FORMAT_INDICATOR,
            EmvTagCatalog.TAG_POINT_OF_INITIATION_METHOD,
            EmvTagCatalog.TAG_MERCHANT_CATEGORY_CODE,
            EmvTagCatalog.TAG_TRANSACTION_CURRENCY,
            EmvTagCatalog.TAG_TRANSACTION_AMOUNT,
            EmvTagCatalog.TAG_TIP_INDICATOR,
            EmvTagCatalog.TAG_CONVENIENCE_FEE_FIXED,
            EmvTagCatalog.TAG_CONVENIENCE_FEE_PERCENTAGE,
            EmvTagCatalog.TAG_COUNTRY_CODE,
            EmvTagCatalog.TAG_MERCHANT_NAME,
            EmvTagCatalog.TAG_MERCHANT_CITY,
            EmvTagCatalog.TAG_POSTAL_CODE,
            EmvTagCatalog.TAG_CRC,
        )

        val REPRESENTED_SUBTAGS = setOf(
            EmvTagCatalog.SUBTAG_GLOBALLY_UNIQUE_IDENTIFIER,
            EmvTagCatalog.SUBTAG_MERCHANT_PAN,
            EmvTagCatalog.SUBTAG_MERCHANT_ID,
            EmvTagCatalog.SUBTAG_MERCHANT_CRITERIA,
        )
    }
}
