package com.minion.scaffold.core.vcard.usecase

import com.minion.scaffold.core.vcard.model.ContactCard
import com.minion.scaffold.core.vcard.model.VCardBuildResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `parse(build(card)) == card`, plus the exact bytes where the format is prescriptive.
 *
 * The round trip alone would pass against a writer and reader that were consistently wrong together
 * — the escaping and `N` rules are asserted literally for that reason, because those are what
 * anything *else* reading the card depends on.
 */
internal class VCardPayloadRoundTripTest {

    private val build = BuildVCardPayloadUseCase()
    private val parse = ParseVCardPayloadUseCase()

    private val jane = ContactCard(
        formattedName = "Jane Smith",
        givenName = "Jane",
        familyName = "Smith",
        organization = "Acme Ltd",
        title = "Engineer",
        phone = "+62811234567",
        email = "jane@acme.example",
    )

    @Test
    fun `a full card writes the canonical form`() {
        assertEquals(
            "BEGIN:VCARD\r\n" +
                "VERSION:3.0\r\n" +
                "N:Smith;Jane;;;\r\n" +
                "FN:Jane Smith\r\n" +
                "ORG:Acme Ltd\r\n" +
                "TITLE:Engineer\r\n" +
                "TEL;TYPE=CELL:+62811234567\r\n" +
                "EMAIL;TYPE=INTERNET:jane@acme.example\r\n" +
                "END:VCARD",
            build(jane).payloadOrFail(),
        )
    }

    @Test
    fun `a full card round trips`() {
        assertEquals(jane, parse(build(jane).payloadOrFail()))
    }

    @Test
    fun `a card with only a display name round trips`() {
        val minimal = ContactCard(formattedName = "Jane")

        assertEquals(minimal, parse(build(minimal).payloadOrFail()))
    }

    /** All five components, trailing empties included — a reader counting them expects five. */
    @Test
    fun `the structured name keeps its trailing empty components`() {
        assertTrue("N:Smith;Jane;;;" in build(jane).payloadOrFail())
    }

    @Test
    fun `a card with no name components still writes an N line`() {
        val card = ContactCard(formattedName = "Reception")

        assertTrue("N:;;;;" in build(card).payloadOrFail())
    }

    /** Blank optionals are absent, not written as empty properties. */
    @Test
    fun `blank optional properties are omitted`() {
        val payload = build(ContactCard(formattedName = "Jane")).payloadOrFail()

        assertTrue("ORG" !in payload)
        assertTrue("TITLE" !in payload)
        assertTrue("TEL" !in payload)
        assertTrue("EMAIL" !in payload)
    }

    // Escaping — the rules that silently corrupt a card when skipped.

    @Test
    fun `a comma in an organisation is escaped`() {
        val card = jane.copy(organization = "Smith, Jones & Co")

        val payload = build(card).payloadOrFail()

        assertTrue("ORG:Smith\\, Jones & Co" in payload)
        assertEquals(card, parse(payload))
    }

    @Test
    fun `a semicolon in a name is escaped and does not split the components`() {
        val card = jane.copy(familyName = "Smith;Jones")

        val payload = build(card).payloadOrFail()

        assertTrue("N:Smith\\;Jones;Jane;;;" in payload)
        assertEquals(card, parse(payload))
    }

    @Test
    fun `every special character survives both directions`() {
        for (character in listOf('\\', ';', ',')) {
            val card = jane.copy(organization = "a${character}b", title = "c${character}d")

            assertEquals(card, parse(build(card).payloadOrFail()))
        }
    }

    @Test
    fun `a newline in a value becomes an escape sequence and comes back`() {
        val card = jane.copy(organization = "Acme\nLtd")

        val payload = build(card).payloadOrFail()

        assertTrue("ORG:Acme\\nLtd" in payload)
        assertEquals(card, parse(payload))
    }

    /**
     * Only the first colon separates a property from its value, so a colon inside one needs no
     * escaping — and must not get any, or the contact shows a literal backslash.
     */
    @Test
    fun `a colon in a value is not escaped`() {
        val card = jane.copy(title = "Engineer: Platform")

        val payload = build(card).payloadOrFail()

        assertTrue("TITLE:Engineer: Platform" in payload)
        assertEquals(card, parse(payload))
    }

    // Reading is liberal where writing is strict.

    @Test
    fun `properties in a different order parse`() {
        val payload = "BEGIN:VCARD\r\nFN:Jane Smith\r\nORG:Acme Ltd\r\nVERSION:3.0\r\n" +
            "N:Smith;Jane;;;\r\nEND:VCARD"

        val card = parse(payload)

        assertEquals("Jane Smith", card?.formattedName)
        assertEquals("Smith", card?.familyName)
        assertEquals("Acme Ltd", card?.organization)
    }

    @Test
    fun `bare LF line endings parse`() {
        val payload = "BEGIN:VCARD\nVERSION:3.0\nFN:Jane Smith\nEND:VCARD"

        assertEquals("Jane Smith", parse(payload)?.formattedName)
    }

    /** Other generators fold at 75 octets; a reader that does not unfold sees a truncated value. */
    @Test
    fun `a folded line parses to the same value as its unfolded equivalent`() {
        val folded = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jane Smith\r\n" +
            "ORG:Acme Interna\r\n tional Ltd\r\nEND:VCARD"

        assertEquals("Acme International Ltd", parse(folded)?.organization)
    }

    @Test
    fun `a missing version parses as 3 point 0`() {
        val payload = "BEGIN:VCARD\r\nFN:Jane Smith\r\nEND:VCARD"

        assertEquals("Jane Smith", parse(payload)?.formattedName)
    }

    @Test
    fun `a TYPE parameter is accepted and its value taken`() {
        val payload = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jane\r\nTEL;TYPE=WORK,VOICE:+62811\r\n" +
            "END:VCARD"

        assertEquals("+62811", parse(payload)?.phone)
    }

    // Passthrough — the reason editing a scanned card is safe.

    @Test
    fun `an unrecognised property survives a round trip`() {
        val payload = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jane Smith\r\n" +
            "ADR;TYPE=HOME:;;1 Long Road;Bekasi;;17151;ID\r\nBDAY:1990-01-01\r\nEND:VCARD"

        val card = parse(payload) ?: throw AssertionError("expected a card")
        val rebuilt = build(card).payloadOrFail()

        assertTrue("ADR;TYPE=HOME:;;1 Long Road;Bekasi;;17151;ID" in rebuilt)
        assertTrue("BDAY:1990-01-01" in rebuilt)
    }

    /**
     * Editing one field must not disturb anything else — the same check the EMV editor has, and the
     * same class of silent loss it exists to catch.
     */
    @Test
    fun `editing a field leaves carried-through properties intact`() {
        val payload = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jane Smith\r\nBDAY:1990-01-01\r\n" +
            "END:VCARD"

        val card = parse(payload) ?: throw AssertionError("expected a card")
        val rebuilt = build(card.copy(title = "Director")).payloadOrFail()

        assertTrue("TITLE:Director" in rebuilt)
        assertTrue("BDAY:1990-01-01" in rebuilt)
    }

    /** A second phone number is not a field this model has, so it is kept rather than dropped. */
    @Test
    fun `a repeated property keeps the first in the field and carries the rest`() {
        val payload = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jane\r\nTEL;TYPE=CELL:+111\r\n" +
            "TEL;TYPE=WORK:+222\r\nEND:VCARD"

        val card = parse(payload) ?: throw AssertionError("expected a card")

        assertEquals("+111", card.phone)
        assertTrue("TEL;TYPE=WORK:+222" in build(card).payloadOrFail())
    }

    // Not a vCard is not an error.

    @Test
    fun `other formats are not contact cards`() {
        assertNull(parse("https://example.com"))
        assertNull(parse("WIFI:T:WPA;S:Guest;P:hunter2!;;"))
        assertNull(parse("0002010102125204573253033605802ID63043D58"))
        assertNull(parse("just some text"))
    }

    @Test
    fun `a card with no BEGIN or END is refused`() {
        assertNull(parse("VERSION:3.0\r\nFN:Jane Smith"))
        assertNull(parse("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jane Smith"))
    }

    /** A 2.1 card's values would be misread by this model rather than merely not shown. */
    @Test
    fun `another vCard version is refused`() {
        assertNull(parse("BEGIN:VCARD\r\nVERSION:2.1\r\nFN:Jane Smith\r\nEND:VCARD"))
        assertNull(parse("BEGIN:VCARD\r\nVERSION:4.0\r\nFN:Jane Smith\r\nEND:VCARD"))
    }

    @Test
    fun `a card with no display name is refused`() {
        assertNull(parse("BEGIN:VCARD\r\nVERSION:3.0\r\nN:Smith;Jane;;;\r\nEND:VCARD"))
    }

    private fun VCardBuildResult.payloadOrFail(): String = when (this) {
        is VCardBuildResult.Success -> payload
        is VCardBuildResult.Invalid -> throw AssertionError("expected Success but was $violations")
    }
}
