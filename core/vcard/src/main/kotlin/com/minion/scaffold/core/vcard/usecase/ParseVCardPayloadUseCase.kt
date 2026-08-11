package com.minion.scaffold.core.vcard.usecase

import com.minion.scaffold.core.vcard.format.VCardFormat
import com.minion.scaffold.core.vcard.format.VCardValueCodec
import com.minion.scaffold.core.vcard.model.ContactCard
import com.minion.scaffold.core.vcard.model.VCardProperty
import javax.inject.Inject

/**
 * Reads a vCard 3.0 payload, or reports that this is not one.
 *
 * Null is not a failure — a payment code is not a broken contact card. The same reason
 * `QrScanError` wraps `QrParseError` rather than extending it.
 *
 * Liberal in what it accepts: properties in any order, bare LF line endings, folded lines, and a
 * missing `VERSION`. A *wrong* version is refused, because a 2.1 or 4.0 card is a different format
 * whose values this model would misread rather than merely not show.
 */
class ParseVCardPayloadUseCase @Inject constructor() {

    /**
     * Reads [payload] into a contact card, or returns null when it is not a vCard.
     *
     * @param payload The scanned or pasted payload, whitespace and all.
     * @return The parsed [ContactCard], or `null` when [payload] is not a vCard 3.0 card or is
     *         missing its mandatory `FN`.
     */
    operator fun invoke(payload: String): ContactCard? {
        val lines = VCardValueCodec.unfold(payload.trim())
            .split(VCardFormat.LINE_BREAK, "\n")
            .map(String::trim)
            .filter(String::isNotEmpty)

        if (lines.size < MINIMUM_LINES) return null
        if (!lines.first().equals(VCardFormat.BEGIN, ignoreCase = true)) return null
        if (!lines.last().equals(VCardFormat.END, ignoreCase = true)) return null

        var formattedName = ""
        var givenName = ""
        var familyName = ""
        var organization = ""
        var title = ""
        var phone = ""
        var email = ""
        var nameSeen = false
        val passthrough = mutableListOf<VCardProperty>()

        for (line in lines.subList(1, lines.size - 1)) {
            val property = line.asProperty()
            if (property == null) {
                passthrough += VCardProperty(line)
                continue
            }

            val (name, value) = property

            // The `else` branch is doing two jobs at once, and deliberately: a property this model
            // has no field for, and a *second* occurrence of one it does, are both things to carry
            // through untouched rather than drop. A card with two phone numbers keeps both — the
            // first in the field, the other verbatim.
            when {
                name == VCardFormat.PROPERTY_VERSION ->
                    if (value.trim() != VCardFormat.VERSION) return null

                name == VCardFormat.PROPERTY_FORMATTED_NAME && formattedName.isEmpty() ->
                    formattedName = VCardValueCodec.decode(value)

                name == VCardFormat.PROPERTY_NAME && !nameSeen -> {
                    nameSeen = true
                    val components = VCardValueCodec.splitUnescaped(
                        value,
                        VCardFormat.COMPONENT_SEPARATOR,
                    )
                    familyName = components.componentAt(VCardFormat.NAME_COMPONENT_FAMILY)
                    givenName = components.componentAt(VCardFormat.NAME_COMPONENT_GIVEN)
                }

                name == VCardFormat.PROPERTY_ORGANIZATION && organization.isEmpty() ->
                    organization = VCardValueCodec.decode(value)

                name == VCardFormat.PROPERTY_TITLE && title.isEmpty() ->
                    title = VCardValueCodec.decode(value)

                name == VCardFormat.PROPERTY_TELEPHONE && phone.isEmpty() ->
                    phone = VCardValueCodec.decode(value)

                name == VCardFormat.PROPERTY_EMAIL && email.isEmpty() ->
                    email = VCardValueCodec.decode(value)

                else -> passthrough += VCardProperty(line)
            }
        }

        // `FN` is mandatory in 3.0, so a card without one is not a card this tool can represent.
        if (formattedName.isEmpty()) return null

        return ContactCard(
            formattedName = formattedName,
            givenName = givenName,
            familyName = familyName,
            organization = organization,
            title = title,
            phone = phone,
            email = email,
            passthrough = passthrough,
        )
    }

    /**
     * Splits `NAME[;PARAMETERS]:value` at its first colon.
     *
     * The name is upper-cased and its parameters discarded — this model has no field for `TYPE`, so
     * `TEL;TYPE=WORK` and `TEL;TYPE=CELL` are both simply the phone number. Null means the line has
     * no colon at all, which makes it unparseable rather than unrecognised.
     */
    private fun String.asProperty(): Pair<String, String>? {
        val separatorIndex = indexOf(VCardFormat.NAME_VALUE_SEPARATOR)
        if (separatorIndex <= 0) return null

        val name = take(separatorIndex)
            .substringBefore(VCardFormat.COMPONENT_SEPARATOR)
            .uppercase()

        return name to substring(separatorIndex + 1)
    }

    /** Missing components are empty rather than an error — a short `N` is common and harmless. */
    private fun List<String>.componentAt(index: Int): String =
        getOrNull(index)?.let(VCardValueCodec::decode).orEmpty()

    private companion object {
        /** `BEGIN`, something, `END`. */
        const val MINIMUM_LINES = 3
    }
}
