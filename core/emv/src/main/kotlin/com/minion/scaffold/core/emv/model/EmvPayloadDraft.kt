package com.minion.scaffold.core.emv.model

/**
 * Everything needed to write an EMV Merchant Presented Mode payload.
 *
 * Strings, not parsed types, because the caller is a form: a half-typed merchant category code is
 * not an `Int`, and refusing to model the intermediate state would push validation into the UI
 * where it could not be tested. Every field is checked by `BuildEmvPayloadUseCase`, which is the
 * single place that knows the specification's limits.
 *
 * Tags `00` (Payload Format Indicator) and `63` (CRC) are absent by design — neither is a choice.
 */
data class EmvPayloadDraft(
    val initiationMethod: PointOfInitiationMethod,
    val merchantAccounts: List<MerchantAccount>,
    val merchantCategoryCode: String,
    val currencyNumericCode: String,
    /** Required when [initiationMethod] is dynamic, and refused when it is static. */
    val amount: String?,
    val countryCode: String,
    val merchantName: String,
    val merchantCity: String,
    val postalCode: String?,
    /** Tags `55`–`57`. Null writes none of them. */
    val tip: TipSpec? = null,
    /**
     * Whole segments no field above represents, re-emitted unchanged.
     *
     * This is what makes editing a scanned payload safe. A real QRIS code carries things this
     * draft has no slot for — a flat merchant account at tag `04`, an additional data template at
     * tag `62` — and without somewhere to put them, changing a merchant's name would quietly
     * delete them. The result would still scan and still pass its checksum, which is precisely
     * what makes the loss dangerous.
     *
     * Tags `00` and `63` never appear here: both are written from scratch every time.
     */
    val passthrough: List<TlvNode> = emptyList(),
)

/**
 * One merchant account template — tag `26`–`51`.
 *
 * A QRIS payload typically carries two: the acquiring bank at tag `26` and the national switch at
 * tag `51`. Only the globally unique identifier is mandatory; the rest are omitted from the
 * payload when blank rather than written as empty segments.
 */
data class MerchantAccount(
    /** The tag this template occupies, `"26"`–`"51"`. */
    val tag: String,
    /** Subtag `00` — a reverse-domain identifier, e.g. `ID.CO.CIMBNIAGA.WWW`. */
    val globallyUniqueIdentifier: String,
    /**
     * Subtag `01` — the merchant's primary account number.
     *
     * Named for what it holds: in QRIS a tag `26` subtag `01` is a PAN carrying the scheme's
     * `9360` prefix, and it is a different value from the merchant identifier in subtag `02`.
     */
    val merchantPan: String?,
    /** Subtag `02` — the merchant identifier the acquirer or national switch assigned. */
    val merchantId: String?,
    /** Subtag `03` — the merchant size classification, e.g. `UMI`. */
    val merchantCriteria: String?,
    /** Subtags this template carries that none of the fields above represent. */
    val passthroughSubtags: List<TlvNode> = emptyList(),
)
