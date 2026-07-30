package com.minion.scaffold.feature.qrscan.presentation

import com.minion.scaffold.core.emv.model.CrcVerification
import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.usecase.ParseEmvPayloadUseCase
import com.minion.scaffold.core.wifi.model.WifiCredentials
import com.minion.scaffold.core.wifi.model.WifiSecurity
import com.minion.scaffold.core.wifi.usecase.ParseWifiPayloadUseCase

/**
 * A real payload for the previews, decoded by the real parser.
 *
 * Hand-built fixtures drift: a preview holding a made-up `QrInquiryReport` keeps rendering
 * happily after the parser's output shape changes, and the catalog quietly stops showing what the
 * screen actually does. Running the parse means the previews break when the decoding does.
 */
internal object QrScanPreviewData {

    /** A live Indonesian QRIS payload: dynamic, IDR 15,000,000.00, checksum `3D58`. */
    const val SAMPLE_PAYLOAD =
        "000201010212041553919900000019026710019ID.CO.CIMBNIAGA.WWW0118936000220000000282021" +
            "50000081600126050303UMI51450015ID.OR.QRNPG.WWW0215ID00000000001230303UMI5204078053" +
            "03360541115000000.005802ID5912PAK BOS QR 16006Bekasi61051715163043D58"

    /** A Wi-Fi code, decoded by the real parser for the same reason the payment one is. */
    const val SAMPLE_WIFI_PAYLOAD = "WIFI:T:WPA;S:Guest Network;P:hunter2!;H:true;;"

    val wifiCredentials: WifiCredentials =
        ParseWifiPayloadUseCase()(SAMPLE_WIFI_PAYLOAD)
            // Unreachable for a payload this file controls, and a preview must not throw — an
            // exception here takes down the whole Showcase catalog, not just this entry.
            ?: WifiCredentials(ssid = "", security = WifiSecurity.OPEN)

    val report: QrInquiryReport =
        when (val result = ParseEmvPayloadUseCase()(SAMPLE_PAYLOAD)) {
            is EmvParseResult.Success -> result.value
            // Unreachable for a payload this file controls, and a preview must not throw — an
            // exception here would take down the whole Showcase catalog, not just this entry.
            is EmvParseResult.Failure -> QrInquiryReport(
                payload = SAMPLE_PAYLOAD,
                segments = emptyList(),
                crc = CrcVerification(expected = "", actual = ""),
            )
        }
}
