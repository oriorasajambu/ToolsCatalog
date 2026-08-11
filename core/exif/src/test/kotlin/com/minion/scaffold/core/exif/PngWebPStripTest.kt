package com.minion.scaffold.core.exif

import com.minion.scaffold.core.exif.model.ImageContainer
import com.minion.scaffold.core.exif.model.MetadataKind
import com.minion.scaffold.core.exif.model.PlanResult
import com.minion.scaffold.core.exif.model.StripFailure
import com.minion.scaffold.core.exif.usecase.ExecuteStripUseCase
import com.minion.scaffold.core.exif.usecase.PlanStripUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

class PngWebPStripTest {

    private val plan = PlanStripUseCase()
    private val execute = ExecuteStripUseCase()

    // region PNG

    @Test
    fun `png text and exif chunks are dropped and the image chunks survive`() {
        val pixels = ByteArray(256) { it.toByte() }
        val input = PngBuilder()
            .chunk("IHDR", ByteArray(13))
            .chunk("tEXt", "Software ".toByteArray() + "SomeEditor 4.2".toByteArray())
            .chunk("eXIf", JpegBuilder.exifPayload())
            .chunk("tIME", ByteArray(7))
            .chunk("IDAT", pixels)
            .chunk("IEND", ByteArray(0))
            .build()

        val output = strip(input)

        assertEquals(1, output.countOccurrences(pixels))
        assertEquals(-1, output.indexOfBytes("SomeEditor".toByteArray()))
        assertEquals(-1, output.indexOfBytes("tEXt".toByteArray()))
        assertEquals(-1, output.indexOfBytes("eXIf".toByteArray()))
        assertTrue(output.indexOfBytes("IHDR".toByteArray()) >= 0)
        assertTrue(output.indexOfBytes("IEND".toByteArray()) >= 0)
    }

    /**
     * Every chunk that survives still carries a CRC matching its contents.
     *
     * Copying chunks whole makes this true by construction, which is the point — the test exists so
     * that a future "optimisation" that rewrites a chunk in place has to notice the checksum.
     */
    @Test
    fun `every retained png chunk keeps a valid CRC`() {
        val input = PngBuilder()
            .chunk("IHDR", ByteArray(13) { 7 })
            .chunk("iTXt", "XML:com.adobe.xmp ".toByteArray() + "<xmp/>".toByteArray())
            .chunk("IDAT", ByteArray(64) { it.toByte() })
            .chunk("IEND", ByteArray(0))
            .build()

        val output = strip(input)

        var cursor = PNG_SIGNATURE_LENGTH
        var checked = 0
        while (cursor < output.size) {
            val length = readInt(output, cursor)
            val nameAt = cursor + 4
            val payloadAt = nameAt + 4
            val crcAt = payloadAt + length

            val expected = CRC32().apply {
                update(output, nameAt, 4)
                update(output, payloadAt, length)
            }.value.toInt()

            assertEquals(
                "CRC mismatch on chunk ${String(output, nameAt, 4, Charsets.US_ASCII)}",
                expected,
                readInt(output, crcAt),
            )

            checked++
            cursor = crcAt + 4
        }

        assertEquals("expected IHDR, IDAT and IEND to survive", 3, checked)
    }

    /** An unknown chunk is dropped for the same reason an undefined JPEG marker is. */
    @Test
    fun `an unknown png chunk is dropped`() {
        val input = PngBuilder()
            .chunk("IHDR", ByteArray(13))
            .chunk("prVt", "device serial 998877".toByteArray())
            .chunk("IDAT", ByteArray(8))
            .chunk("IEND", ByteArray(0))
            .build()

        val output = strip(input)

        assertEquals(-1, output.indexOfBytes("998877".toByteArray()))
        assertTrue(removedKinds(input).contains(MetadataKind.Unknown))
    }

    /** Animation chunks render the image, so dropping them would silently produce a still. */
    @Test
    fun `apng control chunks survive`() {
        val input = PngBuilder()
            .chunk("IHDR", ByteArray(13))
            .chunk("acTL", ByteArray(8))
            .chunk("fcTL", ByteArray(26))
            .chunk("IDAT", ByteArray(8))
            .chunk("fdAT", ByteArray(12))
            .chunk("IEND", ByteArray(0))
            .build()

        val output = strip(input)

        assertTrue(output.indexOfBytes("acTL".toByteArray()) >= 0)
        assertTrue(output.indexOfBytes("fdAT".toByteArray()) >= 0)
    }

    @Test
    fun `a png with no end chunk is rejected`() {
        val input = PngBuilder().chunk("IHDR", ByteArray(13)).chunk("IDAT", ByteArray(4)).build()

        assertEquals(StripFailure.Defect.MissingEndMarker, defectOf(input))
    }

    /**
     * A complete chunk header declaring more payload than the file contains.
     *
     * Distinct from a truncated file: the header is entirely present and internally plausible, and
     * only the arithmetic gives it away. A parser that trusted the declared length would read a
     * kilobyte past the end of the array.
     */
    @Test
    fun `a png chunk length running past the end is rejected`() {
        val input = PngBuilder().chunk("IHDR", ByteArray(13)).build() +
            byteArrayOf(0x00, 0x00, 0x03, 0xE8.toByte()) +
            "IDAT".toByteArray() +
            ByteArray(4)

        assertEquals(StripFailure.Defect.BadLength, defectOf(input))
    }

    /** The pathological case: a length so large that adding it to the cursor overflows. */
    @Test
    fun `a png chunk length that would overflow is rejected`() {
        val input = PngBuilder().chunk("IHDR", ByteArray(13)).build() +
            byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()) +
            "IDAT".toByteArray() +
            ByteArray(4)

        assertEquals(StripFailure.Defect.BadLength, defectOf(input))
    }

    // endregion

    // region WebP

    @Test
    fun `webp exif and xmp chunks are dropped and the image chunk survives`() {
        val pixels = ByteArray(128) { (it * 3).toByte() }
        val input = WebPBuilder()
            .vp8x()
            .chunk("EXIF", JpegBuilder.exifPayload())
            .chunk("XMP ", "<x:xmpmeta>secret</x:xmpmeta>".toByteArray())
            .chunk("VP8 ", pixels)
            .build()

        val output = strip(input)

        assertEquals(1, output.countOccurrences(pixels))
        assertEquals(-1, output.indexOfBytes("secret".toByteArray()))
        assertEquals(-1, output.indexOfBytes("EXIF".toByteArray()))
    }

    /**
     * **The RIFF size field matches what actually follows it.**
     *
     * Android's decoder ignores this field and reads to the end of the file, so a wrong value
     * produces something that opens perfectly on the device that made it and fails elsewhere — a
     * defect that on-device testing would never surface.
     */
    @Test
    fun `the riff size field is rewritten to match the remaining bytes`() {
        val input = WebPBuilder()
            .vp8x()
            .chunk("EXIF", ByteArray(200) { 0x41 })
            .chunk("VP8 ", ByteArray(64))
            .build()

        val output = strip(input)

        val declared = readLittleEndianInt(output, RIFF_SIZE_OFFSET)
        assertEquals(
            "declared size should cover everything after the size field",
            output.size - RIFF_PREFIX_LENGTH,
            declared,
        )
    }

    /**
     * The extended-format flags stop claiming metadata that has been removed.
     *
     * Legal enough that most decoders shrug, and untidy in a way a tool making this particular
     * promise should not be.
     */
    @Test
    fun `the vp8x metadata flags are cleared`() {
        val input = WebPBuilder()
            .vp8x()
            .chunk("EXIF", ByteArray(16))
            .chunk("VP8 ", ByteArray(16))
            .build()

        val output = strip(input)

        val vp8xAt = output.indexOfBytes("VP8X".toByteArray())
        val flags = output[vp8xAt + VP8X_HEADER_LENGTH].toInt() and 0xFF

        assertEquals("EXIF flag should be clear", 0, flags and EXIF_FLAG)
        assertEquals("XMP flag should be clear", 0, flags and XMP_FLAG)
        assertEquals("other flags should be untouched", ALPHA_FLAG, flags and ALPHA_FLAG)
    }

    /** RIFF pads odd payloads to an even boundary, and missing that shifts every later chunk. */
    @Test
    fun `an odd length chunk is walked correctly`() {
        val pixels = ByteArray(33) { 0x5B }
        val input = WebPBuilder()
            .chunk("EXIF", ByteArray(7) { 0x22 })
            .chunk("VP8 ", pixels)
            .build()

        val output = strip(input)

        assertEquals(1, output.countOccurrences(pixels))
        assertEquals(ImageContainer.WebP, requirePlan(input).container)
    }

    // endregion

    // region Helpers

    private fun strip(input: ByteArray): ByteArray = execute.toByteArray(input, requirePlan(input))

    private fun requirePlan(input: ByteArray) =
        (plan(input, orientation = 1, keepIcc = true) as PlanResult.Success).plan

    private fun removedKinds(input: ByteArray) = requirePlan(input).removed.map { it.kind }.toSet()

    private fun defectOf(input: ByteArray): StripFailure.Defect {
        val failure = (plan(input, 1, keepIcc = true) as PlanResult.Failure).failure
        return (failure as StripFailure.Malformed).defect
    }

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)

    private fun readLittleEndianInt(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)

    private companion object {
        const val PNG_SIGNATURE_LENGTH = 8
        const val RIFF_SIZE_OFFSET = 4
        const val RIFF_PREFIX_LENGTH = 8
        const val VP8X_HEADER_LENGTH = 8
        const val EXIF_FLAG = 0x08
        const val XMP_FLAG = 0x04
        const val ALPHA_FLAG = 0x10
    }

    // endregion
}
