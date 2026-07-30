package com.minion.scaffold.core.emv.model

/**
 * Every way reading an EMV payload can fail.
 *
 * Deliberately **not** a subtype of `DomainError`. Kotlin requires the direct subtypes of a sealed
 * interface to share its package *and* its module, so a feature module cannot extend the shared
 * hierarchy no matter how the documentation phrases it. That is not a real loss here: nothing in
 * this feature performs I/O, so no `DomainError` case can ever occur alongside these, and a
 * combined type would only add unreachable branches.
 *
 * The presentation layer maps these to `@StringRes`, exactly as `:core:ui` does for `DomainError`.
 * No case carries user-facing text.
 *
 * A failed checksum is **not** in this set. A payload whose CRC does not match still parses, and
 * reporting the mismatch is the entire point of the tool — turning it into a parse failure would
 * hide the one thing the user came to find out.
 */
sealed interface QrParseError {

    /** Nothing to parse. The scanner produced an empty string, or the input field is blank. */
    data object EmptyPayload : QrParseError

    /**
     * The payload is not EMV at all — a URL, a plain-text barcode, a WiFi credential block.
     *
     * Separate from [MalformedTlv] because the user action differs: this is "wrong kind of QR,
     * try another one", not "this QR is damaged".
     */
    data object NotAnEmvPayload : QrParseError

    /**
     * A segment header could not be read at [offset]: a non-numeric tag or length, or fewer than
     * four characters left to read one.
     *
     * [offset] indexes the payload, so it can be pointed at directly. A template whose *value*
     * fails to frame never reaches here — that segment is reported flat instead.
     */
    data class MalformedTlv(val offset: Int) : QrParseError

    /** Tag [tag] declared [declaredLength] characters but only [available] remain in the payload. */
    data class LengthOverrun(
        val tag: String,
        val declaredLength: Int,
        val available: Int,
    ) : QrParseError

    /** Tag `00` is absent, or is not the first segment. Every EMV payload opens with it. */
    data object MissingPayloadFormatIndicator : QrParseError

    /** Tag `63` is absent, is not the final segment, or does not hold exactly four characters. */
    data object MissingCrc : QrParseError
}
