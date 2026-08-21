package com.minion.scaffold.feature.qrscan.presentation

import com.minion.scaffold.core.emv.model.CrcVerification
import com.minion.scaffold.core.emv.model.EmvComparison
import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.usecase.CompareEmvPayloadsUseCase
import com.minion.scaffold.core.emv.usecase.ParseEmvPayloadUseCase
import com.minion.scaffold.core.wifi.model.WifiCredentials
import com.minion.scaffold.core.wifi.model.WifiSecurity
import com.minion.scaffold.core.wifi.usecase.ParseWifiPayloadUseCase
import com.minion.scaffold.feature.qrscan.domain.ScannedContent
import com.minion.scaffold.feature.qrscan.domain.compare.FieldComparison
import com.minion.scaffold.feature.qrscan.domain.compare.QrComparison

/**
 * A real payload for the previews, decoded by the real parser.
 *
 * Hand-built fixtures drift: a preview holding a made-up `QrInquiryReport` keeps rendering
 * happily after the parser's output shape changes, and the catalog quietly stops showing what the
 * screen actually does. Running the parse means the previews break when the decoding does.
 */
internal object QrScanPreviewData {

    // Declared first on purpose: the comparison properties below run at object
    // initialisation, and a property they read has to already hold a value by then.
    private val compareEmvPayloads = CompareEmvPayloadsUseCase()

    /** A live Indonesian QRIS payload: dynamic, IDR 15,000,000.00, checksum `3D58`. */
    const val SAMPLE_PAYLOAD =
        "000201010212041553919900000019026710019ID.CO.CIMBNIAGA.WWW0118936000220000000282021" +
            "50000081600126050303UMI51450015ID.OR.QRNPG.WWW0215ID00000000001230303UMI5204078053" +
            "03360541115000000.005802ID5912PAK BOS QR 16006Bekasi61051715163043D58"

    /**
     * The same merchant, for a different amount.
     *
     * Substituted digit for digit so the amount still declares eleven characters — a length change
     * would reframe everything after it and turn a one-field preview into a broken payload.
     */
    val SAMPLE_PAYLOAD_CHANGED: String = SAMPLE_PAYLOAD.replace("15000000.00", "25000000.00")

    /**
     * The same fields, re-encoded with the currency ahead of the merchant category.
     *
     * Two whole segments exchanged. This is the case the three-state verdict exists for: every
     * byte after the swap differs, and the code means exactly what the original did.
     */
    val SAMPLE_PAYLOAD_REORDERED: String =
        SAMPLE_PAYLOAD.replace("520407805303360", "530336052040780")

    /** A Wi-Fi code, decoded by the real parser for the same reason the payment one is. */
    const val SAMPLE_WIFI_PAYLOAD = "WIFI:T:WPA;S:Guest Network;P:hunter2!;H:true;;"

    /** The same network, re-issued with a new password and no longer hidden. */
    const val SAMPLE_WIFI_PAYLOAD_CHANGED = "WIFI:T:WPA;S:Guest Network;P:correcthorse;;"

    val wifiCredentials: WifiCredentials = credentialsOf(SAMPLE_WIFI_PAYLOAD)

    val report: QrInquiryReport = reportOf(SAMPLE_PAYLOAD)

    val paymentContent: ScannedContent = ScannedContent.Payment(report)

    val wifiContent: ScannedContent =
        ScannedContent.Wifi(payload = SAMPLE_WIFI_PAYLOAD, credentials = wifiCredentials)

    /** The same code scanned twice — what happens when the camera never left the first sticker. */
    val identicalComparison: QrComparison = comparisonOf(SAMPLE_PAYLOAD, SAMPLE_PAYLOAD)

    /** Same values, different bytes. */
    val equivalentComparison: QrComparison =
        comparisonOf(SAMPLE_PAYLOAD, SAMPLE_PAYLOAD_REORDERED)

    /** One field apart. */
    val changedComparison: QrComparison = comparisonOf(SAMPLE_PAYLOAD, SAMPLE_PAYLOAD_CHANGED)

    /** The structural half of [changedComparison], for previewing the field view on its own. */
    val changedEmvComparison: EmvComparison =
        compareEmvPayloads(reportOf(SAMPLE_PAYLOAD), reportOf(SAMPLE_PAYLOAD_CHANGED))

    /** Two Wi-Fi codes: a changed password and a hidden flag that is gone. */
    val wifiComparison: QrComparison = QrComparison(
        baseline = wifiContent,
        candidate = ScannedContent.Wifi(
            payload = SAMPLE_WIFI_PAYLOAD_CHANGED,
            credentials = credentialsOf(SAMPLE_WIFI_PAYLOAD_CHANGED),
        ),
        fields = FieldComparison.Flat,
    )

    private fun reportOf(payload: String): QrInquiryReport =
        when (val result = ParseEmvPayloadUseCase()(payload)) {
            is EmvParseResult.Success -> result.value
            // Unreachable for a payload this file controls, and a preview must not throw — an
            // exception here would take down the whole Showcase catalog, not just this entry.
            is EmvParseResult.Failure -> QrInquiryReport(
                payload = payload,
                segments = emptyList(),
                crc = CrcVerification(expected = "", actual = ""),
            )
        }

    private fun credentialsOf(payload: String): WifiCredentials =
        ParseWifiPayloadUseCase()(payload)
            // Unreachable for a payload this file controls, and a preview must not throw.
            ?: WifiCredentials(ssid = "", security = WifiSecurity.OPEN)

    /** Built by the real comparison, for the reason the reports are built by the real parser. */
    private fun comparisonOf(baseline: String, candidate: String): QrComparison {
        val first = ScannedContent.Payment(reportOf(baseline))
        val second = ScannedContent.Payment(reportOf(candidate))

        return QrComparison(
            baseline = first,
            candidate = second,
            fields = FieldComparison.Payment(compareEmvPayloads(first.report, second.report)),
        )
    }

}
