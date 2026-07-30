package com.minion.scaffold.core.vcard.usecase

import com.minion.scaffold.core.vcard.model.ContactCard
import com.minion.scaffold.core.vcard.model.VCardBuildResult
import com.minion.scaffold.core.vcard.model.VCardField
import com.minion.scaffold.core.vcard.model.VCardViolation
import com.minion.scaffold.core.vcard.model.VCardViolationReason
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BuildVCardPayloadUseCaseTest {

    private val build = BuildVCardPayloadUseCase()

    /** vCard 3.0 refuses a card without `FN`, so this is the one field that is not optional. */
    @Test
    fun `a display name is required`() {
        assertTrue(
            VCardViolation(VCardField.DISPLAY_NAME, VCardViolationReason.REQUIRED)
                in build(ContactCard(formattedName = "   ")).violationsOrFail(),
        )
    }

    @Test
    fun `a card with only a display name builds`() {
        assertTrue(build(ContactCard(formattedName = "Reception")) is VCardBuildResult.Success)
    }

    @Test
    fun `an email with no at sign is rejected`() {
        val card = ContactCard(formattedName = "Jane", email = "jane.acme.example")

        assertTrue(
            VCardViolation(VCardField.EMAIL, VCardViolationReason.INVALID_EMAIL)
                in build(card).violationsOrFail(),
        )
    }

    @Test
    fun `an email with nothing before the at sign is rejected`() {
        val card = ContactCard(formattedName = "Jane", email = "@acme.example")

        assertTrue(
            VCardViolation(VCardField.EMAIL, VCardViolationReason.INVALID_EMAIL)
                in build(card).violationsOrFail(),
        )
    }

    /** Loose on purpose — a card is not the place to argue with someone about their own address. */
    @Test
    fun `an unusual but plausible email is accepted`() {
        for (email in listOf("a@b", "jane+tag@acme.example", "jane.smith@sub.domain.co.id")) {
            val card = ContactCard(formattedName = "Jane", email = email)

            assertTrue("expected $email to build", build(card) is VCardBuildResult.Success)
        }
    }

    @Test
    fun `a phone number with no digits is rejected`() {
        val card = ContactCard(formattedName = "Jane", phone = "call me")

        assertTrue(
            VCardViolation(VCardField.PHONE, VCardViolationReason.INVALID_PHONE)
                in build(card).violationsOrFail(),
        )
    }

    /**
     * Numbers are written with spaces, dashes, brackets and extensions in every combination, so the
     * only rule is that there is a digit somewhere.
     */
    @Test
    fun `every plausible phone format is accepted`() {
        val numbers = listOf(
            "+62 811 234 567",
            "(021) 555-0100",
            "0811234567",
            "+1-555-0100 ext. 22",
        )

        for (number in numbers) {
            val card = ContactCard(formattedName = "Jane", phone = number)

            assertTrue("expected $number to build", build(card) is VCardBuildResult.Success)
        }
    }

    @Test
    fun `every violation is reported, not just the first`() {
        val card = ContactCard(formattedName = "", phone = "none", email = "nope")

        val violations = build(card).violationsOrFail()

        assertTrue(violations.any { it.field == VCardField.DISPLAY_NAME })
        assertTrue(violations.any { it.field == VCardField.PHONE })
        assertTrue(violations.any { it.field == VCardField.EMAIL })
    }

    private fun VCardBuildResult.violationsOrFail(): List<VCardViolation> = when (this) {
        is VCardBuildResult.Success -> throw AssertionError("expected Invalid but built $payload")
        is VCardBuildResult.Invalid -> violations
    }
}
