package com.minion.scaffold.core.exif.webp

import com.minion.scaffold.core.exif.jpeg.u8
import com.minion.scaffold.core.exif.model.ImageContainer
import com.minion.scaffold.core.exif.model.MetadataKind
import com.minion.scaffold.core.exif.model.PlanResult
import com.minion.scaffold.core.exif.model.SegmentSummary
import com.minion.scaffold.core.exif.model.StripFailure
import com.minion.scaffold.core.exif.model.StripOperation
import com.minion.scaffold.core.exif.model.StripPlan

/**
 * Decides what a cleaned WebP contains.
 *
 * ## The RIFF size field has to be rewritten, and that is the whole difficulty
 *
 * A WebP is a RIFF container: the twelve-byte header declares how many bytes follow, then chunks
 * run to the end. Dropping a chunk changes that count, so the header has to be rebuilt — and this is
 * the one format here where the output is not simply a subset of the input's bytes.
 *
 * Getting it wrong is nastier than it sounds. Several decoders, Android's included, ignore the
 * declared size and read to the end of the file, so a wrong value produces something that opens
 * perfectly on the device that made it and fails elsewhere. There is a test asserting the field
 * matches the real remaining length for exactly that reason.
 *
 * ## The extended-format flag
 *
 * A WebP carrying metadata is by definition an extended-format file (`VP8X`), whose first chunk
 * holds flag bits declaring what else is present. Removing the EXIF and XMP chunks without clearing
 * those bits leaves a file announcing metadata that is not there — legal enough that most decoders
 * shrug, and untidy in a way this tool should not be. The flags are cleared in place.
 */
internal object WebPStripPlanner {

    /**
     * Plans a metadata strip for a WebP.
     *
     * @param bytes The whole WebP file.
     * @return [PlanResult.Success] with the plan, or [PlanResult.Failure] when the chunk walk fails.
     */
    fun plan(bytes: ByteArray): PlanResult {
        if (bytes.size < HEADER_LENGTH) {
            return failure(0, StripFailure.Defect.Truncated)
        }

        val chunks = mutableListOf<StripOperation>()
        val removed = mutableListOf<SegmentSummary>()
        var vp8xPayloadStart = -1

        var cursor = HEADER_LENGTH
        while (cursor < bytes.size) {
            if (cursor + CHUNK_HEADER > bytes.size) {
                return failure(cursor, StripFailure.Defect.Truncated)
            }

            val name = String(bytes, cursor, NAME_BYTES, Charsets.US_ASCII)
            val declared = bytes.leU32(cursor + NAME_BYTES)
            if (declared < 0) return failure(cursor, StripFailure.Defect.BadLength)

            // RIFF pads odd-length payloads to an even boundary, and the pad byte is not counted in
            // the declared size. Missing this shifts every subsequent chunk by one.
            val padded = declared + (declared and 1)
            val chunkEnd = cursor + CHUNK_HEADER + padded
            if (chunkEnd > bytes.size) return failure(cursor, StripFailure.Defect.BadLength)

            val size = chunkEnd - cursor
            when (val kind = metadataKind(name)) {
                null -> {
                    if (name == CHUNK_VP8X) vp8xPayloadStart = cursor + CHUNK_HEADER
                    chunks += StripOperation.Copy(cursor, chunkEnd)
                }

                else -> removed += SegmentSummary(kind, name, size)
            }

            cursor = chunkEnd
        }

        val merged = mergeAdjacent(chunks)
        val payloadLength = merged.sumOf { (it as StripOperation.Copy).length }

        val operations = buildList {
            add(StripOperation.Insert(riffHeader(bytes, payloadLength)))
            addAll(withClearedFlags(bytes, merged, vp8xPayloadStart))
        }

        return PlanResult.Success(
            StripPlan(
                container = ImageContainer.WebP,
                operations = operations,
                removed = removed,
                retained = emptyList(),
                // A RIFF file is exactly as long as its header says, so there is no notion of data
                // past the end — anything extra was already excluded by the chunk walk above.
                trailing = null,
            ),
        )
    }

    /**
     * The twelve-byte header, with the size field set to what will actually follow.
     *
     * The count covers everything after the size field itself, which includes the four bytes of
     * `WEBP` — hence the addition rather than the plain payload length.
     */
    private fun riffHeader(bytes: ByteArray, payloadLength: Int): ByteArray {
        val declared = payloadLength + FORM_TYPE_BYTES

        return byteArrayOf(
            bytes[0], bytes[1], bytes[2], bytes[3],
            (declared and 0xFF).toByte(),
            ((declared shr 8) and 0xFF).toByte(),
            ((declared shr 16) and 0xFF).toByte(),
            ((declared shr 24) and 0xFF).toByte(),
            bytes[8], bytes[9], bytes[10], bytes[11],
        )
    }

    /**
     * Replaces the `VP8X` copy with an edited one whose EXIF and XMP flags are off.
     *
     * The only place this module rewrites a chunk's contents rather than copying or dropping it
     * whole. Two bits, at a fixed offset, in a chunk whose layout is fully specified.
     */
    private fun withClearedFlags(
        bytes: ByteArray,
        operations: List<StripOperation>,
        vp8xPayloadStart: Int,
    ): List<StripOperation> {
        if (vp8xPayloadStart < 0) return operations

        return operations.flatMap { operation ->
            val copy = operation as StripOperation.Copy
            val flagsAt = vp8xPayloadStart
            if (flagsAt !in copy.start until copy.endExclusive) return@flatMap listOf(operation)

            val edited = bytes.u8(flagsAt) and
                (EXIF_FLAG or XMP_FLAG).inv() and
                0xFF

            listOfNotNull(
                StripOperation.Copy(copy.start, flagsAt).takeIf { it.length > 0 },
                StripOperation.Insert(byteArrayOf(edited.toByte())),
                StripOperation.Copy(flagsAt + 1, copy.endExclusive).takeIf { it.length > 0 },
            )
        }
    }

    private fun metadataKind(name: String): MetadataKind? = when (name) {
        CHUNK_EXIF -> MetadataKind.Exif
        CHUNK_XMP -> MetadataKind.Xmp
        in RENDERING_CHUNKS -> null
        else -> MetadataKind.Unknown
    }

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

    private fun failure(offset: Int, defect: StripFailure.Defect) =
        PlanResult.Failure(StripFailure.Malformed(offset, defect))

    private const val HEADER_LENGTH = 12
    private const val NAME_BYTES = 4
    private const val SIZE_BYTES = 4
    private const val CHUNK_HEADER = NAME_BYTES + SIZE_BYTES
    private const val FORM_TYPE_BYTES = 4

    private const val CHUNK_VP8X = "VP8X"
    private const val CHUNK_EXIF = "EXIF"
    private const val CHUNK_XMP = "XMP "

    /** Bit 3 is EXIF, bit 2 is XMP, in the first byte of the `VP8X` payload. */
    private const val EXIF_FLAG = 0x08
    private const val XMP_FLAG = 0x04

    private val RENDERING_CHUNKS = setOf(
        "VP8 ", "VP8L", "VP8X", "ALPH",
        // Animation. Dropping these would turn an animated WebP into a still.
        "ANIM", "ANMF",
        "ICCP",
    )
}

/** Little-endian signed 32-bit value at [index]. RIFF is little-endian, unlike everything else. */
internal fun ByteArray.leU32(index: Int): Int =
    u8(index) or (u8(index + 1) shl 8) or (u8(index + 2) shl 16) or (u8(index + 3) shl 24)
