package com.minion.scaffold.core.emv.usecase

import com.minion.scaffold.core.emv.model.EmvBuildResult
import com.minion.scaffold.core.emv.model.EmvField
import com.minion.scaffold.core.emv.model.EmvPayloadDraft
import com.minion.scaffold.core.emv.model.FieldViolation
import com.minion.scaffold.core.emv.model.MerchantAccount
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.TipIndicator
import com.minion.scaffold.core.emv.model.TipSpec
import com.minion.scaffold.core.emv.model.ViolationReason
import com.minion.scaffold.core.emv.parser.EmvCrc16
import com.minion.scaffold.core.emv.parser.EmvTagCatalog
import javax.inject.Inject

/**
 * Writes an EMV Merchant Presented Mode payload from a [EmvPayloadDraft].
 *
 * The mirror of `ParseEmvPayloadUseCase`, and deliberately in the same module: they share the tag
 * catalog and — the part that matters — one implementation of the checksum. Two copies of a CRC
 * that drift apart produce payloads this app would reject as tampered, which is the exact bug the
 * scan tool exists to surface.
 *
 * Validation runs to completion before anything is written, so [EmvBuildResult.Invalid] carries
 * every problem rather than the first one found.
 *
 * Synchronous: the work is a handful of string concatenations and a pass over ~200 characters.
 */
class BuildEmvPayloadUseCase @Inject constructor() {

    /**
     * Validates [draft] and, if it is sound, writes the payload.
     *
     * @param draft The form inputs to validate and write.
     * @return [EmvBuildResult.Success] with the payload, or [EmvBuildResult.Invalid] listing
     *         every field that failed validation.
     */
    operator fun invoke(draft: EmvPayloadDraft): EmvBuildResult {
        val violations = draft.validate()
        return if (violations.isEmpty()) {
            EmvBuildResult.Success(draft.assemble())
        } else {
            EmvBuildResult.Invalid(violations)
        }
    }

    private fun EmvPayloadDraft.validate(): List<FieldViolation> = buildList {
        if (initiationMethod.code == null) {
            add(FieldViolation(EmvField.INITIATION_METHOD, ViolationReason.UNSUPPORTED))
        }

        if (merchantAccounts.isEmpty()) {
            add(FieldViolation(EmvField.MERCHANT_ACCOUNTS, ViolationReason.REQUIRED))
        }
        merchantAccounts.forEachIndexed { index, account -> addAll(account.violations(index)) }

        addAll(required(EmvField.MERCHANT_NAME, merchantName, MAX_MERCHANT_NAME))
        addAll(required(EmvField.MERCHANT_CITY, merchantCity, MAX_MERCHANT_CITY))
        addAll(fixedLength(EmvField.COUNTRY_CODE, countryCode, COUNTRY_CODE_LENGTH))
        addAll(fixedNumeric(EmvField.MERCHANT_CATEGORY_CODE, merchantCategoryCode, MCC_LENGTH))
        addAll(fixedNumeric(EmvField.TRANSACTION_CURRENCY, currencyNumericCode, CURRENCY_LENGTH))
        addAll(optional(EmvField.POSTAL_CODE, postalCode, MAX_POSTAL_CODE))
        addAll(amountViolations())
        addAll(tipViolations())
    }

    /**
     * The tip's companion value, when the chosen mode has one.
     *
     * [TipSpec.Prompt] has nothing to check — the payer's app decides the amount, which is the
     * point of it.
     */
    private fun EmvPayloadDraft.tipViolations(): List<FieldViolation> = when (val spec = tip) {
        null, TipSpec.Prompt -> emptyList()

        is TipSpec.FixedFee -> feeViolations(spec.amount, MAX_AMOUNT) { emptyList() }

        is TipSpec.PercentageFee -> feeViolations(spec.rate, MAX_PERCENTAGE_RATE) {
            // A rate above 100% is well-formed and meaningless; the pattern cannot catch it.
            val rate = it.toDoubleOrNull()
            if (rate != null && rate > MAX_PERCENTAGE) {
                listOf(FieldViolation(EmvField.CONVENIENCE_FEE, ViolationReason.OUT_OF_RANGE))
            } else {
                emptyList()
            }
        }
    }

    private fun feeViolations(
        value: String,
        maxLength: Int,
        extraChecks: (String) -> List<FieldViolation>,
    ): List<FieldViolation> {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() ->
                listOf(FieldViolation(EmvField.CONVENIENCE_FEE, ViolationReason.REQUIRED))

            trimmed.length > maxLength ->
                listOf(FieldViolation(EmvField.CONVENIENCE_FEE, ViolationReason.TOO_LONG))

            !AMOUNT_PATTERN.matches(trimmed) ->
                listOf(FieldViolation(EmvField.CONVENIENCE_FEE, ViolationReason.NOT_AN_AMOUNT))

            else -> extraChecks(trimmed)
        }
    }

    /**
     * An amount is mandatory on a dynamic payload and forbidden on a static one.
     *
     * A static code is a printed sticker reused for every customer; baking a price into one would
     * charge the next person the previous person's total.
     */
    private fun EmvPayloadDraft.amountViolations(): List<FieldViolation> {
        val value = amount?.trim().orEmpty()
        val dynamic = initiationMethod == PointOfInitiationMethod.DYNAMIC

        return when {
            dynamic && value.isEmpty() ->
                listOf(FieldViolation(EmvField.TRANSACTION_AMOUNT, ViolationReason.REQUIRED))

            !dynamic && value.isNotEmpty() ->
                listOf(FieldViolation(EmvField.TRANSACTION_AMOUNT, ViolationReason.NOT_ALLOWED))

            value.isEmpty() -> emptyList()

            value.length > MAX_AMOUNT ->
                listOf(FieldViolation(EmvField.TRANSACTION_AMOUNT, ViolationReason.TOO_LONG))

            !AMOUNT_PATTERN.matches(value) ->
                listOf(FieldViolation(EmvField.TRANSACTION_AMOUNT, ViolationReason.NOT_AN_AMOUNT))

            else -> emptyList()
        }
    }

    private fun MerchantAccount.violations(index: Int): List<FieldViolation> {
        val violations = mutableListOf<FieldViolation>()

        val numericTag = tag.toIntOrNull()
        if (numericTag == null || numericTag !in EmvTagCatalog.MERCHANT_ACCOUNT_TAGS) {
            violations += FieldViolation(EmvField.ACQUIRER_TAG, ViolationReason.UNSUPPORTED, index)
        }

        violations += required(
            EmvField.ACQUIRER_IDENTIFIER,
            globallyUniqueIdentifier,
            MAX_VALUE,
            index,
        )
        violations += optional(EmvField.ACQUIRER_MERCHANT_PAN, merchantPan, MAX_VALUE, index)
        violations += optional(EmvField.ACQUIRER_MERCHANT_ID, merchantId, MAX_VALUE, index)
        violations += optional(EmvField.ACQUIRER_CRITERIA, merchantCriteria, MAX_VALUE, index)

        // The subtags are concatenated into the template's own value, which is itself length-
        // prefixed by two digits — so three individually legal subtags can still overflow the
        // template holding them. Checked only when the subtags are otherwise sound, so the report
        // names the real problem instead of adding a second, derived complaint.
        if (violations.isEmpty() && templateValue().length > MAX_VALUE) {
            violations += FieldViolation(
                EmvField.ACQUIRER_IDENTIFIER,
                ViolationReason.TOO_LONG,
                index,
            )
        }

        return violations
    }

    private fun required(
        field: EmvField,
        value: String,
        maxLength: Int,
        accountIndex: Int? = null,
    ): List<FieldViolation> = when {
        value.isBlank() -> listOf(FieldViolation(field, ViolationReason.REQUIRED, accountIndex))
        value.length > maxLength ->
            listOf(FieldViolation(field, ViolationReason.TOO_LONG, accountIndex))

        else -> emptyList()
    }

    private fun optional(
        field: EmvField,
        value: String?,
        maxLength: Int,
        accountIndex: Int? = null,
    ): List<FieldViolation> =
        if (!value.isNullOrBlank() && value.length > maxLength) {
            listOf(FieldViolation(field, ViolationReason.TOO_LONG, accountIndex))
        } else {
            emptyList()
        }

    private fun fixedLength(field: EmvField, value: String, length: Int): List<FieldViolation> =
        when {
            value.isBlank() -> listOf(FieldViolation(field, ViolationReason.REQUIRED))
            value.length != length -> listOf(FieldViolation(field, ViolationReason.WRONG_LENGTH))
            else -> emptyList()
        }

    private fun fixedNumeric(field: EmvField, value: String, length: Int): List<FieldViolation> =
        fixedLength(field, value, length).ifEmpty {
            if (value.all(Char::isDigit)) {
                emptyList()
            } else {
                listOf(FieldViolation(field, ViolationReason.NOT_NUMERIC))
            }
        }

    /**
     * Writes every segment in ascending tag order, then the checksum over everything including its
     * own `6304` header.
     *
     * Collected then sorted, rather than appended in a fixed sequence, because passthrough
     * segments can carry any tag — a flat merchant account at `04` has to land between `01` and
     * `26`, and interleaving it by hand would mean a special case per tag the form does not own.
     *
     * Optional fields left blank are omitted rather than written as zero-length segments — a
     * `6100` in the payload says "this merchant has an empty postal code", which is a different
     * claim from not having one.
     */
    private fun EmvPayloadDraft.assemble(): String {
        // Non-null by validation: a draft with an unwritable initiation method never reaches here.
        val initiationCode = requireNotNull(initiationMethod.code)

        val segments = buildList {
            add(
                EmvTagCatalog.TAG_PAYLOAD_FORMAT_INDICATOR to
                    EmvTagCatalog.PAYLOAD_FORMAT_VERSION,
            )
            add(EmvTagCatalog.TAG_POINT_OF_INITIATION_METHOD to initiationCode)
            merchantAccounts.forEach { add(it.tag to it.templateValue()) }
            add(EmvTagCatalog.TAG_MERCHANT_CATEGORY_CODE to merchantCategoryCode)
            add(EmvTagCatalog.TAG_TRANSACTION_CURRENCY to currencyNumericCode)
            amount.nonBlank()?.let { add(EmvTagCatalog.TAG_TRANSACTION_AMOUNT to it) }
            addAll(tipSegments())
            add(EmvTagCatalog.TAG_COUNTRY_CODE to countryCode)
            add(EmvTagCatalog.TAG_MERCHANT_NAME to merchantName.trim())
            add(EmvTagCatalog.TAG_MERCHANT_CITY to merchantCity.trim())
            postalCode.nonBlank()?.let { add(EmvTagCatalog.TAG_POSTAL_CODE to it) }
            passthrough.forEach { add(it.tag to it.rawValue) }
        }

        return buildString {
            segments.sortedByTag().forEach { (tag, value) -> append(tlv(tag, value)) }

            // The checksum covers its own tag and length, so they go in before it is computed.
            append(EmvTagCatalog.TAG_CRC)
            append(EmvTagCatalog.CRC_VALUE_LENGTH.toString().padStart(LENGTH_DIGITS, '0'))
            append(EmvCrc16.compute(toString()))
        }
    }

    /**
     * Ascending numeric order, stable.
     *
     * A non-numeric tag cannot reach here — the parser rejects one — but sorting it last rather
     * than throwing keeps this total.
     */
    private fun List<Pair<String, String>>.sortedByTag(): List<Pair<String, String>> =
        sortedBy { (tag, _) -> tag.toIntOrNull() ?: Int.MAX_VALUE }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Tag `55`, plus whichever companion tag the mode requires.
     *
     * The indicator and its value are written together here rather than as two independent
     * fields, so a payload can never claim a percentage fee and then carry a fixed one — the
     * sealed [TipSpec] makes that unrepresentable and this keeps it that way on the way out.
     */
    private fun EmvPayloadDraft.tipSegments(): List<Pair<String, String>> = when (val spec = tip) {
        null -> emptyList()

        TipSpec.Prompt -> listOf(
            EmvTagCatalog.TAG_TIP_INDICATOR to TipIndicator.PROMPT.requireCode(),
        )

        is TipSpec.FixedFee -> listOf(
            EmvTagCatalog.TAG_TIP_INDICATOR to TipIndicator.FIXED_FEE.requireCode(),
            EmvTagCatalog.TAG_CONVENIENCE_FEE_FIXED to spec.amount.trim(),
        )

        is TipSpec.PercentageFee -> listOf(
            EmvTagCatalog.TAG_TIP_INDICATOR to TipIndicator.PERCENTAGE_FEE.requireCode(),
            EmvTagCatalog.TAG_CONVENIENCE_FEE_PERCENTAGE to spec.rate.trim(),
        )
    }

    /** Non-null for every indicator a [TipSpec] can name; only `UNKNOWN` has no code. */
    private fun TipIndicator.requireCode(): String = requireNotNull(code)

    /** The template's subtags, in ascending order, with anything unrecognised carried through. */
    private fun MerchantAccount.templateValue(): String {
        val subtags = buildList {
            add(
                EmvTagCatalog.SUBTAG_GLOBALLY_UNIQUE_IDENTIFIER to
                    globallyUniqueIdentifier.trim(),
            )
            merchantPan.nonBlank()?.let { add(EmvTagCatalog.SUBTAG_MERCHANT_PAN to it) }
            merchantId.nonBlank()?.let { add(EmvTagCatalog.SUBTAG_MERCHANT_ID to it) }
            merchantCriteria.nonBlank()?.let { add(EmvTagCatalog.SUBTAG_MERCHANT_CRITERIA to it) }
            passthroughSubtags.forEach { add(it.tag to it.rawValue) }
        }

        return subtags.sortedByTag().joinToString(separator = "") { (tag, value) ->
            tlv(tag, value)
        }
    }

    /**
     * `tag + two-digit length + value`.
     *
     * `padStart`, not `String.format`: the length is decimal and a locale that renders digits in
     * its own numerals would produce a payload nothing can read.
     */
    private fun tlv(tag: String, value: String): String =
        tag + value.length.toString().padStart(LENGTH_DIGITS, '0') + value

    private companion object {
        const val LENGTH_DIGITS = 2
        const val MAX_VALUE = EmvTagCatalog.MAX_VALUE_LENGTH

        // Field limits from the EMVCo Merchant Presented Mode specification.
        const val MAX_MERCHANT_NAME = 25
        const val MAX_MERCHANT_CITY = 15
        const val MAX_POSTAL_CODE = 10
        const val MAX_AMOUNT = 13
        const val MAX_PERCENTAGE_RATE = 5
        const val MAX_PERCENTAGE = 100.0
        const val COUNTRY_CODE_LENGTH = 2
        const val MCC_LENGTH = 4
        const val CURRENCY_LENGTH = 3

        /** Digits, optionally followed by a point and one or two more. No sign, no exponent. */
        val AMOUNT_PATTERN = Regex("""\d+(\.\d{1,2})?""")
    }
}
