package com.minion.scaffold.core.exif

import com.minion.scaffold.core.exif.model.MetadataKind
import com.minion.scaffold.core.exif.model.PlanResult
import com.minion.scaffold.core.exif.usecase.ExecuteStripUseCase
import com.minion.scaffold.core.exif.usecase.PlanStripUseCase
import com.minion.scaffold.core.exif.usecase.VerificationResult
import com.minion.scaffold.core.exif.usecase.VerifyStripUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The round trip: strip a file, then read the result back and ask what is left.
 *
 * This is what turns the feature's claim into evidence. It also happens to be the strongest test in
 * the module, because it exercises the walkers twice over — once to plan, once to inspect — and any
 * disagreement between what the planner removed and what a fresh read of the output finds shows up
 * immediately.
 */
class VerifyStripTest {

    private val plan = PlanStripUseCase()
    private val execute = ExecuteStripUseCase()
    private val verify = VerifyStripUseCase(plan)

    @Test
    fun `a stripped jpeg verifies clean`() {
        val input = JpegBuilder()
            .soi()
            .segment(0xE1, JpegBuilder.exifPayload(orientation = 6))
            .segment(0xE1, JpegBuilder.xmpPayload())
            .segment(0xED, JpegBuilder.iptcPayload())
            .segment(0xFE, "comment".toByteArray())
            .segment(0xE7, "vendor".toByteArray())
            .sof0()
            .sos(ByteArray(512) { it.toByte() })
            .eoi()
            .raw(JpegBuilder.mp4Trailer())
            .build()

        val result = verify(strip(input, orientation = 6), keepIcc = false)

        assertTrue("expected clean, was $result", result is VerificationResult.Clean)
    }

    /**
     * The original verifies dirty, and names what it found.
     *
     * The counterpart that stops the check being vacuous: a verifier that returned Clean for
     * everything would pass the test above.
     */
    @Test
    fun `the original verifies dirty and names what is in it`() {
        val input = JpegBuilder()
            .soi()
            .segment(0xE1, JpegBuilder.exifPayload())
            .segment(0xE1, JpegBuilder.xmpPayload())
            .sof0()
            .sos(byteArrayOf(1, 2, 3))
            .eoi()
            .build()

        val result = verify(input, keepIcc = false)

        assertTrue(result is VerificationResult.Dirty)
        val kinds = (result as VerificationResult.Dirty).remaining.map { it.kind }
        assertTrue(kinds.contains(MetadataKind.Exif))
        assertTrue(kinds.contains(MetadataKind.Xmp))
    }

    /** Trailing data counts as dirty even when every segment is clean. */
    @Test
    fun `an appended video alone makes a file dirty`() {
        val trailer = JpegBuilder.mp4Trailer()
        val input = JpegBuilder().soi().sof0().sos(byteArrayOf(1)).eoi().raw(trailer).build()

        val result = verify(input, keepIcc = false)

        assertTrue(result is VerificationResult.Dirty)
        assertEquals(trailer.size, (result as VerificationResult.Dirty).trailingBytes)
    }

    /**
     * A deliberately retained colour profile is not reported as a survivor.
     *
     * Verification has to be told the same preference the export used, or the one thing kept on
     * purpose would be flagged as a failure — and the user would be shown an alarming warning about
     * a decision they made themselves.
     */
    @Test
    fun `a retained colour profile verifies clean and is listed as kept`() {
        val input = JpegBuilder()
            .soi()
            .segment(0xE2, JpegBuilder.iccPayload())
            .segment(0xE1, JpegBuilder.exifPayload())
            .sof0()
            .sos(byteArrayOf(4, 5))
            .eoi()
            .build()

        val output = strip(input, keepIcc = true)

        val kept = verify(output, keepIcc = true)
        assertTrue("expected clean, was $kept", kept is VerificationResult.Clean)
        assertTrue(
            (kept as VerificationResult.Clean).retained.any { it.kind == MetadataKind.IccProfile },
        )

        // And the same file checked against the opposite preference correctly reports it.
        assertTrue(verify(output, keepIcc = false) is VerificationResult.Dirty)
    }

    @Test
    fun `a stripped png verifies clean`() {
        val input = PngBuilder()
            .chunk("IHDR", ByteArray(13))
            .chunk("tEXt", "Software ".toByteArray() + "Editor".toByteArray())
            .chunk("eXIf", JpegBuilder.exifPayload())
            .chunk("IDAT", ByteArray(32))
            .chunk("IEND", ByteArray(0))
            .build()

        val result = verify(strip(input), keepIcc = false)

        assertTrue("expected clean, was $result", result is VerificationResult.Clean)
    }

    @Test
    fun `a stripped webp verifies clean`() {
        val input = WebPBuilder()
            .vp8x()
            .chunk("EXIF", JpegBuilder.exifPayload())
            .chunk("XMP ", "<xmp/>".toByteArray())
            .chunk("VP8 ", ByteArray(48))
            .build()

        val result = verify(strip(input), keepIcc = false)

        assertTrue("expected clean, was $result", result is VerificationResult.Clean)
    }

    /**
     * The output of a strip is still a file the walkers can read.
     *
     * Worth asserting separately: an export that produced structurally broken bytes would be caught
     * here as Unreadable rather than being handed to the user as a clean photo.
     */
    @Test
    fun `stripping twice is a no-op`() {
        val input = JpegBuilder()
            .soi()
            .segment(0xE1, JpegBuilder.exifPayload(orientation = 3))
            .sof0()
            .sos(ByteArray(64) { it.toByte() })
            .eoi()
            .build()

        val once = strip(input, orientation = 3)
        val twice = execute.toByteArray(
            once,
            (plan(once, orientation = 3, keepIcc = false) as PlanResult.Success).plan,
        )

        assertTrue(once.contentEquals(twice))
    }

    private fun strip(input: ByteArray, orientation: Int = 1, keepIcc: Boolean = false): ByteArray =
        execute.toByteArray(
            input,
            (plan(input, orientation, keepIcc) as PlanResult.Success).plan,
        )
}
