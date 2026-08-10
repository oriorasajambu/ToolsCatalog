package com.minion.scaffold.feature.qrscan.domain

import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.core.vcard.model.ContactCard
import com.minion.scaffold.core.wifi.model.WifiCredentials

/**
 * A code this app understands, and what it turned out to be.
 *
 * [payload] is common to both because everything downstream of recognizing a code needs the
 * original string: copying it, sharing it, and handing it to the right editor.
 */
internal sealed interface ScannedContent {

    val payload: String

    data class Payment(val report: QrInquiryReport) : ScannedContent {
        override val payload: String get() = report.payload
    }

    data class Wifi(
        override val payload: String,
        val credentials: WifiCredentials,
    ) : ScannedContent

    data class Web(
        override val payload: String,
        val url: String,
    ) : ScannedContent

    data class Contact(
        override val payload: String,
        val card: ContactCard,
    ) : ScannedContent
}

/**
 * What came of trying to read a scanned string.
 *
 * [Malformed] and [Unrecognised] are separate on purpose. "This is a URL" and "this payment code
 * is corrupt" are different things to tell someone, and collapsing them would undo the reason the
 * parser reports typed errors at all.
 */
internal sealed interface ScanResult {

    data class Recognised(val content: ScannedContent) : ScanResult

    /**
     * It was a payment code, and something about it does not hold up.
     *
     * [payload] is the **trimmed** string the offsets in [error] index. Carried rather than left to
     * the caller because the trim happens here: comparing against the untrimmed original would put
     * every reported position out by however much whitespace a scanner or clipboard added.
     */
    data class Malformed(val error: QrParseError, val payload: String) : ScanResult

    /** Neither a payment code nor Wi-Fi credentials. */
    data object Unrecognised : ScanResult
}
