package com.minion.scaffold.core.exif.jpeg

import com.minion.scaffold.core.exif.model.StripFailure

/**
 * JPEG marker numbers, as the bytes that follow `0xFF`.
 *
 * Only the ones this module reasons about individually. The rest are handled by range.
 */
internal object JpegMarkers {
    const val SOI = 0xD8
    const val EOI = 0xD9
    const val SOS = 0xDA
    const val DQT = 0xDB
    const val DNL = 0xDC
    const val DRI = 0xDD
    const val COM = 0xFE
    const val TEM = 0x01
    const val APP0 = 0xE0
    const val APP1 = 0xE1
    const val APP2 = 0xE2
    const val APP13 = 0xED

    /** `SOF0`–`SOF15`, with `DHT` and `DAC` interleaved among them. */
    val FRAME_RANGE = 0xC0..0xCF

    /** Reserved inside [FRAME_RANGE] with no defined meaning, so not kept. */
    const val JPG_RESERVED = 0xC8

    /** Restart markers. These live *inside* the entropy-coded scan. */
    val RESTART_RANGE = 0xD0..0xD7

    val APP_RANGE = 0xE0..0xEF
}

/**
 * One marker segment, located in the input.
 *
 * [endExclusive] for `SOS` covers the entropy-coded scan data as well, so a single [start] to
 * [endExclusive] copy carries the whole image payload across untouched.
 */
internal data class JpegSegment(
    val marker: Int,
    /** The `0xFF` that introduces the marker. Fill bytes before it are excluded. */
    val start: Int,
    val endExclusive: Int,
    /** First byte after the 2-byte length, or `-1` for markers that carry no payload. */
    val payloadStart: Int,
    val payloadLength: Int,
) {
    val hasPayload: Boolean get() = payloadStart >= 0
}

internal sealed interface JpegScan {
    data class Success(
        val segments: List<JpegSegment>,
        /** Offset just past the `EOI` marker. Anything beyond this is trailing data. */
        val endOfImage: Int,
    ) : JpegScan

    data class Failure(val failure: StripFailure) : JpegScan
}

/**
 * Walks a JPEG's marker structure.
 *
 * ## The entropy-coded scan is the part that is easy to get wrong
 *
 * After `SOS` the compressed image data is *not* length-prefixed — it runs until the next real
 * marker. Finding that marker means understanding three things that all look like markers and are
 * not:
 *
 *  - **`FF 00` is a stuffed byte.** The encoder inserts the `00` so a literal `0xFF` in the
 *    compressed data cannot be mistaken for a marker. Treat it as one and the scan ends at the first
 *    such byte, truncating the image — which on a typical photo happens within the first few
 *    kilobytes and produces a grey rectangle below the cut.
 *  - **`FF D0`–`FF D7` are restart markers**, deliberately embedded in the scan at intervals.
 *  - **`FF FF` is fill**, and may repeat any number of times before a real marker.
 *
 * All three have their own test, because a small hand-built fixture that happens to contain none of
 * them will pass a walker that gets every one of them wrong.
 *
 * ## Bounds
 *
 * Every read is bounds-checked and every failure is reported with an offset. Files arrive from a
 * picker and may be truncated, partially downloaded, or built to be hostile; the parser has to stop
 * rather than loop, over-read, or — worst of all — succeed.
 */
internal object JpegScanner {

    fun scan(bytes: ByteArray): JpegScan {
        if (bytes.size < MINIMUM_SIZE) return truncated(0)
        if (bytes.u8(0) != 0xFF || bytes.u8(1) != JpegMarkers.SOI) {
            return malformed(0, StripFailure.Defect.UnexpectedStructure)
        }

        val segments = mutableListOf(
            JpegSegment(JpegMarkers.SOI, start = 0, endExclusive = 2, payloadStart = -1, payloadLength = 0),
        )
        var cursor = 2

        while (true) {
            if (cursor >= bytes.size) return truncated(cursor)
            if (bytes.u8(cursor) != 0xFF) {
                return malformed(cursor, StripFailure.Defect.UnexpectedStructure)
            }

            // Any number of fill bytes may precede a marker. Skipping to the last one means the
            // segment starts at exactly `FF <marker>`, so fill is simply never copied.
            val markerAt = skipFill(bytes, cursor)
            if (markerAt >= bytes.size) return truncated(markerAt)

            val marker = bytes.u8(markerAt)

            // `FF 00` is a stuffed byte and belongs only inside the scan. Meeting one out here means
            // the walk has lost its place — most likely the entropy scan ended in the wrong spot,
            // which is precisely the bug that must not be allowed to produce output.
            if (marker == 0x00) {
                return malformed(markerAt, StripFailure.Defect.UnexpectedStructure)
            }

            val start = markerAt - 1
            val afterMarker = markerAt + 1

            if (marker == JpegMarkers.EOI) {
                segments += JpegSegment(marker, start, afterMarker, -1, 0)
                return JpegScan.Success(segments, endOfImage = afterMarker)
            }

            if (isStandalone(marker)) {
                segments += JpegSegment(marker, start, afterMarker, -1, 0)
                cursor = afterMarker
                continue
            }

            when (val read = readPayloadSegment(bytes, marker, start, afterMarker)) {
                is SegmentRead.Failed -> return read.scan
                is SegmentRead.Ok -> {
                    segments += read.segment
                    cursor = read.nextCursor
                }
            }
        }
    }

    /**
     * Whether [marker] is one that carries no payload at all.
     *
     * `TEM` and the eight restart markers are two bytes and nothing else — no length field follows,
     * so reading one as though it had a payload would consume the bytes after it.
     */
    private fun isStandalone(marker: Int): Boolean =
        marker == JpegMarkers.TEM || marker in JpegMarkers.RESTART_RANGE

    /** Walks past any run of `0xFF` fill starting at [from], to the marker byte itself. */
    private fun skipFill(bytes: ByteArray, from: Int): Int {
        var index = from
        while (index < bytes.size && bytes.u8(index) == 0xFF) index++
        return index
    }

    /**
     * Reads a length-bearing segment, and works out where the next one starts.
     *
     * Every way the framing can be wrong lives here — a length field that does not fit, a declared
     * length below its own two bytes, and a payload running past the end of the file. Gathering
     * them out of [scan] leaves the walk reading as the walk it is.
     *
     * `SOS` is the exception that shapes the return: its segment does not end where its payload
     * does but after the entropy-coded data that follows, so the next cursor cannot be derived from
     * the segment alone and is returned alongside it.
     */
    private fun readPayloadSegment(
        bytes: ByteArray,
        marker: Int,
        start: Int,
        afterMarker: Int,
    ): SegmentRead {
        if (afterMarker + 1 >= bytes.size) return SegmentRead.Failed(truncated(afterMarker))

        // The declared length counts itself, so anything below 2 is nonsense rather than an
        // empty segment — and a naive parser would advance by a negative amount and loop.
        val declared = bytes.u16(afterMarker)
        if (declared < 2) {
            return SegmentRead.Failed(malformed(afterMarker, StripFailure.Defect.BadLength))
        }

        val payloadStart = afterMarker + 2
        val payloadLength = declared - 2
        val payloadEnd = payloadStart + payloadLength
        if (payloadEnd > bytes.size) {
            return SegmentRead.Failed(malformed(afterMarker, StripFailure.Defect.BadLength))
        }

        if (marker != JpegMarkers.SOS) {
            return SegmentRead.Ok(
                segment = JpegSegment(marker, start, payloadEnd, payloadStart, payloadLength),
                nextCursor = payloadEnd,
            )
        }

        val scanEnd = endOfEntropyData(bytes, payloadEnd)
        if (scanEnd >= bytes.size) return SegmentRead.Failed(missingEnd(bytes.size))

        return SegmentRead.Ok(
            segment = JpegSegment(marker, start, scanEnd, payloadStart, payloadLength),
            nextCursor = scanEnd,
        )
    }

    /** One length-bearing segment read: the segment and where to continue, or why it failed. */
    private sealed interface SegmentRead {

        /**
         * The segment framed.
         *
         * @property segment    The segment read.
         * @property nextCursor Where the walk continues.
         */
        data class Ok(val segment: JpegSegment, val nextCursor: Int) : SegmentRead

        /**
         * The segment did not frame.
         *
         * @property scan The finished failure to return from [scan].
         */
        data class Failed(val scan: JpegScan) : SegmentRead
    }

    /**
     * Finds where the compressed scan data ends — the offset of the next real marker's `0xFF`.
     *
     * See the class note. The three non-marker cases are the whole substance of this function.
     */
    private fun endOfEntropyData(bytes: ByteArray, from: Int): Int {
        var index = from

        while (index < bytes.size - 1) {
            if (bytes.u8(index) != 0xFF) {
                index++
                continue
            }

            when (val next = bytes.u8(index + 1)) {
                // A stuffed byte: the 0xFF is image data, not a marker.
                0x00 -> index += 2

                // Fill. Advance one so a run of them is consumed a byte at a time.
                0xFF -> index += 1

                else -> if (next in JpegMarkers.RESTART_RANGE) {
                    // Part of the scan, by design — it is how a decoder resynchronises.
                    index += 2
                } else {
                    return index
                }
            }
        }

        // Ran off the end without meeting a marker. The caller turns this into a failure rather
        // than silently treating the rest of the file as image data.
        return bytes.size
    }

    private fun truncated(offset: Int) =
        JpegScan.Failure(StripFailure.Malformed(offset, StripFailure.Defect.Truncated))

    private fun missingEnd(offset: Int) =
        JpegScan.Failure(StripFailure.Malformed(offset, StripFailure.Defect.MissingEndMarker))

    private fun malformed(offset: Int, defect: StripFailure.Defect) =
        JpegScan.Failure(StripFailure.Malformed(offset, defect))

    /** `SOI` plus `EOI` plus a marker byte — below this there is nothing to parse. */
    private const val MINIMUM_SIZE = 4
}

/** Unsigned byte at [index]. The parser reasons in `Int` throughout; Kotlin's `Byte` is signed. */
internal fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

/** Big-endian unsigned 16-bit value at [index]. JPEG is big-endian everywhere. */
internal fun ByteArray.u16(index: Int): Int = (u8(index) shl 8) or u8(index + 1)
