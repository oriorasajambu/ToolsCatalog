package com.minion.scaffold.feature.qrcreate.presentation.form

import com.minion.scaffold.core.emv.model.EmvField
import com.minion.scaffold.core.emv.model.EmvPayloadDraft
import com.minion.scaffold.core.emv.model.MerchantAccount
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.TipSpec
import com.minion.scaffold.core.emv.model.TlvNode
import com.minion.scaffold.core.emv.reference.Currency
import com.minion.scaffold.core.emv.reference.CurrencyCodes
import com.minion.scaffold.core.emv.reference.MerchantCategory
import com.minion.scaffold.core.emv.reference.MerchantCategoryCodes

/**
 * What the form currently holds.
 *
 * Text fields are `String`, not parsed types: a half-typed postal code is not a number, and
 * modeling it as one would force the UI to reject keystrokes rather than let the builder explain
 * what is wrong. The picked currency and category *are* typed, because a picker cannot produce
 * anything else.
 */
internal data class EmvFormState(
    val initiationMethod: PointOfInitiationMethod = PointOfInitiationMethod.DYNAMIC,
    val merchantName: String = "",
    val merchantCity: String = "",
    val countryCode: String = DEFAULT_COUNTRY_CODE,
    val postalCode: String = "",
    val amount: String = "",
    val tipMode: TipMode = TipMode.NONE,
    /** The fixed fee or the percentage rate, depending on [tipMode]. Raw, like every input. */
    val tipValue: String = "",
    val currency: Currency? = CurrencyCodes.of(DEFAULT_CURRENCY_CODE),
    val merchantCategory: MerchantCategory? = null,
    val accounts: List<AccountFormState> = listOf(AccountFormState(tag = ACQUIRER_TAG)),
    /**
     * Segments carried over from a scanned payload that no field here shows.
     *
     * Invisible but not inert: they go back out on generate. Editing a merchant's name must not
     * quietly delete the tags this form has no box for.
     */
    val passthrough: List<TlvNode> = emptyList(),
) {

    /** Returns a copy with [field] set to [value]. Unknown fields are returned unchanged. */
    fun withField(field: EmvField, value: String): EmvFormState = when (field) {
        EmvField.MERCHANT_NAME -> copy(merchantName = value)
        EmvField.MERCHANT_CITY -> copy(merchantCity = value)
        EmvField.COUNTRY_CODE -> copy(countryCode = value)
        EmvField.POSTAL_CODE -> copy(postalCode = value)
        EmvField.TRANSACTION_AMOUNT -> copy(amount = value)
        EmvField.CONVENIENCE_FEE -> copy(tipValue = value)
        else -> this
    }

    fun withAccountField(index: Int, field: EmvField, value: String): EmvFormState = copy(
        accounts = accounts.mapIndexed { position, account ->
            if (position == index) account.withField(field, value) else account
        },
    )

    companion object {
        const val DEFAULT_COUNTRY_CODE = "ID"
        const val DEFAULT_CURRENCY_CODE = "360"

        /** Tag 26 — the acquiring bank, the one template every payload carries. */
        const val ACQUIRER_TAG = "26"

        /** Tag 51 — the national switch, the conventional second template on a QRIS payload. */
        const val NATIONAL_SWITCH_TAG = "51"
    }
}

/**
 * The tip choice as the form models it.
 *
 * A flat enum rather than `TipSpec?`, because the form has to hold a mode the user has selected
 * while the value box beside it is still empty — a state `TipSpec` deliberately cannot represent.
 * [toDraft] is where the two become one again, and where an empty box turns into a violation.
 */
internal enum class TipMode {
    NONE,
    PROMPT,
    FIXED_FEE,
    PERCENTAGE_FEE,
    ;

    /** Whether this mode needs a companion value in tag `56` or `57`. */
    val takesValue: Boolean get() = this == FIXED_FEE || this == PERCENTAGE_FEE
}

internal data class AccountFormState(
    val tag: String,
    val identifier: String = "",
    val merchantPan: String = "",
    val merchantId: String = "",
    val criteria: String = "",
    val passthroughSubtag: List<TlvNode> = emptyList(),
) {
    fun withField(field: EmvField, value: String): AccountFormState = when (field) {
        EmvField.ACQUIRER_IDENTIFIER -> copy(identifier = value)
        EmvField.ACQUIRER_MERCHANT_PAN -> copy(merchantPan = value)
        EmvField.ACQUIRER_MERCHANT_ID -> copy(merchantId = value)
        EmvField.ACQUIRER_CRITERIA -> copy(criteria = value)
        else -> this
    }
}

/**
 * The form as the builder wants it.
 *
 * Blank optionals become null rather than empty strings, so the builder omits the segment instead
 * of writing a zero-length one — `6100` claims the merchant has an empty postal code, which is a
 * different statement from not having one.
 *
 * An unpicked category becomes an empty code, which the builder reports as `REQUIRED`. That keeps
 * "you have not chosen yet" in the same channel as every other validation failure, rather than as
 * a separate nullability case the UI would have to handle on its own.
 */
internal fun EmvFormState.toDraft(): EmvPayloadDraft = EmvPayloadDraft(
    initiationMethod = initiationMethod,
    merchantAccounts = accounts.map { account ->
        MerchantAccount(
            tag = account.tag,
            globallyUniqueIdentifier = account.identifier,
            merchantPan = account.merchantPan.ifBlank { null },
            merchantId = account.merchantId.ifBlank { null },
            merchantCriteria = account.criteria.ifBlank { null },
            passthroughSubtags = account.passthroughSubtag,
        )
    },
    merchantCategoryCode = merchantCategory?.code.orEmpty(),
    currencyNumericCode = currency?.numericCode.orEmpty(),
    amount = amount.ifBlank { null },
    countryCode = countryCode,
    merchantName = merchantName,
    merchantCity = merchantCity,
    passthrough = passthrough,
    postalCode = postalCode.ifBlank { null },
    tip = when (tipMode) {
        TipMode.NONE -> null
        TipMode.PROMPT -> TipSpec.Prompt
        // The raw box goes across even when empty, so the builder reports the missing value as a
        // violation on CONVENIENCE_FEE rather than this mapping quietly dropping the whole mode.
        TipMode.FIXED_FEE -> TipSpec.FixedFee(tipValue)
        TipMode.PERCENTAGE_FEE -> TipSpec.PercentageFee(tipValue)
    },
)

/**
 * The inverse of [toDraft] — a scanned payload, opened for editing.
 *
 * A currency or category the reference tables do not carry leaves its picker unselected rather
 * than being substituted for something close. The user then has to choose one, which is honest:
 * the form cannot show a value it has no entry for, and silently swapping it would change the
 * payload without anyone being told.
 */
internal fun EmvPayloadDraft.toFormState(): EmvFormState = EmvFormState(
    initiationMethod = initiationMethod,
    merchantName = merchantName,
    merchantCity = merchantCity,
    countryCode = countryCode,
    postalCode = postalCode.orEmpty(),
    amount = amount.orEmpty(),
    tipMode = tip.toTipMode(),
    tipValue = tip.toTipValue(),
    currency = CurrencyCodes.of(currencyNumericCode),
    merchantCategory = MerchantCategoryCodes.nameOf(merchantCategoryCode)
        ?.let { MerchantCategory(merchantCategoryCode, it) },
    accounts = merchantAccounts.map { account ->
        AccountFormState(
            tag = account.tag,
            identifier = account.globallyUniqueIdentifier,
            merchantPan = account.merchantPan.orEmpty(),
            merchantId = account.merchantId.orEmpty(),
            criteria = account.merchantCriteria.orEmpty(),
            passthroughSubtag = account.passthroughSubtags,
        )
    },
    passthrough = passthrough,
)

private fun TipSpec?.toTipMode(): TipMode = when (this) {
    null -> TipMode.NONE
    TipSpec.Prompt -> TipMode.PROMPT
    is TipSpec.FixedFee -> TipMode.FIXED_FEE
    is TipSpec.PercentageFee -> TipMode.PERCENTAGE_FEE
}

private fun TipSpec?.toTipValue(): String = when (this) {
    is TipSpec.FixedFee -> amount
    is TipSpec.PercentageFee -> rate
    null, TipSpec.Prompt -> ""
}
