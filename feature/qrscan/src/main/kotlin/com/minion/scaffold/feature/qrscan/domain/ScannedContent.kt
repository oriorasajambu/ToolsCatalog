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

    /** The original scanned string. */
    val payload: String

    /**
     * An EMV payment code.
     *
     * @property report The decoded inquiry report.
     */
    data class Payment(val report: QrInquiryReport) : ScannedContent {
        override val payload: String get() = report.payload
    }

    /**
     * A Wi-Fi credential code.
     *
     * @property payload     The original scanned string.
     * @property credentials The parsed network credentials.
     */
    data class Wifi(
        override val payload: String,
        val credentials: WifiCredentials,
    ) : ScannedContent

    /**
     * A web link.
     *
     * @property payload The original scanned string.
     * @property url     The parsed URL.
     */
    data class Web(
        override val payload: String,
        val url: String,
    ) : ScannedContent

    /**
     * A contact card.
     *
     * @property payload The original scanned string.
     * @property card    The parsed contact card.
     */
    data class Contact(
        override val payload: String,
        val card: ContactCard,
    ) : ScannedContent
}

/**
 * Which of the four kinds a code is, without its contents.
 *
 * Exists because comparing two codes has to ask "are these even the same sort of thing?" and name
 * the answer in a sentence — and `is ScannedContent.Wifi` is a check, not a noun. Keeping the
 * question separate from the data also means the rejection message needs no payload to talk about
 * a format.
 */
internal enum class ScannedFormat {
    Payment,
    Wifi,
    Web,
    Contact,
}

/** Which kind of code this is. */
internal val ScannedContent.format: ScannedFormat
    get() = when (this) {
        is ScannedContent.Payment -> ScannedFormat.Payment
        is ScannedContent.Wifi -> ScannedFormat.Wifi
        is ScannedContent.Web -> ScannedFormat.Web
        is ScannedContent.Contact -> ScannedFormat.Contact
    }

/**
 * What came of trying to read a scanned string.
 *
 * [Malformed] and [Unrecognised] are separate on purpose. "This is a URL" and "this payment code
 * is corrupt" are different things to tell someone, and collapsing them would undo the reason the
 * parser reports typed errors at all.
 */
internal sealed interface ScanResult {

    /**
     * The code was recognised and decoded.
     *
     * @property content What the code turned out to be.
     */
    data class Recognised(val content: ScannedContent) : ScanResult

    /**
     * It was a payment code, and something about it does not hold up.
     *
     * [payload] is the **trimmed** string the offsets in [error] index. Carried rather than left to
     * the caller because the trim happens here: comparing against the untrimmed original would put
     * every reported position out by however much whitespace a scanner or clipboard added.
     *
     * @property error   What is wrong with the payment code.
     * @property payload The trimmed payload the error's offsets index.
     */
    data class Malformed(val error: QrParseError, val payload: String) : ScanResult

    /** Neither a payment code nor Wi-Fi credentials. */
    data object Unrecognised : ScanResult
}
