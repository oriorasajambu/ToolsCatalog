package com.minion.scaffold.core.exif.png

import com.minion.scaffold.core.exif.jpeg.u8
import com.minion.scaffold.core.exif.model.ImageContainer
import com.minion.scaffold.core.exif.model.MetadataKind
import com.minion.scaffold.core.exif.model.PlanResult
import com.minion.scaffold.core.exif.model.SegmentSummary
import com.minion.scaffold.core.exif.model.StripFailure
import com.minion.scaffold.core.exif.model.StripOperation
import com.minion.scaffold.core.exif.model.StripPlan

/**
 * Decides what a cleaned PNG contains.
 *
 * PNG is the pleasant one. Every chunk is length-prefixed, four-character-named and CRC'd, so the
 * walk cannot lose its place the way a JPEG entropy scan can, and dropping a chunk is a matter of
 * not copying it — no offsets elsewhere refer to it and no checksum spans the whole file.
 *
 * Screenshots rather than camera photos, mostly, so the GPS risk is lower. What PNGs *do* carry is
 * text chunks: editing software writes its name and version, and Android screenshot pipelines have
 * been known to record rather more.
 *
 * The same allowlist rule as JPEG. Only the chunks that render the image survive; anything else goes
 * whether or not this module has heard of it.
 */
internal object PngStripPlanner {

    fun plan(bytes: ByteArray): PlanResult {
        if (bytes.size < SIGNATURE_LENGTH) {
            return failure(0, StripFailure.Defect.Truncated)
        }

        val operations = mutableListOf<StripOperation>(
            StripOperation.Copy(0, SIGNATURE_LENGTH),
        )
        val removed = mutableListOf<SegmentSummary>()
        val retained = mutableListOf<SegmentSummary>()

        var cursor = SIGNATURE_LENGTH
        var sawEnd = false

        while (cursor < bytes.size) {
            if (cursor + CHUNK_OVERHEAD > bytes.size) {
                return failure(cursor, StripFailure.Defect.Truncated)
            }

            val declared = bytes.u32(cursor)

            // A chunk length is a 31-bit value by specification. Reading it as signed and finding it
            // negative means either a hostile file or a misaligned walk; either way, stop.
            if (declared < 0 || declared > MAX_CHUNK_LENGTH) {
                return failure(cursor, StripFailure.Defect.BadLength)
            }

            val name = String(bytes, cursor + LENGTH_BYTES, NAME_BYTES, Charsets.US_ASCII)
            val chunkEnd = cursor + CHUNK_OVERHEAD + declared
            if (chunkEnd > bytes.size) {
                return failure(cursor, StripFailure.Defect.BadLength)
            }

            val size = chunkEnd - cursor
            when (val kind = metadataKind(name)) {
                null -> operations += StripOperation.Copy(cursor, chunkEnd)
                else -> removed += SegmentSummary(kind, name, size)
            }

            if (name == CHUNK_END) {
                sawEnd = true
                cursor = chunkEnd
                break
            }

            cursor = chunkEnd
        }

        if (!sawEnd) return failure(bytes.size, StripFailure.Defect.MissingEndMarker)

        return PlanResult.Success(
            StripPlan(
                container = ImageContainer.Png,
                operations = mergeAdjacent(operations),
                removed = removed,
                retained = retained,
                // Anything past IEND is outside the image, exactly as with a JPEG past EOI.
                trailing = trailingData(bytes, cursor),
            ),
        )
    }

    /**
     * What a chunk carries, or `null` when it is part of the image and must be kept.
     *
     * The kept set is the rendering-relevant one: header, palette, image data, transparency, gamma
     * and colour description, plus the end marker. Everything else — named or not — is metadata as
     * far as this tool is concerned.
     *
     * `iCCP` is kept unconditionally rather than following the ICC preference. Unlike a JPEG's
     * `APP2`, a PNG colour profile is compressed into the chunk and a viewer that finds the image
     * without it will render a wide-gamut screenshot visibly wrong, with no upside: there is no
     * plausible reading of a colour profile that identifies a person.
     */
    private fun metadataKind(name: String): MetadataKind? = when (name) {
        in RENDERING_CHUNKS -> null
        CHUNK_EXIF -> MetadataKind.Exif
        CHUNK_TEXT, CHUNK_COMPRESSED_TEXT -> MetadataKind.Comment
        CHUNK_INTERNATIONAL_TEXT -> MetadataKind.Xmp
        CHUNK_TIME -> MetadataKind.Timestamp
        else -> MetadataKind.Unknown
    }

    private fun trailingData(bytes: ByteArray, endOfImage: Int) =
        if (bytes.size > endOfImage) {
            com.minion.scaffold.core.exif.model.TrailingData(
                byteCount = bytes.size - endOfImage,
                kind = com.minion.scaffold.core.exif.model.TrailingKind.Unknown,
            )
        } else {
            null
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

    private const val SIGNATURE_LENGTH = 8
    private const val LENGTH_BYTES = 4
    private const val NAME_BYTES = 4
    private const val CRC_BYTES = 4
    private const val CHUNK_OVERHEAD = LENGTH_BYTES + NAME_BYTES + CRC_BYTES

    /** The specification's ceiling: chunk lengths are 31-bit. */
    private const val MAX_CHUNK_LENGTH = Int.MAX_VALUE - CHUNK_OVERHEAD

    private const val CHUNK_END = "IEND"
    private const val CHUNK_EXIF = "eXIf"
    private const val CHUNK_TEXT = "tEXt"
    private const val CHUNK_COMPRESSED_TEXT = "zTXt"
    private const val CHUNK_INTERNATIONAL_TEXT = "iTXt"
    private const val CHUNK_TIME = "tIME"

    private val RENDERING_CHUNKS = setOf(
        "IHDR", "PLTE", "IDAT", "IEND",
        "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "bKGD", "pHYs", "sPLT", "hIST",
        // APNG. Dropping these would silently turn an animation into its first frame.
        "acTL", "fcTL", "fdAT",
    )
}

/** Big-endian signed 32-bit value at [index]. PNG lengths are big-endian. */
internal fun ByteArray.u32(index: Int): Int =
    (u8(index) shl 24) or (u8(index + 1) shl 16) or (u8(index + 2) shl 8) or u8(index + 3)
