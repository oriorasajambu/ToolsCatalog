package com.minion.scaffold.core.exif.jpeg

import com.minion.scaffold.core.exif.model.ImageContainer
import com.minion.scaffold.core.exif.model.MetadataKind
import com.minion.scaffold.core.exif.model.PlanResult
import com.minion.scaffold.core.exif.model.SegmentSummary
import com.minion.scaffold.core.exif.model.StripOperation
import com.minion.scaffold.core.exif.model.StripPlan
import com.minion.scaffold.core.exif.model.TrailingData
import com.minion.scaffold.core.exif.model.TrailingKind
import com.minion.scaffold.core.exif.model.startsWith

/**
 * Decides what a cleaned JPEG contains.
 *
 * ## The allowlist fails closed
 *
 * Only markers needed to decode the image survive, plus two deliberate exceptions. **Anything the
 * list does not name is dropped, recognised or not.** The alternative — removing the segments known
 * to carry metadata and passing the rest through — is easier to reason about and wrong for this
 * tool: it ships every vendor block intact, and vendor blocks are exactly where cameras put body and
 * lens serial numbers. A stripper whose coverage depends on someone having heard of a format is not
 * offering a guarantee, it is offering a best effort.
 *
 * The cost is real and worth naming: an unusual file needing some segment nobody anticipated would
 * come out broken. That is why every export is verified by re-reading it, and why the retained list
 * is shown rather than summarised as "done".
 */
internal object JpegStripPlanner {

    /**
     * Plans a metadata strip for a JPEG.
     *
     * @param bytes       The whole JPEG file.
     * @param orientation The EXIF orientation to preserve as a minimal Exif block.
     * @param keepIcc     Whether to retain an embedded ICC colour profile.
     * @return [PlanResult.Success] with the plan, or [PlanResult.Failure] when the marker scan fails.
     */
    fun plan(bytes: ByteArray, orientation: Int, keepIcc: Boolean): PlanResult {
        val scan = when (val result = JpegScanner.scan(bytes)) {
            is JpegScan.Failure -> return PlanResult.Failure(result.failure)
            is JpegScan.Success -> result
        }

        val operations = mutableListOf<StripOperation>()
        val removed = mutableListOf<SegmentSummary>()
        val retained = mutableListOf<SegmentSummary>()

        // A file this tool already cleaned carries an Exif block that is byte-for-byte one of ours.
        // Recognising it — rather than dropping it and writing an identical one back — is what makes
        // stripping idempotent, and it is what lets verification re-read an output and find nothing
        // to remove. Without it, every correctly cleaned photo would fail its own check.
        val alreadyMinimal = scan.segments.firstOrNull { segment ->
            segment.marker == JpegMarkers.APP1 &&
                MinimalExif.matches(bytes, segment.start, segment.endExclusive)
        }

        for (segment in scan.segments) {
            val decision = when (segment) {
                alreadyMinimal -> Decision.Keep(
                    SegmentSummary(
                        MetadataKind.Orientation,
                        ORIENTATION_LABEL,
                        segment.endExclusive - segment.start,
                    ),
                )

                else -> classify(bytes, segment, keepIcc)
            }

            when (decision) {
                is Decision.Keep -> {
                    operations += StripOperation.Copy(segment.start, segment.endExclusive)
                    decision.summary?.let(retained::add)
                }

                is Decision.Drop -> removed += decision.summary
            }

            // Inserted immediately after SOI, which is where a decoder expects to find it. Doing it
            // here rather than at the end keeps the output's segment order conventional.
            if (segment.marker == JpegMarkers.SOI && alreadyMinimal == null) {
                MinimalExif.segmentFor(orientation)?.let { block ->
                    operations += StripOperation.Insert(block)
                    retained += SegmentSummary(MetadataKind.Orientation, ORIENTATION_LABEL, block.size)
                }
            }
        }

        return PlanResult.Success(
            StripPlan(
                container = ImageContainer.Jpeg,
                operations = mergeAdjacent(operations),
                removed = removed,
                retained = retained,
                trailing = trailingData(bytes, scan.endOfImage),
            ),
        )
    }

    private sealed interface Decision {
        /** [summary] is set only for the things worth telling the user were kept on purpose. */
        data class Keep(val summary: SegmentSummary? = null) : Decision
        data class Drop(val summary: SegmentSummary) : Decision
    }

    private fun classify(bytes: ByteArray, segment: JpegSegment, keepIcc: Boolean): Decision {
        val marker = segment.marker
        val size = segment.endExclusive - segment.start

        // Structure and image data. Nothing here can carry metadata, and all of it is needed.
        val structural = marker == JpegMarkers.SOI ||
            marker == JpegMarkers.EOI ||
            marker == JpegMarkers.SOS ||
            marker == JpegMarkers.DQT ||
            marker == JpegMarkers.DNL ||
            marker == JpegMarkers.DRI ||
            marker in JpegMarkers.RESTART_RANGE ||
            (marker in JpegMarkers.FRAME_RANGE && marker != JpegMarkers.JPG_RESERVED)

        if (structural) return Decision.Keep()

        return when (marker) {
            JpegMarkers.APP0 -> classifyApp0(bytes, segment, size)
            JpegMarkers.APP1 -> Decision.Drop(
                SegmentSummary(
                    kind = when {
                        payloadStartsWith(bytes, segment, EXIF_IDENTIFIER) -> MetadataKind.Exif
                        payloadStartsWith(bytes, segment, XMP_IDENTIFIER) -> MetadataKind.Xmp
                        else -> MetadataKind.Unknown
                    },
                    name = "APP1",
                    byteCount = size,
                ),
            )

            JpegMarkers.APP2 -> classifyApp2(bytes, segment, size, keepIcc)

            JpegMarkers.APP13 -> Decision.Drop(
                SegmentSummary(
                    kind = if (payloadStartsWith(bytes, segment, PHOTOSHOP_IDENTIFIER)) {
                        MetadataKind.Iptc
                    } else {
                        MetadataKind.Unknown
                    },
                    name = "APP13",
                    byteCount = size,
                ),
            )

            JpegMarkers.COM -> Decision.Drop(
                SegmentSummary(MetadataKind.Comment, "COM", size),
            )

            else -> Decision.Drop(
                SegmentSummary(MetadataKind.Unknown, markerName(marker), size),
            )
        }
    }

    /**
     * `APP0` is kept only when it is a plain JFIF header with no thumbnail of its own.
     *
     * The standard 14-byte JFIF payload carries density information and nothing identifying. But the
     * same marker can hold an embedded thumbnail, and a `JFXX` extension segment holds one outright
     * — so the rule is length and content, not the marker number.
     */
    private fun classifyApp0(bytes: ByteArray, segment: JpegSegment, size: Int): Decision {
        val plainJfif = payloadStartsWith(bytes, segment, JFIF_IDENTIFIER) &&
            segment.payloadLength == JFIF_PAYLOAD_LENGTH &&
            bytes.u8(segment.payloadStart + JFIF_THUMBNAIL_WIDTH_OFFSET) == 0 &&
            bytes.u8(segment.payloadStart + JFIF_THUMBNAIL_HEIGHT_OFFSET) == 0

        return if (plainJfif) {
            Decision.Keep()
        } else {
            Decision.Drop(SegmentSummary(MetadataKind.Unknown, "APP0", size))
        }
    }

    /**
     * `APP2` is either a colour profile or something considerably less innocent.
     *
     * `MPF` — the multi-picture format Samsung and Apple use — indexes *additional whole images*
     * inside the same file, which is a leak of a different order from a tag. It is dropped whatever
     * the ICC preference says.
     */
    private fun classifyApp2(
        bytes: ByteArray,
        segment: JpegSegment,
        size: Int,
        keepIcc: Boolean,
    ): Decision = when {
        payloadStartsWith(bytes, segment, ICC_IDENTIFIER) -> if (keepIcc) {
            Decision.Keep(SegmentSummary(MetadataKind.IccProfile, "APP2", size))
        } else {
            Decision.Drop(SegmentSummary(MetadataKind.IccProfile, "APP2", size))
        }

        else -> Decision.Drop(SegmentSummary(MetadataKind.Unknown, "APP2", size))
    }

    /**
     * Whatever sits past the end of the image.
     *
     * Sniffs for an ISO base-media `ftyp` box so the report can say "an embedded video" rather than
     * quoting a byte count at someone. Motion photos put the MP4 immediately after `EOI`, sometimes
     * behind a short vendor header, so the search covers the first stretch rather than only offset
     * four.
     */
    private fun trailingData(bytes: ByteArray, endOfImage: Int): TrailingData? {
        val length = bytes.size - endOfImage
        if (length <= 0) return null

        val searchEnd = minOf(bytes.size, endOfImage + FTYP_SEARCH_WINDOW)
        var index = endOfImage
        while (index < searchEnd) {
            if (bytes.startsWith(FTYP_MARKER, index)) {
                return TrailingData(length, TrailingKind.EmbeddedVideo)
            }
            index++
        }

        return TrailingData(length, TrailingKind.Unknown)
    }

    /**
     * Joins copies that touch, so the executor issues one read per run rather than one per segment.
     *
     * Cosmetic for correctness and not for performance: a photo's scan data is a single enormous
     * range already, and this mostly collapses the tables that precede it.
     */
    private fun mergeAdjacent(operations: List<StripOperation>): List<StripOperation> =
        operations.fold(mutableListOf()) { merged, operation ->
            val previous = merged.lastOrNull()
            if (
                operation is StripOperation.Copy &&
                previous is StripOperation.Copy &&
                previous.endExclusive == operation.start
            ) {
                merged[merged.lastIndex] = StripOperation.Copy(previous.start, operation.endExclusive)
            } else {
                merged += operation
            }
            merged
        }

    private fun payloadStartsWith(
        bytes: ByteArray,
        segment: JpegSegment,
        identifier: ByteArray,
    ): Boolean = segment.hasPayload &&
        segment.payloadLength >= identifier.size &&
        bytes.startsWith(identifier, segment.payloadStart)

    private fun markerName(marker: Int): String = when (marker) {
        in JpegMarkers.APP_RANGE -> "APP${marker - JpegMarkers.APP0}"
        else -> "0x%02X".format(marker)
    }

    private const val ORIENTATION_LABEL = "Orientation"

    // NUL terminators written as explicit escapes. A literal NUL byte in a source file is valid
    // Kotlin and invisible in every editor, which is not a combination worth having in the one
    // place that decides whether a segment is Exif or something pretending to be.
    private val EXIF_IDENTIFIER = "Exif  ".toByteArray(Charsets.US_ASCII)
    private val XMP_IDENTIFIER = "http://ns.adobe.com/xap/1.0/ ".toByteArray(Charsets.US_ASCII)
    private val ICC_IDENTIFIER = "ICC_PROFILE ".toByteArray(Charsets.US_ASCII)
    private val PHOTOSHOP_IDENTIFIER = "Photoshop 3.0 ".toByteArray(Charsets.US_ASCII)
    private val JFIF_IDENTIFIER = "JFIF ".toByteArray(Charsets.US_ASCII)
    private val FTYP_MARKER = "ftyp".toByteArray(Charsets.US_ASCII)

    /** Identifier 5 + version 2 + units 1 + densities 4 + thumbnail dimensions 2. */
    private const val JFIF_PAYLOAD_LENGTH = 14
    private const val JFIF_THUMBNAIL_WIDTH_OFFSET = 12
    private const val JFIF_THUMBNAIL_HEIGHT_OFFSET = 13

    private const val FTYP_SEARCH_WINDOW = 256
}
