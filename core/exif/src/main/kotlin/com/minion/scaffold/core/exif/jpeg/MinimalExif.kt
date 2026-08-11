package com.minion.scaffold.core.exif.jpeg

/**
 * Builds the one EXIF block a cleaned file is allowed to keep: orientation, and nothing else.
 *
 * ## Why anything survives at all
 *
 * Orientation *is* EXIF. Cameras write the sensor's pixels as they came off it and record "rotate
 * this on display" as a tag. Strip it with everything else and every portrait photo comes out
 * sideways — an immediate, visible regression that a user will read as the tool corrupting their
 * pictures, and one they will notice long before they appreciate the privacy they gained.
 *
 * The alternative is rotating the pixels so no tag is needed, which means re-encoding, which throws
 * away the entire reason for doing byte-level surgery. So one tag is kept, and the UI says which one
 * and why. What it reveals is a number from 1 to 8: not the camera, not the time, not the place.
 *
 * ## Synthesised, never copied
 *
 * The original EXIF block is discarded outright rather than edited down. Editing would mean walking
 * a TIFF structure full of offsets that point at each other, deleting entries, and repairing every
 * offset that moved — with maker notes, which are frequently offset-dependent and undocumented,
 * sitting in the middle of it. Building 36 known bytes from one integer has no such failure mode.
 */
internal object MinimalExif {

    /**
     * A complete `APP1` segment carrying only [orientation], or `null` when none is needed.
     *
     * Returns `null` for [NORMAL_ORIENTATION] because a tag saying "do not rotate" and no tag at all
     * mean exactly the same thing to every decoder — so the common case produces a file with no EXIF
     * whatsoever, which is both smaller and a stronger thing to be able to say.
     *
     * Out-of-range values are treated as normal rather than written through. The value comes from
     * whatever wrote the original file, and a nonsense orientation should not be faithfully
     * preserved into a file this tool is vouching for.
     */
    fun segmentFor(orientation: Int): ByteArray? {
        if (orientation !in VALID_ORIENTATIONS || orientation == NORMAL_ORIENTATION) return null

        val payload = byteArrayOf(
            // "Exif\0\0" — the APP1 identifier.
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00,

            // TIFF header: "MM" for big-endian, the 42 that marks it as TIFF, and the offset from
            // the start of this header to the first IFD — 8, so it follows immediately.
            0x4D, 0x4D,
            0x00, 0x2A,
            0x00, 0x00, 0x00, 0x08,

            // IFD0: exactly one entry.
            0x00, 0x01,

            // Tag 0x0112 (Orientation), type 3 (SHORT), count 1.
            0x01, 0x12,
            0x00, 0x03,
            0x00, 0x00, 0x00, 0x01,
            // A SHORT fits in the 4-byte value field, big-endian and left-aligned, so the two
            // trailing zeros are padding rather than part of the number.
            0x00, orientation.toByte(), 0x00, 0x00,

            // No next IFD. In particular no IFD1, which is where the embedded thumbnail lives — and
            // a thumbnail can differ from the image it belongs to, keeping the pre-crop version of a
            // photo somebody deliberately cropped.
            0x00, 0x00, 0x00, 0x00,
        )

        // The length field counts itself but not the two marker bytes.
        val declaredLength = payload.size + LENGTH_FIELD_BYTES

        return byteArrayOf(
            0xFF.toByte(),
            JpegMarkers.APP1.toByte(),
            (declaredLength shr 8).toByte(),
            (declaredLength and 0xFF).toByte(),
        ) + payload
    }

    /**
     * Whether `[start, endExclusive)` is byte-for-byte one of the blocks [segmentFor] produces.
     *
     * This is what makes the strip idempotent and, more importantly, what makes it *checkable*.
     * Verification works by re-reading the output and asking what a stripper would still want to
     * remove; without this, the one tag kept on purpose would be reported as a survivor and every
     * correctly cleaned photo would fail its own verification.
     *
     * Compared as exact bytes rather than "is this an Exif block containing an orientation". An
     * incoming file could carry an orientation tag alongside a GPS fix, and the difference between
     * the two has to be the whole content, not the presence of one tag.
     */
    fun matches(bytes: ByteArray, start: Int, endExclusive: Int): Boolean {
        val length = endExclusive - start
        for (orientation in VALID_ORIENTATIONS) {
            val candidate = segmentFor(orientation) ?: continue
            if (candidate.size != length) continue

            var same = true
            for (index in candidate.indices) {
                if (bytes[start + index] != candidate[index]) {
                    same = false
                    break
                }
            }
            if (same) return true
        }
        return false
    }

    const val NORMAL_ORIENTATION = 1

    private val VALID_ORIENTATIONS = 1..8
    private const val LENGTH_FIELD_BYTES = 2
}
