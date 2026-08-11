package com.minion.scaffold.core.exif

import com.minion.scaffold.core.exif.model.MetadataKind
import com.minion.scaffold.core.exif.model.PlanResult
import com.minion.scaffold.core.exif.model.StripFailure
import com.minion.scaffold.core.exif.model.TrailingKind
import com.minion.scaffold.core.exif.usecase.ExecuteStripUseCase
import com.minion.scaffold.core.exif.usecase.PlanStripUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JpegStripTest {

    private val plan = PlanStripUseCase()
    private val execute = ExecuteStripUseCase()

    /**
     * **The test the whole approach exists for.**
     *
     * The compressed scan data comes out of the strip byte for byte identical, in one contiguous
     * run. If this holds, nothing was re-encoded — which is the difference between handing someone a
     * clean copy of their photo and handing them a worse photo.
     */
    @Test
    fun `the compressed scan survives byte for byte`() {
        val scan = ByteArray(4096) { (it * 31 and 0x7F).toByte() }
        val input = JpegBuilder()
            .soi()
            .segment(0xE1, JpegBuilder.exifPayload())
            .segment(0xFE, "a comment".toByteArray())
            .dqt()
            .sof0()
            .sos(scan)
            .eoi()
            .build()

        val output = strip(input)

        assertEquals(
            "the scan should appear exactly once, unbroken",
            1,
            output.countOccurrences(scan),
        )
    }

    /**
     * Scan data containing every sequence that looks like a marker and is not.
     *
     * `FF 00` is a stuffed byte, `FF D0`–`FF D7` are restart markers, `FF FF` is fill. Treat any of
     * them as the end of the scan and the image is truncated at that point — on a real photo, a grey
     * rectangle below the cut. A hand-built fixture containing none of these passes a walker that
     * gets all three wrong, which is exactly why they are here.
     */
    @Test
    fun `byte stuffing, restart markers and fill are part of the scan`() {
        val scan = byteArrayOf(
            0x11, 0x22,
            0xFF.toByte(), 0x00,
            0x33,
            0xFF.toByte(), 0xD0.toByte(),
            0x44,
            0xFF.toByte(), 0xD7.toByte(),
            0x55,
            0xFF.toByte(), 0xFF.toByte(), 0x00,
            0x66,
        )
        val input = JpegBuilder().soi().sof0().sos(scan).eoi().build()

        val output = strip(input)

        assertEquals(1, output.countOccurrences(scan))
        assertTrue("output should end with EOI", output.endsWithEoi())
    }

    /**
     * The allowlist fails closed.
     *
     * `APP7` has no defined meaning, so nothing in the planner names it. It is dropped because it was
     * not on the list of things to keep, which is the entire point: a stripper whose coverage depends
     * on someone having heard of a vendor's format is offering a best effort, not a guarantee.
     */
    @Test
    fun `an undefined APP segment is dropped without being named anywhere`() {
        val vendorBlob = "SERIALNO=1234567890".toByteArray()
        val input = JpegBuilder()
            .soi()
            .segment(0xE7, vendorBlob)
            .sof0()
            .sos(byteArrayOf(1, 2, 3))
            .eoi()
            .build()

        val output = strip(input)

        assertEquals(-1, output.indexOfBytes(vendorBlob))
        assertTrue(removedKinds(input).contains(MetadataKind.Unknown))
    }

    @Test
    fun `exif, xmp, iptc and comments are all removed`() {
        val input = JpegBuilder()
            .soi()
            .segment(0xE1, JpegBuilder.exifPayload())
            .segment(0xE1, JpegBuilder.xmpPayload())
            .segment(0xED, JpegBuilder.iptcPayload())
            .segment(0xFE, "shot on a phone".toByteArray())
            .sof0()
            .sos(byteArrayOf(9, 9, 9))
            .eoi()
            .build()

        val removed = removedKinds(input)

        assertTrue(removed.containsAll(setOf(
            MetadataKind.Exif,
            MetadataKind.Xmp,
            MetadataKind.Iptc,
            MetadataKind.Comment,
        )))
        assertEquals(-1, strip(input).indexOfBytes("gps here too".toByteArray()))
    }

    /**
     * All eight orientations survive, and orientation 1 leaves no EXIF at all.
     *
     * The second half matters more than it looks: "do not rotate" and "no tag" mean the same thing to
     * every decoder, so the common case produces a file with no EXIF block whatsoever — a stronger
     * thing to be able to say than "one tag remains".
     */
    @Test
    fun `orientation round trips, and normal orientation writes nothing`() {
        for (orientation in 1..8) {
            val input = JpegBuilder()
                .soi()
                .segment(0xE1, JpegBuilder.exifPayload(orientation))
                .sof0()
                .sos(byteArrayOf(7))
                .eoi()
                .build()

            val output = strip(input, orientation = orientation)

            if (orientation == 1) {
                assertEquals(
                    "orientation 1 should leave no Exif block",
                    -1,
                    output.indexOfBytes("Exif".toByteArray(Charsets.US_ASCII)),
                )
            } else {
                assertEquals(
                    "orientation $orientation should round trip",
                    orientation,
                    output.readOrientation(),
                )
            }
        }
    }

    /** A nonsense orientation is not faithfully preserved into a file this tool is vouching for. */
    @Test
    fun `an out of range orientation writes nothing`() {
        val input = JpegBuilder().soi().sof0().sos(byteArrayOf(7)).eoi().build()

        val output = strip(input, orientation = 99)

        assertEquals(-1, output.indexOfBytes("Exif".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `the colour profile is kept when asked and dropped when not`() {
        val icc = JpegBuilder.iccPayload()
        val input = JpegBuilder()
            .soi()
            .segment(0xE2, icc)
            .sof0()
            .sos(byteArrayOf(3))
            .eoi()
            .build()

        assertTrue(strip(input, keepIcc = true).indexOfBytes(icc) >= 0)
        assertEquals(-1, strip(input, keepIcc = false).indexOfBytes(icc))
    }

    /**
     * `MPF` indexes additional whole images inside the same file, so it goes whatever the ICC
     * preference says. It shares a marker with the colour profile, which is the only reason this
     * needs stating.
     */
    @Test
    fun `a multi-picture block is dropped even with the profile retained`() {
        val mpf = "MPF ".toByteArray(Charsets.US_ASCII) + ByteArray(64) { 0x5A }
        val input = JpegBuilder()
            .soi()
            .segment(0xE2, mpf)
            .sof0()
            .sos(byteArrayOf(3))
            .eoi()
            .build()

        assertEquals(-1, strip(input, keepIcc = true).indexOfBytes(mpf))
    }

    @Test
    fun `a plain JFIF header is kept but one carrying a thumbnail is not`() {
        val plain = JpegBuilder()
            .soi().segment(0xE0, JpegBuilder.jfifPayload()).sof0().sos(byteArrayOf(1)).eoi().build()
        val withThumbnail = JpegBuilder()
            .soi().segment(0xE0, JpegBuilder.jfifWithThumbnailPayload()).sof0()
            .sos(byteArrayOf(1)).eoi().build()

        assertTrue(strip(plain).indexOfBytes("JFIF".toByteArray()) >= 0)
        assertEquals(-1, strip(withThumbnail).indexOfBytes("JFIF".toByteArray()))
    }

    /**
     * A motion photo's appended video is removed, and reported as a video rather than as a number.
     *
     * Stripping the GPS tag while shipping several seconds of video and audio from around the shot
     * would be the largest hole this tool could leave, inside a file it had just declared clean.
     */
    @Test
    fun `an appended video is truncated and named`() {
        val trailer = JpegBuilder.mp4Trailer()
        val input = JpegBuilder()
            .soi().sof0().sos(byteArrayOf(1, 2)).eoi().raw(trailer).build()

        val plan = requirePlan(input)

        assertEquals(TrailingKind.EmbeddedVideo, plan.trailing?.kind)
        assertEquals(trailer.size, plan.trailing?.byteCount)

        val output = execute.toByteArray(input, plan)
        assertTrue(output.endsWithEoi())
        assertEquals(-1, output.indexOfBytes("ftyp".toByteArray()))
    }

    @Test
    fun `unidentifiable trailing data is still removed and counted`() {
        val junk = ByteArray(300) { 0x7E }
        val input = JpegBuilder().soi().sof0().sos(byteArrayOf(1)).eoi().raw(junk).build()

        val plan = requirePlan(input)

        assertEquals(TrailingKind.Unknown, plan.trailing?.kind)
        assertEquals(junk.size, plan.trailing?.byteCount)
    }

    @Test
    fun `a file with nothing to remove reports as much`() {
        val input = JpegBuilder().soi().sof0().dqt().sos(byteArrayOf(1, 2, 3)).eoi().build()

        val plan = requirePlan(input)

        assertTrue(plan.removed.isEmpty())
        assertNull(plan.trailing)
        assertTrue(!plan.hasAnythingToRemove)
    }

    // region Hostile and malformed input

    /**
     * Files arrive from a picker and may be truncated, partially downloaded or built to be hostile.
     * Every one of these must stop with an offset rather than loop, read out of bounds, or succeed.
     */
    @Test
    fun `a segment length below two is rejected rather than looping`() {
        val input = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE1.toByte(), 0x00, 0x01,
            0xFF.toByte(), 0xD9.toByte(),
        )

        assertEquals(StripFailure.Defect.BadLength, defectOf(input))
    }

    @Test
    fun `a segment length running past the end is rejected`() {
        val input = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE1.toByte(), 0x7F, 0xFF.toByte(),
            0xFF.toByte(), 0xD9.toByte(),
        )

        assertEquals(StripFailure.Defect.BadLength, defectOf(input))
    }

    @Test
    fun `a file with no end marker is rejected`() {
        val input = JpegBuilder().soi().sof0().sos(byteArrayOf(1, 2, 3)).build()

        assertEquals(StripFailure.Defect.MissingEndMarker, defectOf(input))
    }

    /** Enough of a JPEG to be recognised as one, and then it simply stops. */
    @Test
    fun `a file that ends mid-header is rejected`() {
        val input = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte())

        assertEquals(StripFailure.Defect.Truncated, defectOf(input))
    }

    /**
     * Two bytes is not a truncated JPEG, it is not a JPEG.
     *
     * The distinction is the difference between "this file is damaged" and "this is not a picture",
     * and the detection happens on content before any parser is handed the bytes.
     */
    @Test
    fun `a file too short to identify is not an image`() {
        val result = plan(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), 1, keepIcc = true)

        assertTrue((result as PlanResult.Failure).failure is StripFailure.NotAnImage)
    }

    @Test
    fun `a stuffed byte outside the scan is rejected`() {
        val input = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0x00,
            0xFF.toByte(), 0xD9.toByte(),
        )

        assertEquals(StripFailure.Defect.UnexpectedStructure, defectOf(input))
    }

    @Test
    fun `something that is not an image at all is named as such`() {
        val result = plan("this is a text file".toByteArray(), 1, keepIcc = true)

        assertTrue((result as PlanResult.Failure).failure is StripFailure.NotAnImage)
    }

    /** HEIC is a real image in a container this module cannot strip, which is a different answer. */
    @Test
    fun `a HEIC is reported as convertible rather than as not an image`() {
        val heic = byteArrayOf(0, 0, 0, 0x18) +
            "ftypheic".toByteArray(Charsets.US_ASCII) +
            ByteArray(64)

        val failure = (plan(heic, 1, keepIcc = true) as PlanResult.Failure).failure

        assertTrue(failure is StripFailure.UnsupportedContainer)
        assertEquals("heic", (failure as StripFailure.UnsupportedContainer).describedAs)
    }

    // endregion

    // region Helpers

    private fun strip(input: ByteArray, orientation: Int = 1, keepIcc: Boolean = true): ByteArray =
        execute.toByteArray(input, requirePlan(input, orientation, keepIcc))

    private fun requirePlan(
        input: ByteArray,
        orientation: Int = 1,
        keepIcc: Boolean = true,
    ) = (plan(input, orientation, keepIcc) as PlanResult.Success).plan

    private fun removedKinds(input: ByteArray) = requirePlan(input).removed.map { it.kind }.toSet()

    private fun defectOf(input: ByteArray): StripFailure.Defect {
        val failure = (plan(input, 1, keepIcc = true) as PlanResult.Failure).failure
        return (failure as StripFailure.Malformed).defect
    }

    private fun ByteArray.endsWithEoi(): Boolean =
        size >= 2 && this[size - 2] == 0xFF.toByte() && this[size - 1] == 0xD9.toByte()

    /**
     * Reads the orientation back out of a synthesised block, without using the module's own parser.
     *
     * Written independently on purpose: verifying the writer with the reader that was built
     * alongside it would pass just as happily if both agreed on the wrong layout.
     */
    private fun ByteArray.readOrientation(): Int? {
        val exifAt = indexOfBytes("Exif".toByteArray(Charsets.US_ASCII))
        if (exifAt < 0) return null

        // Identifier is 6 bytes, then the TIFF header is 8, then a 2-byte entry count, then the
        // entry: 2 tag + 2 type + 4 count, and the value sits in the next two bytes.
        val valueAt = exifAt + 6 + 8 + 2 + 8
        assertNotNull(this)
        return ((this[valueAt].toInt() and 0xFF) shl 8) or (this[valueAt + 1].toInt() and 0xFF)
    }

    // endregion
}
