package com.minion.scaffold.core.vcard.model

import com.minion.scaffold.core.vcard.format.VCardFormat
import com.minion.scaffold.core.vcard.format.VCardValueCodec

/**
 * One line of a vCard that [ContactCard] has no field for.
 *
 * The raw [line] is the single stored value and everything else is derived from it, which is what
 * keeps re-emission byte-exact: a property split into parts and reassembled can differ from what
 * arrived, and a line with no colon at all could not be represented.
 *
 * The derived accessors exist so a reader can *show* these rather than only count them. An
 * `ADR;TYPE=HOME:;;1 Long Road;Bekasi;;17151;ID` displayed verbatim is unreadable; the same
 * property as a name and a list of components is a postal address.
 */
data class VCardProperty(val line: String) {

    /** Upper-cased property name — `ADR`, `BDAY`, `X-SOMETHING`. */
    val name: String
        get() = nameAndParameters.substringBefore(VCardFormat.COMPONENT_SEPARATOR).uppercase()

    /** The value with escapes resolved. */
    val value: String get() = VCardValueCodec.decode(rawValue)

    /**
     * The value split on unescaped `;`, each part unescaped.
     *
     * One entry for a plain property, several for a structured one like `ADR`. A semicolon the
     * author actually typed is escaped, so it stays inside its component rather than splitting it.
     */
    val components: List<String>
        get() = VCardValueCodec
            .splitUnescaped(rawValue, VCardFormat.COMPONENT_SEPARATOR)
            .map(VCardValueCodec::decode)

    private val rawValue: String
        get() = if (separatorIndex < 0) "" else line.substring(separatorIndex + 1)

    /** A line with no colon is malformed; treating the whole of it as the name loses nothing. */
    private val nameAndParameters: String
        get() = if (separatorIndex < 0) line else line.take(separatorIndex)

    private val separatorIndex: Int
        get() = line.indexOf(VCardFormat.NAME_VALUE_SEPARATOR)
}
