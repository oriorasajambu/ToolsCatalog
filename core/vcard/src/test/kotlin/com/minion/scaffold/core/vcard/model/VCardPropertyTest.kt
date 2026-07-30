package com.minion.scaffold.core.vcard.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The accessors a reader uses to *show* a carried-through property rather than only count it.
 *
 * [VCardProperty.line] stays the source of truth so re-emission is byte-exact; these derive from it.
 */
internal class VCardPropertyTest {

    @Test
    fun `the name is upper-cased and stripped of parameters`() {
        assertEquals("ADR", VCardProperty("ADR;TYPE=HOME:;;1 Long Road;;;;").name)
        assertEquals("BDAY", VCardProperty("bday:1990-01-01").name)
        assertEquals("X-ANDROID-CUSTOM", VCardProperty("X-ANDROID-CUSTOM:something").name)
    }

    @Test
    fun `the line is preserved exactly`() {
        val line = "ADR;TYPE=HOME:;;1 Long Road;Bekasi;;17151;ID"

        assertEquals(line, VCardProperty(line).line)
    }

    /** Seven components, of which four are empty — which is what makes joining them necessary. */
    @Test
    fun `a structured value splits into its components`() {
        val property = VCardProperty("ADR;TYPE=HOME:;;1 Long Road;Bekasi;;17151;ID")

        assertEquals(
            listOf("", "", "1 Long Road", "Bekasi", "", "17151", "ID"),
            property.components,
        )
    }

    @Test
    fun `a plain value is a single component`() {
        assertEquals(listOf("1990-01-01"), VCardProperty("BDAY:1990-01-01").components)
    }

    /** An escaped semicolon is content, so it stays inside its component. */
    @Test
    fun `an escaped separator does not split a component`() {
        val property = VCardProperty("""NOTE:Met at a conference\; remember the follow-up""")

        assertEquals(
            listOf("Met at a conference; remember the follow-up"),
            property.components,
        )
    }

    @Test
    fun `the value has its escapes resolved`() {
        assertEquals(
            "Smith, Jones & Co",
            VCardProperty("""ORG:Smith\, Jones & Co""").value,
        )
    }

    @Test
    fun `an escaped newline becomes a line break`() {
        assertEquals("line one\nline two", VCardProperty("""NOTE:line one\nline two""").value)
    }

    /** A colon inside a value is not a separator, so only the first one is. */
    @Test
    fun `only the first colon separates the name from the value`() {
        val property = VCardProperty("URL:https://example.com/a:b")

        assertEquals("URL", property.name)
        assertEquals("https://example.com/a:b", property.value)
    }

    /** A malformed line is still carried; treating all of it as the name loses nothing. */
    @Test
    fun `a line with no colon keeps itself as the name`() {
        val property = VCardProperty("GARBAGE")

        assertEquals("GARBAGE", property.name)
        assertEquals("", property.value)
    }
}
