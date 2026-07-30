package com.minion.scaffold.core.emv.usecase

import com.minion.scaffold.core.emv.model.EmvBuildResult
import com.minion.scaffold.core.emv.model.EmvField
import com.minion.scaffold.core.emv.model.EmvPayloadDraft
import com.minion.scaffold.core.emv.model.FieldViolation
import com.minion.scaffold.core.emv.model.MerchantAccount
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.TipSpec
import com.minion.scaffold.core.emv.model.TlvNode
import com.minion.scaffold.core.emv.model.ViolationReason
import com.minion.scaffold.core.emv.valueOrFail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BuildEmvPayloadUseCaseTest {

    private val build = BuildEmvPayloadUseCase()
    private val parse = ParseEmvPayloadUseCase()

    /**
     * The test the two use cases exist to satisfy.
     *
     * Asserting the payload string directly would only prove the builder is consistent with
     * itself. Reading it back with the parser proves it is consistent with the *specification* as
     * this codebase understands it — including that the checksum the builder wrote is the one the
     * parser recomputes, which is the single thing a second CRC implementation would break.
     */
    @Test
    fun `a built payload parses back to the values it was built from`() {
        val payload = build(dynamicDraft()).payloadOrFail()

        val report = parse(payload).valueOrFail()
        val segments = report.segments.associate { it.node.tag to it.node.rawValue }

        assertTrue(report.crc.passed)
        assertEquals("01", segments.getValue("00"))
        assertEquals("12", segments.getValue("01"))
        assertEquals("5812", segments.getValue("52"))
        assertEquals("360", segments.getValue("53"))
        assertEquals("15000000.00", segments.getValue("54"))
        assertEquals("ID", segments.getValue("58"))
        assertEquals("PAK BOS QR 1", segments.getValue("59"))
        assertEquals("Bekasi", segments.getValue("60"))
        assertEquals("17151", segments.getValue("61"))
    }

    @Test
    fun `merchant account subtags survive the round trip`() {
        val payload = build(dynamicDraft()).payloadOrFail()

        val acquirer = parse(payload).valueOrFail().segments.single { it.node.tag == "26" }

        assertEquals(
            listOf(
                TlvNode("00", 19, "ID.CO.CIMBNIAGA.WWW"),
                TlvNode("01", 18, "936000220000000282"),
                TlvNode("02", 15, "000008160012605"),
                TlvNode("03", 3, "UMI"),
            ),
            acquirer.node.children,
        )
    }

    @Test
    fun `two merchant accounts are written in ascending tag order and both nest`() {
        val draft = dynamicDraft(
            accounts = listOf(
                nationalSwitchAccount(),
                acquirerAccount(),
            ),
        )

        val segments = parse(build(draft).payloadOrFail()).valueOrFail().segments

        assertEquals(
            listOf("00", "01", "26", "51", "52", "53", "54", "58", "59", "60", "61", "63"),
            segments.map { it.node.tag },
        )
        assertEquals(3, segments.single { it.node.tag == "51" }.node.children.size)
    }

    @Test
    fun `a static payload omits the amount`() {
        val draft = dynamicDraft().copy(
            initiationMethod = PointOfInitiationMethod.STATIC,
            amount = null,
        )

        val segments = parse(build(draft).payloadOrFail()).valueOrFail().segments

        assertEquals("11", segments.single { it.node.tag == "01" }.node.rawValue)
        assertNull(segments.firstOrNull { it.node.tag == "54" })
    }

    /** Blank optionals are absent, not written as zero-length segments. */
    @Test
    fun `a blank postal code is omitted rather than written empty`() {
        val draft = dynamicDraft().copy(postalCode = "   ")

        val segments = parse(build(draft).payloadOrFail()).valueOrFail().segments

        assertNull(segments.firstOrNull { it.node.tag == "61" })
    }

    @Test
    fun `every violation is reported, not just the first`() {
        val draft = dynamicDraft().copy(
            merchantName = "",
            merchantCity = "",
            countryCode = "IDN",
        )

        val violations = build(draft).violationsOrFail()

        assertTrue(
            violations.containsAll(
                listOf(
                    FieldViolation(EmvField.MERCHANT_NAME, ViolationReason.REQUIRED),
                    FieldViolation(EmvField.MERCHANT_CITY, ViolationReason.REQUIRED),
                    FieldViolation(EmvField.COUNTRY_CODE, ViolationReason.WRONG_LENGTH),
                ),
            ),
        )
    }

    @Test
    fun `a dynamic payload requires an amount`() {
        val draft = dynamicDraft().copy(amount = null)

        assertTrue(
            FieldViolation(EmvField.TRANSACTION_AMOUNT, ViolationReason.REQUIRED)
                in build(draft).violationsOrFail(),
        )
    }

    /**
     * A static code is a printed sticker reused for every customer. An amount baked into one would
     * charge the next person the previous person's total.
     */
    @Test
    fun `a static payload refuses an amount`() {
        val draft = dynamicDraft().copy(
            initiationMethod = PointOfInitiationMethod.STATIC,
            amount = "15000.00",
        )

        assertTrue(
            FieldViolation(EmvField.TRANSACTION_AMOUNT, ViolationReason.NOT_ALLOWED)
                in build(draft).violationsOrFail(),
        )
    }

    @Test
    fun `an amount with three decimal places is rejected`() {
        val draft = dynamicDraft().copy(amount = "100.005")

        assertTrue(
            FieldViolation(EmvField.TRANSACTION_AMOUNT, ViolationReason.NOT_AN_AMOUNT)
                in build(draft).violationsOrFail(),
        )
    }

    @Test
    fun `a non-numeric merchant category code is rejected`() {
        val draft = dynamicDraft().copy(merchantCategoryCode = "58AB")

        assertTrue(
            FieldViolation(EmvField.MERCHANT_CATEGORY_CODE, ViolationReason.NOT_NUMERIC)
                in build(draft).violationsOrFail(),
        )
    }

    @Test
    fun `a merchant name over twenty-five characters is rejected`() {
        val draft = dynamicDraft().copy(merchantName = "A".repeat(26))

        assertTrue(
            FieldViolation(EmvField.MERCHANT_NAME, ViolationReason.TOO_LONG)
                in build(draft).violationsOrFail(),
        )
    }

    @Test
    fun `a draft with no merchant account is rejected`() {
        val draft = dynamicDraft(accounts = emptyList())

        assertTrue(
            FieldViolation(EmvField.MERCHANT_ACCOUNTS, ViolationReason.REQUIRED)
                in build(draft).violationsOrFail(),
        )
    }

    @Test
    fun `a merchant account tag outside 26 to 51 is rejected`() {
        val draft = dynamicDraft(accounts = listOf(acquirerAccount().copy(tag = "52")))

        assertTrue(
            FieldViolation(EmvField.ACQUIRER_TAG, ViolationReason.UNSUPPORTED, accountIndex = 0)
                in build(draft).violationsOrFail(),
        )
    }

    /** The violation has to say *which* account, or a form with two of them cannot mark one. */
    @Test
    fun `a violation in the second account carries its index`() {
        val draft = dynamicDraft(
            accounts = listOf(
                acquirerAccount(),
                nationalSwitchAccount().copy(globallyUniqueIdentifier = ""),
            ),
        )

        assertTrue(
            FieldViolation(
                EmvField.ACQUIRER_IDENTIFIER,
                ViolationReason.REQUIRED,
                accountIndex = 1,
            ) in build(draft).violationsOrFail(),
        )
    }

    /**
     * The length prefix is two decimal digits, so 99 is the ceiling everywhere — and a subtag
     * cannot reach it. The template's value is itself capped at 99 and carries the subtag's own
     * four-character header, so the largest identifier that fits alone is 95.
     */
    @Test
    fun `a subtag that fills its template exactly builds`() {
        val draft = dynamicDraft(
            accounts = listOf(
                acquirerAccount().copy(
                    globallyUniqueIdentifier = "A".repeat(95),
                    merchantPan = null,
                    merchantId = null,
                    merchantCriteria = null,
                ),
            ),
        )

        assertTrue(build(draft) is EmvBuildResult.Success)
    }

    @Test
    fun `a subtag one character past its template is rejected`() {
        val draft = dynamicDraft(
            accounts = listOf(
                acquirerAccount().copy(
                    globallyUniqueIdentifier = "A".repeat(96),
                    merchantPan = null,
                    merchantId = null,
                    merchantCriteria = null,
                ),
            ),
        )

        assertTrue(
            FieldViolation(EmvField.ACQUIRER_IDENTIFIER, ViolationReason.TOO_LONG, 0)
                in build(draft).violationsOrFail(),
        )
    }

    /** Caught by the per-value ceiling rather than the template one — same verdict either way. */
    @Test
    fun `a value of one hundred characters is rejected`() {
        val draft = dynamicDraft(
            accounts = listOf(acquirerAccount().copy(globallyUniqueIdentifier = "A".repeat(100))),
        )

        assertTrue(
            FieldViolation(EmvField.ACQUIRER_IDENTIFIER, ViolationReason.TOO_LONG, 0)
                in build(draft).violationsOrFail(),
        )
    }

    /** Individually legal subtags can still overflow the template that concatenates them. */
    @Test
    fun `subtags that overflow their template are rejected`() {
        val draft = dynamicDraft(
            accounts = listOf(
                acquirerAccount().copy(
                    globallyUniqueIdentifier = "A".repeat(50),
                    merchantPan = "B".repeat(50),
                    merchantId = null,
                    merchantCriteria = null,
                ),
            ),
        )

        assertTrue(
            FieldViolation(EmvField.ACQUIRER_IDENTIFIER, ViolationReason.TOO_LONG, 0)
                in build(draft).violationsOrFail(),
        )
    }

    @Test
    fun `a tip prompt writes tag 55 and no fee tag`() {
        val draft = dynamicDraft().copy(tip = TipSpec.Prompt)

        val segments = parse(build(draft).payloadOrFail()).valueOrFail().segments

        assertEquals("01", segments.single { it.node.tag == "55" }.node.rawValue)
        assertNull(segments.firstOrNull { it.node.tag == "56" })
        assertNull(segments.firstOrNull { it.node.tag == "57" })
    }

    @Test
    fun `a fixed convenience fee writes tags 55 and 56`() {
        val draft = dynamicDraft().copy(tip = TipSpec.FixedFee("5000.00"))

        val segments = parse(build(draft).payloadOrFail()).valueOrFail().segments

        assertEquals("02", segments.single { it.node.tag == "55" }.node.rawValue)
        assertEquals("5000.00", segments.single { it.node.tag == "56" }.node.rawValue)
        assertNull(segments.firstOrNull { it.node.tag == "57" })
    }

    @Test
    fun `a percentage convenience fee writes tags 55 and 57`() {
        val draft = dynamicDraft().copy(tip = TipSpec.PercentageFee("5.5"))

        val segments = parse(build(draft).payloadOrFail()).valueOrFail().segments

        assertEquals("03", segments.single { it.node.tag == "55" }.node.rawValue)
        assertEquals("5.5", segments.single { it.node.tag == "57" }.node.rawValue)
        assertNull(segments.firstOrNull { it.node.tag == "56" })
    }

    /** The tip tags sit between the amount and the country code, keeping the payload ascending. */
    @Test
    fun `tip segments are written in tag order`() {
        val draft = dynamicDraft().copy(tip = TipSpec.FixedFee("5000"))

        val tags = parse(build(draft).payloadOrFail()).valueOrFail().segments.map { it.node.tag }

        assertEquals(
            listOf("00", "01", "26", "52", "53", "54", "55", "56", "58", "59", "60", "61", "63"),
            tags,
        )
    }

    @Test
    fun `no tip writes none of the tip tags`() {
        val segments = parse(build(dynamicDraft()).payloadOrFail()).valueOrFail().segments

        assertNull(segments.firstOrNull { it.node.tag == "55" })
    }

    @Test
    fun `a fee mode with no value is rejected`() {
        val draft = dynamicDraft().copy(tip = TipSpec.FixedFee(""))

        assertTrue(
            FieldViolation(EmvField.CONVENIENCE_FEE, ViolationReason.REQUIRED)
                in build(draft).violationsOrFail(),
        )
    }

    @Test
    fun `a non-numeric fee is rejected`() {
        val draft = dynamicDraft().copy(tip = TipSpec.FixedFee("five thousand"))

        assertTrue(
            FieldViolation(EmvField.CONVENIENCE_FEE, ViolationReason.NOT_AN_AMOUNT)
                in build(draft).violationsOrFail(),
        )
    }

    /** Well-formed and meaningless — the pattern alone cannot catch it. */
    @Test
    fun `a percentage above one hundred is rejected`() {
        val draft = dynamicDraft().copy(tip = TipSpec.PercentageFee("150"))

        assertTrue(
            FieldViolation(EmvField.CONVENIENCE_FEE, ViolationReason.OUT_OF_RANGE)
                in build(draft).violationsOrFail(),
        )
    }

    @Test
    fun `a percentage of exactly one hundred is allowed`() {
        val draft = dynamicDraft().copy(tip = TipSpec.PercentageFee("100"))

        assertTrue(build(draft) is EmvBuildResult.Success)
    }

    /** Tag 57 holds at most five characters, so a long rate cannot be expressed. */
    @Test
    fun `a percentage longer than five characters is rejected`() {
        val draft = dynamicDraft().copy(tip = TipSpec.PercentageFee("12.345"))

        assertTrue(
            FieldViolation(EmvField.CONVENIENCE_FEE, ViolationReason.TOO_LONG)
                in build(draft).violationsOrFail(),
        )
    }

    /** A tip is orthogonal to the amount: a reusable sticker can still ask for one. */
    @Test
    fun `a static payload may still prompt for a tip`() {
        val draft = dynamicDraft().copy(
            initiationMethod = PointOfInitiationMethod.STATIC,
            amount = null,
            tip = TipSpec.Prompt,
        )

        val segments = parse(build(draft).payloadOrFail()).valueOrFail().segments

        assertEquals("01", segments.single { it.node.tag == "55" }.node.rawValue)
        assertNull(segments.firstOrNull { it.node.tag == "54" })
    }

    private fun dynamicDraft(
        accounts: List<MerchantAccount> = listOf(acquirerAccount()),
    ) = EmvPayloadDraft(
        initiationMethod = PointOfInitiationMethod.DYNAMIC,
        merchantAccounts = accounts,
        merchantCategoryCode = "5812",
        currencyNumericCode = "360",
        amount = "15000000.00",
        countryCode = "ID",
        merchantName = "PAK BOS QR 1",
        merchantCity = "Bekasi",
        postalCode = "17151",
    )

    /** Mirrors the acquirer template in the sample payload, subtags `00`, `01`, `02`, `03`. */
    private fun acquirerAccount() = MerchantAccount(
        tag = "26",
        globallyUniqueIdentifier = "ID.CO.CIMBNIAGA.WWW",
        merchantPan = "936000220000000282",
        merchantId = "000008160012605",
        merchantCriteria = "UMI",
    )

    /** The national switch template carries no PAN — subtags `00`, `02`, `03`. */
    private fun nationalSwitchAccount() = MerchantAccount(
        tag = "51",
        globallyUniqueIdentifier = "ID.OR.QRNPG.WWW",
        merchantPan = null,
        merchantId = "ID0000000000123",
        merchantCriteria = "UMI",
    )

    private fun EmvBuildResult.payloadOrFail(): String = when (this) {
        is EmvBuildResult.Success -> payload
        is EmvBuildResult.Invalid -> throw AssertionError("expected Success but was $violations")
    }

    private fun EmvBuildResult.violationsOrFail(): List<FieldViolation> = when (this) {
        is EmvBuildResult.Success -> throw AssertionError("expected Invalid but built $payload")
        is EmvBuildResult.Invalid -> violations
    }
}
