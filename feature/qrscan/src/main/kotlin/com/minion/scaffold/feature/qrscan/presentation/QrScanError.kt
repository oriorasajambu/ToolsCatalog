package com.minion.scaffold.feature.qrscan.presentation

import com.minion.scaffold.core.emv.model.QrParseError

/**
 * Everything the screen can have to report, from either stage of the job.
 *
 * Finding a QR and reading one are different failures, and `QrParseError` deliberately covers only
 * the second — "there is no QR in this photo" is not a statement about EMV framing, and folding it
 * into the domain's error set would put a gallery concern inside the parser's contract. Wrapping
 * instead keeps `QrParseError` exhaustive over exactly what the parser can produce.
 */
internal sealed interface QrScanError {

    /** A payment code was found but could not be read. */
    data class Parse(val error: QrParseError) : QrScanError

    /**
     * A perfectly good QR code in a format this app does not handle — a URL, a contact card.
     *
     * Distinct from [Parse] because the two mean opposite things to the person holding the phone:
     * this one says "wrong kind of code", that one says "this code is damaged".
     */
    data object UnrecognisedFormat : QrScanError

    /** The picked image opened, and holds no QR code. */
    data object NoBarcodeInImage : QrScanError

    /** The picked image could not be opened or decoded. */
    data object ImageUnreadable : QrScanError
}
