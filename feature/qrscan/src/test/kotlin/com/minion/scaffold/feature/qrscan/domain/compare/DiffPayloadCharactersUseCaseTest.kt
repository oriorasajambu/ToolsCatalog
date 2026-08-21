package com.minion.scaffold.feature.qrscan.domain.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DiffPayloadCharactersUseCaseTest {

    private val diff = DiffPayloadCharactersUseCase()

    @Test
    fun `identical payloads have nothing to report`() {
        val result = diff("000201010212", "000201010212")

        assertTrue(result.identical)
        assertFalse(result.truncated)
    }

    @Test
    fun `a substituted character is one span on each side`() {
        // The payloads agree for nine characters, then "2" becomes "3", then agree again.
        val result = diff("000201010212", "000201010312")

        assertEquals(listOf(DiffSpan(9, 10)), result.baselineSpans)
        assertEquals(listOf(DiffSpan(9, 10)), result.candidateSpans)
    }

    @Test
    fun `an inserted character does not disturb the tail`() {
        // A "9" inserted at offset 5. Everything after it shifts by one and still matches, so the
        // first payload has nothing missing at all — the case a position-wise comparison gets
        // wrong, where index against index would light the whole remainder up.
        val result = diff("00020101", "000209101")

        assertTrue(result.baselineSpans.isEmpty())
        assertEquals(listOf(DiffSpan(5, 6)), result.candidateSpans)
    }

    @Test
    fun `a deleted run is one span, not one per character`() {
        val result = diff("0002ABCD0101", "00020101")

        assertEquals(listOf(DiffSpan(4, 8)), result.baselineSpans)
        assertTrue(result.candidateSpans.isEmpty())
    }

    @Test
    fun `a shared prefix and suffix are never reported`() {
        val prefix = "0".repeat(300)
        val suffix = "9".repeat(300)

        val result = diff(prefix + "AAA" + suffix, prefix + "BBB" + suffix)

        assertEquals(listOf(DiffSpan(300, 303)), result.baselineSpans)
        assertEquals(listOf(DiffSpan(300, 303)), result.candidateSpans)
    }

    @Test
    fun `an empty side makes the whole of the other an addition`() {
        val result = diff("", "0002")

        assertTrue(result.baselineSpans.isEmpty())
        assertEquals(listOf(DiffSpan(0, 4)), result.candidateSpans)
        assertFalse(result.truncated)
    }

    @Test
    fun `two payloads with nothing in common report one replaced run each`() {
        // Past the cell bound the alignment is abandoned rather than allocating a table of
        // millions of cells. Distinct alphabets so there is no common subsequence to trim.
        val baseline = "a".repeat(2_000)
        val candidate = "b".repeat(2_000)

        val result = diff(baseline, candidate)

        assertTrue(result.truncated)
        assertEquals(listOf(DiffSpan(0, 2_000)), result.baselineSpans)
        assertEquals(listOf(DiffSpan(0, 2_000)), result.candidateSpans)
    }

    @Test
    fun `a long pair sharing everything but one field still aligns`() {
        // The real shape: two QRIS codes of a few hundred characters differing in an amount. The
        // trim collapses this to a handful of cells, so the bound is never approached.
        val prefix = "0".repeat(1_500)
        val suffix = "9".repeat(1_500)

        val result = diff(prefix + "15000000.00" + suffix, prefix + "25000000.00" + suffix)

        assertFalse(result.truncated)
        assertEquals(listOf(DiffSpan(1_500, 1_501)), result.baselineSpans)
        assertEquals(listOf(DiffSpan(1_500, 1_501)), result.candidateSpans)
    }

    @Test
    fun `spans never overlap between the trimmed prefix and suffix`() {
        // "aa" against "aaaa": the prefix and suffix would both claim the same characters if the
        // suffix scan were not stopped at what the prefix already took.
        val result = diff("aa", "aaaa")

        assertTrue(result.baselineSpans.isEmpty())
        assertEquals(1, result.candidateSpans.size)
        assertEquals(2, result.candidateSpans.single().let { it.endExclusive - it.start })
    }
}
