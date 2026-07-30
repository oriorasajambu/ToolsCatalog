package com.minion.scaffold.core.vcard.format

/** The literals of vCard 3.0, shared by the writer and the reader. */
internal object VCardFormat {

    const val BEGIN = "BEGIN:VCARD"
    const val END = "END:VCARD"
    const val VERSION = "3.0"

    /**
     * The spec's line ending.
     *
     * Written always, and either accepted on read — plenty of generators emit bare LF, and refusing
     * their cards would gain nothing.
     */
    const val LINE_BREAK = "\r\n"

    const val PROPERTY_VERSION = "VERSION"
    const val PROPERTY_NAME = "N"
    const val PROPERTY_FORMATTED_NAME = "FN"
    const val PROPERTY_ORGANIZATION = "ORG"
    const val PROPERTY_TITLE = "TITLE"
    const val PROPERTY_TELEPHONE = "TEL"
    const val PROPERTY_EMAIL = "EMAIL"

    /** The parameters written on a single phone and a single email. */
    const val TELEPHONE_PARAMETERS = ";TYPE=CELL"
    const val EMAIL_PARAMETERS = ";TYPE=INTERNET"

    /** `N` is five components: family, given, additional, prefix, suffix. */
    const val NAME_COMPONENT_COUNT = 5
    const val NAME_COMPONENT_FAMILY = 0
    const val NAME_COMPONENT_GIVEN = 1

    const val COMPONENT_SEPARATOR = ';'
    const val NAME_VALUE_SEPARATOR = ':'
}
