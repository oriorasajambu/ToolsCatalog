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
 * ## Every case carries a [span]
 *
 * Declared on the interface rather than on each case, so a renderer highlighting the damage asks
 * one question and never has to `when` over the hierarchy to find out what to point at. Only the
 * *wording* varies by case, and wording is the feature's job.
 *
 * ## There is no `expected` field, on purpose
 *
 * The case **is** the expectation: [MissingCrc] means "expected tag 63 holding four characters",
 * [MissingPayloadFormatIndicator] means "expected tag 00 first". Adding a parallel `expected` enum
 * would duplicate this hierarchy and let the two drift out of agreement. The one case that genuinely
 * needs sub-discrimination is [MalformedTlv], which is why [HeaderDefect] exists and nothing else
 * does.
 *
 * A failed checksum is **not** in this set. A payload whose CRC does not match still parses, and
 * reporting the mismatch is the entire point of the tool — turning it into a parse failure would
 * hide the one thing the user came to find out.
 */
sealed interface QrParseError {

    /**
     * The payload characters this error is about — the one thing a renderer highlights.
     *
     * Always indexes the trimmed payload. See [PayloadSpan].
     */
    val span: PayloadSpan

    /** Nothing to parse. The scanner produced an empty string, or the input field is blank. */
    data object EmptyPayload : QrParseError {

        /** Nothing to point at. A renderer must skip the highlight entirely rather than draw a caret. */
        override val span: PayloadSpan = PayloadSpan.at(0)
    }

    /**
     * The payload is not EMV at all — a URL, a plain-text barcode, a WiFi credential block.
     *
     * Separate from [MalformedTlv] because the user action differs: this is "wrong kind of QR,
     * try another one", not "this QR is damaged".
     *
     * [span] covers the leading characters that should have been a numeric tag.
     */
    data class NotAnEmvPayload(
        override val span: PayloadSpan,
        val found: String,
    ) : QrParseError

    /**
     * A segment header could not be read.
     *
     * [offset] is where the header *attempt* began, so it names the segment that failed.
     * [span] narrows to the two characters actually at fault, which is usually not the same place:
     * a valid tag followed by a non-numeric length fails at `offset + 2`, and pointing at `offset`
     * would accuse two perfectly good characters.
     *
     * [lastGoodSegment] is null only when the very first segment fails.
     */
    data class MalformedTlv(
        val offset: Int,
        override val span: PayloadSpan,
        val defect: HeaderDefect,
        val found: String,
        val lastGoodSegment: SegmentTrace?,
    ) : QrParseError

    /**
     * Tag [tag] declared [declaredLength] characters but only [available] remain in the payload.
     *
     * [span] runs from the segment's header to the end of the payload — what "runs off the end"
     * looks like when drawn.
     */
    data class LengthOverrun(
        val tag: String,
        val declaredLength: Int,
        val available: Int,
        val offset: Int,
        override val span: PayloadSpan,
        val lastGoodSegment: SegmentTrace?,
    ) : QrParseError

    /**
     * Tag `00` is absent, or is not the first segment. Every EMV payload opens with it.
     *
     * [span] covers the segment that was found in its place, so the highlight lands on something a
     * reader can act on rather than on character zero.
     */
    data class MissingPayloadFormatIndicator(
        override val span: PayloadSpan,
        val foundTag: String,
    ) : QrParseError

    /**
     * Tag `63` is absent, is not the final segment, or does not hold exactly four characters.
     *
     * [span] covers the *last* segment rather than a zero-width caret at the tail: for
     * `00020163043D585802ID` that highlights `5802ID` and lets the message say the payload ends on
     * tag 58, which is the actionable form of "no CRC at the end".
     */
    data class MissingCrc(
        override val span: PayloadSpan,
        val foundTag: String,
        val foundLength: Int,
    ) : QrParseError
}
