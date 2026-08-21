package com.minion.scaffold.feature.qrscan.domain.export

import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.usecase.EmvDraftFromPayloadUseCase
import com.minion.scaffold.core.emv.usecase.ParseEmvPayloadUseCase
import com.minion.scaffold.feature.qrscan.presentation.ScanSamples
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserted against the parsed document rather than against formatted text, so indentation and key
 * order are free to change without rewriting every expectation.
 *
 * Payloads that need a tip are assembled tag by tag, because no real specimen to hand carries one.
 * Their checksums are placeholders — a failed checksum is deliberately not a parse error, so a
 * payload ending `6304ABCD` reads back fine and the export never has to care.
 */
internal class ExportPaymentJsonUseCaseTest {

    private val parse = ParseEmvPayloadUseCase()
    private val export = ExportPaymentJsonUseCase(EmvDraftFromPayloadUseCase(parse))

    @Test
    fun `a domestic code names both of its merchant identifiers`() {
        val json = exportOf(ScanSamples.QRIS_DYNAMIC)

        assertEquals("qris", json.string("qr_type"))
        assertEquals("dynamic", json.obj("qr_data").string("qr_type"))

        val merchant = json.merchantData()
        assertEquals("ID.CO.CIMBNIAGA.WWW", merchant.string("merchant_payment_provider_name"))
        assertEquals("936000220000000282", merchant.string("merchant_pan"))
        assertEquals("000008160012605", merchant.string("merchant_id"))
        // The national switch at tag 51, which carries no subtag 01 and so is never the primary.
        assertEquals("ID0000000000123", merchant.string("merchant_id_national"))
    }

    @Test
    fun `a domestic code carries its own figures, unreformatted`() {
        val merchant = exportOf(ScanSamples.QRIS_DYNAMIC).merchantData()

        assertEquals("15000000.00", merchant.string("amount"))
        assertEquals("FIXED_AMOUNT", merchant.string("amount_type"))
        assertEquals("IDR", merchant.string("amount_currency"))
        assertEquals("PAK BOS QR 1", merchant.string("merchant_name"))
        assertEquals("ID", merchant.string("merchant_country_code"))
        assertEquals("Bekasi", merchant.string("merchant_city"))
        assertEquals("17151", merchant.string("merchant_postal_code"))
    }

    @Test
    fun `a code with no tip has no total distinct from its amount`() {
        val merchant = exportOf(ScanSamples.QRIS_DYNAMIC).merchantData()

        assertEquals("NO_TIPS", merchant.string("tips_type"))
        assertTrue(merchant.isNull("tips"))
        assertTrue(merchant.obj("dynamic_qr").isNull("total_payment"))
    }

    @Test
    fun `a cross-border code is named as one and has no national identifier`() {
        val json = exportOf(CROSS_BORDER)

        assertEquals("qrcrossborder", json.string("qr_type"))

        val merchant = json.merchantData()
        assertEquals("SA.GOV.SAMA", merchant.string("merchant_payment_provider_name"))
        // The single template sits at tag 32, which the draft mapper reads as an account because
        // it is inside 26-51 and nests — the same rule that picks up a domestic tag 26.
        assertEquals("0000000003465480", merchant.string("merchant_pan"))
        assertEquals("010100", merchant.string("merchant_id"))
        assertTrue(merchant.isNull("merchant_id_national"))
        assertEquals("SAR", merchant.string("amount_currency"))
        assertEquals("100.00", merchant.string("amount"))
        assertEquals("SA", merchant.string("merchant_country_code"))
    }

    @Test
    fun `tags the contract has no field for are left out`() {
        // The whole document as text, because what is being asserted is an absence — and these
        // values are distinctive enough that finding them anywhere means they leaked.
        val text = export(reportOf(CROSS_BORDER))!!

        // Tag 62's reference label and its nested fee structure.
        assertFalse(text.contains("REF20260806000001"))
        assertFalse(text.contains("840000000"))
        // Tag 32's expiry, subtag 04.
        assertFalse(text.contains("E20280323T120000"))
    }

    @Test
    fun `a static code lets the payer choose the amount`() {
        val json = exportOf(payload(tlv("01", "11"), tlv("52", "0780"), tlv("53", "360")))

        assertEquals("static", json.obj("qr_data").string("qr_type"))
        assertEquals("INPUT_AMOUNT", json.merchantData().string("amount_type"))
    }

    @Test
    fun `a fixed tip is added to the amount`() {
        val merchant = exportOf(
            payload(
                tlv("01", "12"),
                tlv("53", "360"),
                tlv("54", "150000.00"),
                tlv("55", "02"),
                tlv("56", "5000.00"),
            ),
        ).merchantData()

        assertEquals("FIXED_TIPS", merchant.string("tips_type"))
        assertEquals("5000.00", merchant.string("tips"))
        assertEquals("IDR", merchant.string("tips_currency"))
        assertEquals("155000.00", merchant.obj("dynamic_qr").string("total_payment"))
    }

    @Test
    fun `a percentage tip stays a rate and is applied to the amount`() {
        val merchant = exportOf(
            payload(
                tlv("01", "12"),
                tlv("53", "360"),
                tlv("54", "200.00"),
                tlv("55", "03"),
                tlv("57", "5"),
            ),
        ).merchantData()

        assertEquals("PERCENTAGE_TIPS", merchant.string("tips_type"))
        // The rate the merchant wrote, not money. The consumer reads tips_type to know which.
        assertEquals("5", merchant.string("tips"))
        assertEquals("210.00", merchant.obj("dynamic_qr").string("total_payment"))
    }

    @Test
    fun `a prompted tip has no figure and no total`() {
        val merchant = exportOf(
            payload(tlv("01", "12"), tlv("53", "360"), tlv("54", "100.00"), tlv("55", "01")),
        ).merchantData()

        assertEquals("INPUT_TIPS", merchant.string("tips_type"))
        assertTrue(merchant.isNull("tips"))
        // The payer has not chosen yet, so there is no total to state.
        assertTrue(merchant.obj("dynamic_qr").isNull("total_payment"))
    }

    @Test
    fun `a code with no account template reports no merchant identity`() {
        val merchant = exportOf(payload(tlv("01", "11"), tlv("53", "360"))).merchantData()

        assertTrue(merchant.isNull("merchant_pan"))
        assertTrue(merchant.isNull("merchant_id"))
        assertTrue(merchant.isNull("merchant_payment_provider_name"))
        assertTrue(merchant.isNull("merchant_id_national"))
    }

    @Test
    fun `an unlisted currency falls back to its number`() {
        val json = exportOf(payload(tlv("01", "11"), tlv("53", "999"), tlv("58", "ID")))

        assertEquals("999", json.merchantData().string("amount_currency"))
        assertEquals(listOf("999"), json.arrayOfStrings("currency_allowed"))
        // Country matches but currency does not, and both halves are required.
        assertEquals("qrcrossborder", json.string("qr_type"))
    }

    @Test
    fun `the issuer fields nobody can scan are always present`() {
        val json = exportOf(ScanSamples.QRIS_DYNAMIC)

        assertEquals("000000012687", json.string("transaction_id"))
        assertEquals("6012", json.string("tran_code"))
        assertEquals("QRMerchant", json.string("qr_template_type"))
        assertEquals("CASA", json.string("banked_status"))
        assertEquals(listOf("CASA", "CC", "RPN", "LOCRC"), json.arrayOfStrings("sof_allowed"))
        assertEquals("25000000.00", json.obj("limit").string("limit_per_daily"))
        assertEquals("10000000.00", json.obj("limit").string("limit_per_transaction"))
        assertEquals(listOf("IDR"), json.arrayOfStrings("currency_allowed"))
        assertEquals(
            "Payment with QR",
            json.merchantData().obj("dynamic_qr").string("payment_type"),
        )
        assertFalse(json.merchantData()["is_amount_allowed_decimal"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `every export is valid JSON`() {
        for (payload in listOf(ScanSamples.QRIS_DYNAMIC, CROSS_BORDER)) {
            val text = export(reportOf(payload))
            assertNull(Json.parseToJsonElement(text!!).jsonObject["nothing"])
        }
    }

    private fun exportOf(payload: String): JsonObject =
        Json.parseToJsonElement(export(reportOf(payload))!!).jsonObject

    private fun reportOf(payload: String): QrInquiryReport =
        (parse(payload) as EmvParseResult.Success).value

    private fun JsonObject.merchantData(): JsonObject =
        obj("qr_data").obj("tran_merchant_data")

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.isNull(key: String): Boolean = getValue(key) is JsonNull

    private fun JsonObject.arrayOfStrings(key: String): List<String> =
        getValue(key).jsonArray.map { it.jsonPrimitive.content }

    private companion object {

        /**
         * The Saudi cross-border specimen, verbatim.
         *
         * One account template at tag `32`, an additional data template at tag `62` whose subtag
         * `54` is itself TLV, and a tag `65` — three shapes the domestic sample does not have.
         */
        const val CROSS_BORDER =
            "00020101021232710011SA.GOV.SAMA011600000000034654800206010100030201041" +
                "6E20280323T1200005204074253036825406100.005802SA5910MUSAADTest6007ALRIY" +
                "AD61051234562770517REF20260806000001100512453110340054360011SA.GOV.SAMA0" +
                "10515.000208400000006502SA63047484"

        fun tlv(tag: String, value: String): String =
            tag + value.length.toString().padStart(2, '0') + value

        /** A payload has to open with the format indicator and close with a checksum to parse. */
        fun payload(vararg segments: String): String =
            tlv("00", "01") + segments.joinToString(separator = "") + "6304ABCD"
    }
}
