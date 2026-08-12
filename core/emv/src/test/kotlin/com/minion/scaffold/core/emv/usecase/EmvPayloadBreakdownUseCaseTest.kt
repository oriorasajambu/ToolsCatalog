package com.minion.scaffold.core.emv.usecase

import com.minion.scaffold.core.emv.EmvSamples
import com.minion.scaffold.core.emv.model.TagInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class EmvPayloadBreakdownUseCaseTest {

    private val breakdown = EmvPayloadBreakdownUseCase()

    @Test
    fun `flattens every top-level tag in payload order`() {
        val tags = breakdown(EmvSamples.QRIS_DYNAMIC)
        val topLevel = tags.filter { it.depth == 0 }

        // The same thirteen segments ParseEmvPayloadUseCase reports, in order, starting at 00.
        assertEquals(13, topLevel.size)
        assertEquals("00", topLevel.first().path)
        assertEquals(0, topLevel.first().span.start)
    }

    @Test
    fun `top-level spans are contiguous and cover the whole payload`() {
        val payload = EmvSamples.QRIS_DYNAMIC
        val topLevel = breakdown(payload).filter { it.depth == 0 }

        topLevel.zipWithNext { current, next ->
            assertEquals(current.span.endExclusive, next.span.start)
        }
        assertEquals(0, topLevel.first().span.start)
        assertEquals(payload.length, topLevel.last().span.endExclusive)
    }

    @Test
    fun `breaks a template out into its sub-tags, nested inside the template's span`() {
        val tags = breakdown(EmvSamples.QRIS_DYNAMIC)

        val template = tags.single { it.path == "26" }
        val subtags = tags.filter { it.path.startsWith("26.") }

        assertTrue(template.isTemplate)
        assertTrue(subtags.isNotEmpty())
        assertTrue(subtags.all { it.depth == 1 })
        // Each sub-tag lives strictly within the template's own span.
        assertTrue(
            subtags.all {
                it.span.start >= template.span.start && it.span.endExclusive <= template.span.endExclusive
            },
        )
    }

    /** A sub-tag `00` is a globally-unique identifier, so it must not decode as a payload version. */
    @Test
    fun `does not interpret a template's sub-tags as top-level tags`() {
        val subtag = breakdown(EmvSamples.QRIS_DYNAMIC).single { it.path == "26.00" }

        assertEquals("00", subtag.tag)
        assertEquals(1, subtag.depth)
        assertEquals(TagInterpretation.None, subtag.interpretation)
    }

    @Test
    fun `decodes top-level coded tags`() {
        val tags = breakdown(EmvSamples.QRIS_DYNAMIC)

        assertEquals(
            TagInterpretation.PayloadVersion("1"),
            tags.single { it.path == "00" }.interpretation,
        )
        assertEquals(
            TagInterpretation.Currency(numericCode = "360", alphaCode = "IDR", name = "Indonesian Rupiah"),
            tags.single { it.path == "53" }.interpretation,
        )
    }

    @Test
    fun `verifies the checksum on tag 63`() {
        val crc = breakdown(EmvSamples.QRIS_DYNAMIC).single { it.path == "63" }.interpretation

        assertTrue(crc is TagInterpretation.Checksum && crc.verification.passed)
    }

    @Test
    fun `returns nothing for a payload that does not frame`() {
        assertTrue(breakdown("this is not an emv payload").isEmpty())
        assertTrue(breakdown("   ").isEmpty())
    }
}
