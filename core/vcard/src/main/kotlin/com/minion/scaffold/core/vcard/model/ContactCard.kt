package com.minion.scaffold.core.vcard.model

/**
 * The contact details this tool can show and edit.
 *
 * [formattedName] is `FN`, which vCard 3.0 requires — a card without one is invalid, so it is a
 * field here rather than something derived and hoped for. [givenName] and [familyName] are the two
 * components of `N` that a business card actually carries.
 */
data class ContactCard(
    val formattedName: String,
    val givenName: String = "",
    val familyName: String = "",
    val organization: String = "",
    val title: String = "",
    val phone: String = "",
    val email: String = "",
    /**
     * Properties no field above represents, re-emitted verbatim.
     *
     * This is what makes editing a scanned card safe. A real card carries `ADR`, `BDAY`, `NOTE`,
     * sometimes a whole `PHOTO`, and a second `TEL` — none of which this form shows. Without
     * somewhere to keep them, changing someone's job title would quietly delete their address, and
     * the regenerated code would scan perfectly while holding less than the one it replaced.
     *
     * Each holds its whole unfolded line, so putting them back is exact — and exposes the name and
     * value so a reader can show what a card carries rather than only how much of it.
     */
    val passthrough: List<VCardProperty> = emptyList(),
)
