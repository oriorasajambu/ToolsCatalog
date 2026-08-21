package com.minion.scaffold.feature.qrscan.domain.export

import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.usecase.EmvDraftFromPayloadUseCase
import com.minion.scaffold.core.emv.usecase.ParseEmvPayloadUseCase
import com.minion.scaffold.feature.qrscan.presentation.ScanSamples
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each name is worth, against both specimens.
 *
 * The vocabulary is wider than the shipped contract uses, so most of what is asserted here is not
 * covered by the golden export test — a name nothing renders can still be wrong.
 */
internal class ResolvePlaceholdersUseCaseTest {

    private val parse = ParseEmvPayloadUseCase()
    private val resolve = ResolvePlaceholdersUseCase(EmvDraftFromPayloadUseCase(parse))

    @Test
    fun `a domestic code answers every merchant name`() {
        val values = valuesOf(ScanSamples.QRIS_DYNAMIC)

        assertEquals("PAK BOS QR 1", values.text(PlaceholderName.MerchantName))
        assertEquals("Bekasi", values.text(PlaceholderName.MerchantCity))
        assertEquals("ID", values.text(PlaceholderName.MerchantCountryCode))
        assertEquals("17151", values.text(PlaceholderName.MerchantPostalCode))
        assertEquals("0780", values.text(PlaceholderName.MerchantCategoryCode))
        assertEquals("UMI", values.text(PlaceholderName.MerchantCriteria))
    }

    @Test
    fun `a domestic code separates its two accounts`() {
        val values = valuesOf(ScanSamples.QRIS_DYNAMIC)

        assertEquals(
            "ID.CO.CIMBNIAGA.WWW",
            values.text(PlaceholderName.MerchantPaymentProviderName),
        )
        assertEquals("936000220000000282", values.text(PlaceholderName.MerchantPan))
        assertEquals("000008160012605", values.text(PlaceholderName.MerchantId))
        assertEquals("ID0000000000123", values.text(PlaceholderName.MerchantIdNational))
        assertEquals("ID.OR.QRNPG.WWW", values.text(PlaceholderName.MerchantProviderNational))
    }

    @Test
    fun `a cross-border code has one account and says so`() {
        val values = valuesOf(CROSS_BORDER)

        assertEquals("SA.GOV.SAMA", values.text(PlaceholderName.MerchantPaymentProviderName))
        assertEquals("0000000003465480", values.text(PlaceholderName.MerchantPan))
        assertEquals("010100", values.text(PlaceholderName.MerchantId))
        assertTrue(values.isNull(PlaceholderName.MerchantIdNational))
        assertTrue(values.isNull(PlaceholderName.MerchantProviderNational))
    }

    @Test
    fun `the scheme verdict needs both country and currency`() {
        assertEquals("qris", valuesOf(ScanSamples.QRIS_DYNAMIC).text(PlaceholderName.SchemeType))
        assertEquals("qrcrossborder", valuesOf(CROSS_BORDER).text(PlaceholderName.SchemeType))
    }

    @Test
    fun `currency comes out both ways round`() {
        val values = valuesOf(ScanSamples.QRIS_DYNAMIC)

        assertEquals("IDR", values.text(PlaceholderName.AmountCurrency))
        assertEquals("360", values.text(PlaceholderName.AmountCurrencyCode))
    }

    @Test
    fun `the checksum is offered as its two halves and a verdict`() {
        val values = valuesOf(ScanSamples.QRIS_DYNAMIC)

        assertEquals("3D58", values.text(PlaceholderName.CrcExpected))
        assertEquals("3D58", values.text(PlaceholderName.CrcActual))
        assertEquals("true", values.text(PlaceholderName.CrcValid))
    }

    @Test
    fun `a tampered code reports its checksum as failing`() {
        val values = valuesOf(ScanSamples.QRIS_TAMPERED)

        assertEquals("false", values.text(PlaceholderName.CrcValid))
    }

    @Test
    fun `the whole payload is available`() {
        val values = valuesOf(ScanSamples.QRIS_DYNAMIC)

        assertEquals(ScanSamples.QRIS_DYNAMIC, values.text(PlaceholderName.Payload))
    }

    @Test
    fun `a tag path resolves on the code that has it and not the other`() {
        // The reason a template should prefer merchant_pan: the primary account is tag 26 on a
        // domestic code and tag 32 on a cross-border one, and a hard-coded path is right on
        // exactly one of them.
        val domestic = valuesOf(ScanSamples.QRIS_DYNAMIC)
        val crossBorder = valuesOf(CROSS_BORDER)

        assertEquals("936000220000000282", domestic.tag("tag:26.01"))
        assertEquals(JsonNull, crossBorder.resolveToken("tag:26.01"))

        assertEquals("0000000003465480", crossBorder.tag("tag:32.01"))
        assertEquals(JsonNull, domestic.resolveToken("tag:32.01"))
    }

    @Test
    fun `a tag path reaches what no name covers`() {
        // Tag 62's reference label and tag 32's expiry, neither of which the vocabulary names —
        // the whole reason the escape hatch exists.
        val values = valuesOf(CROSS_BORDER)

        assertEquals("REF20260806000001", values.tag("tag:62.05"))
        assertEquals("E20280323T120000", values.tag("tag:32.04"))
        assertEquals("SA", values.tag("tag:65"))
    }

    @Test
    fun `an unknown name resolves to nothing at all, which is different from null`() {
        val values = valuesOf(ScanSamples.QRIS_DYNAMIC)

        // Null would mean "this code does not carry it". Absent means "no such name exists", and
        // only the second is a template author's mistake.
        assertEquals(null, values.resolve(Placeholder.Named("merchant_nmae")))
    }

    private fun valuesOf(payload: String): PlaceholderValues {
        val report = (parse(payload) as EmvParseResult.Success).value
        return resolve(report)!!
    }

    private fun PlaceholderValues.text(name: PlaceholderName): String =
        resolve(Placeholder.Named(name.token))!!.jsonPrimitive.content

    private fun PlaceholderValues.isNull(name: PlaceholderName): Boolean =
        resolve(Placeholder.Named(name.token)) is JsonNull

    private fun PlaceholderValues.tag(token: String): String =
        resolveToken(token)!!.jsonPrimitive.content

    private fun PlaceholderValues.resolveToken(token: String) =
        resolve(PlaceholderSyntax.parse(token)!!)

    private companion object {
        const val CROSS_BORDER =
            "00020101021232710011SA.GOV.SAMA011600000000034654800206010100030201041" +
                "6E20280323T1200005204074253036825406100.005802SA5910MUSAADTest6007ALRIY" +
                "AD61051234562770517REF20260806000001100512453110340054360011SA.GOV.SAMA0" +
                "10515.000208400000006502SA63047484"
    }
}
