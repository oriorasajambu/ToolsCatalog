package com.minion.scaffold.feature.qrscan.domain

import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.core.emv.usecase.ParseEmvPayloadUseCase
import com.minion.scaffold.core.url.usecase.ParseUrlPayloadUseCase
import com.minion.scaffold.core.vcard.usecase.ParseVCardPayloadUseCase
import com.minion.scaffold.core.wifi.usecase.ParseWifiPayloadUseCase
import javax.inject.Inject

/**
 * Works out which format a scanned string is, then reads it.
 *
 * Wi-Fi, vCard and URL each announce themselves — `WIFI:`, `BEGIN:VCARD`, `http` — so they are all
 * tried before EMV, which has **no marker at all**: it is recognized by framing as TLV, which
 * almost any digit string is a candidate for. Putting EMV anywhere but last would have it claim
 * payloads it merely happens to be able to frame.
 *
 * The distinction this exists to preserve: [QrParseError.NotAnEmvPayload] from a payload that is
 * also not Wi-Fi means "we do not know this format", while any other `QrParseError` means it *was*
 * a payment code and is broken. Reporting both the same way would tell someone their perfectly
 * good boarding pass is a corrupt payment code.
 */
internal class DecodeScannedPayloadUseCase @Inject constructor(
    private val parseWifiPayload: ParseWifiPayloadUseCase,
    private val parseVCardPayload: ParseVCardPayloadUseCase,
    private val parseUrlPayload: ParseUrlPayloadUseCase,
    private val parseEmvPayload: ParseEmvPayloadUseCase,
) {

    operator fun invoke(payload: String): ScanResult {
        val trimmed = payload.trim()

        // Every self-identifying format first, in any order among themselves — each is one prefix
        // comparison and a match is certain rather than probable.
        parseWifiPayload(payload)?.let { credentials ->
            return ScanResult.Recognised(ScannedContent.Wifi(trimmed, credentials))
        }
        parseVCardPayload(payload)?.let { card ->
            return ScanResult.Recognised(ScannedContent.Contact(trimmed, card))
        }
        parseUrlPayload(payload)?.let { url ->
            return ScanResult.Recognised(ScannedContent.Web(trimmed, url))
        }

        return when (val parsed = parseEmvPayload(payload)) {
            is EmvParseResult.Success ->
                ScanResult.Recognised(ScannedContent.Payment(parsed.value))

            is EmvParseResult.Failure -> when (parsed.error) {
                // Not EMV either, and Wi-Fi already declined it.
                QrParseError.NotAnEmvPayload -> ScanResult.Unrecognised
                else -> ScanResult.Malformed(parsed.error)
            }
        }
    }
}
