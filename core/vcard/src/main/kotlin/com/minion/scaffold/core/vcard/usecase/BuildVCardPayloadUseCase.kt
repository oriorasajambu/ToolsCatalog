package com.minion.scaffold.core.vcard.usecase

import com.minion.scaffold.core.vcard.format.VCardFormat
import com.minion.scaffold.core.vcard.format.VCardValueCodec
import com.minion.scaffold.core.vcard.model.ContactCard
import com.minion.scaffold.core.vcard.model.VCardBuildResult
import com.minion.scaffold.core.vcard.model.VCardProperty
import com.minion.scaffold.core.vcard.model.VCardField
import com.minion.scaffold.core.vcard.model.VCardViolation
import com.minion.scaffold.core.vcard.model.VCardViolationReason
import javax.inject.Inject

/**
 * Writes a vCard 3.0 payload.
 *
 * Always emits the canonical form — `BEGIN`, `VERSION`, the known properties in a fixed order, then
 * anything carried through, then `END` — even though [ParseVCardPayloadUseCase] accepts properties
 * in any order. Being liberal in what is read and strict in what is written is what makes the
 * round-trip test meaningful; if writing varied too, it would only prove the two halves agree with
 * each other.
 *
 * **Written unfolded.** The spec wraps content lines past 75 octets, but folding a QR payload only
 * lengthens it and makes the code denser to no benefit — every reader tested accepts long lines.
 * Reading still unfolds, because other generators do fold.
 */
class BuildVCardPayloadUseCase @Inject constructor() {

    operator fun invoke(card: ContactCard): VCardBuildResult {
        val violations = card.validate()

        return if (violations.isEmpty()) {
            VCardBuildResult.Success(card.assemble())
        } else {
            VCardBuildResult.Invalid(violations)
        }
    }

    private fun ContactCard.assemble(): String = buildList {
        add(VCardFormat.BEGIN)
        add("${VCardFormat.PROPERTY_VERSION}:${VCardFormat.VERSION}")
        add("${VCardFormat.PROPERTY_NAME}:${structuredName()}")
        add(property(VCardFormat.PROPERTY_FORMATTED_NAME, formattedName.trim()))

        // Optional properties are omitted rather than written empty: an `ORG:` line claims the
        // person has an organisation whose name is blank, which is a different statement from not
        // having one.
        organization.optionalProperty(VCardFormat.PROPERTY_ORGANIZATION)?.let(::add)
        title.optionalProperty(VCardFormat.PROPERTY_TITLE)?.let(::add)
        phone.optionalProperty(
            name = VCardFormat.PROPERTY_TELEPHONE,
            parameters = VCardFormat.TELEPHONE_PARAMETERS,
        )?.let(::add)
        email.optionalProperty(
            name = VCardFormat.PROPERTY_EMAIL,
            parameters = VCardFormat.EMAIL_PARAMETERS,
        )?.let(::add)

        addAll(passthrough.map(VCardProperty::line))
        add(VCardFormat.END)
    }.joinToString(separator = VCardFormat.LINE_BREAK)

    /**
     * `Family;Given;;;` — all five components, trailing empties included.
     *
     * Truncating to `Family;Given` is valid-looking and wrong: a reader counting components would
     * find two where the spec promises five, and some put the given name in the wrong slot.
     */
    private fun ContactCard.structuredName(): String {
        val components = MutableList(VCardFormat.NAME_COMPONENT_COUNT) { "" }
        components[VCardFormat.NAME_COMPONENT_FAMILY] = VCardValueCodec.encode(familyName.trim())
        components[VCardFormat.NAME_COMPONENT_GIVEN] = VCardValueCodec.encode(givenName.trim())

        return components.joinToString(separator = VCardFormat.COMPONENT_SEPARATOR.toString())
    }

    private fun property(name: String, value: String, parameters: String = ""): String =
        "$name$parameters${VCardFormat.NAME_VALUE_SEPARATOR}${VCardValueCodec.encode(value)}"

    private fun String.optionalProperty(name: String, parameters: String = ""): String? =
        trim().takeIf { it.isNotEmpty() }?.let { property(name, it, parameters) }

    private fun ContactCard.validate(): List<VCardViolation> = buildList {
        if (formattedName.isBlank()) {
            add(VCardViolation(VCardField.DISPLAY_NAME, VCardViolationReason.REQUIRED))
        }

        val trimmedPhone = phone.trim()
        if (trimmedPhone.isNotEmpty() && trimmedPhone.none(Char::isDigit)) {
            add(VCardViolation(VCardField.PHONE, VCardViolationReason.INVALID_PHONE))
        }

        val trimmedEmail = email.trim()
        if (trimmedEmail.isNotEmpty() && !EMAIL.matches(trimmedEmail)) {
            add(VCardViolation(VCardField.EMAIL, VCardViolationReason.INVALID_EMAIL))
        }
    }

    private companion object {
        /**
         * One `@`, something either side, no whitespace.
         *
         * Deliberately loose. A stricter pattern rejects more real addresses than fake ones, and a
         * card is not the place to argue with someone about their own email.
         */
        val EMAIL = Regex("""[^@\s]+@[^@\s]+""")
    }
}
