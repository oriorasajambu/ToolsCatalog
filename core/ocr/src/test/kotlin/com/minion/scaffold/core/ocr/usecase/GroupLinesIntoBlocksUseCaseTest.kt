package com.minion.scaffold.core.ocr.usecase

import com.minion.scaffold.core.ocr.model.BoundingBox
import com.minion.scaffold.core.ocr.model.RecognizedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupLinesIntoBlocksUseCaseTest {

    private val groupLines = GroupLinesIntoBlocksUseCase()

    @Test
    fun `empty input yields no blocks`() {
        assertTrue(groupLines(emptyList()).isEmpty())
    }

    @Test
    fun `a single line becomes a single block`() {
        val blocks = groupLines(listOf(line("Total", top = 0)))

        assertEquals(1, blocks.size)
        assertEquals("Total", blocks.single().text)
    }

    @Test
    fun `consecutive lines of one paragraph join`() {
        // 20px tall, 10px apart — inside one line height, so this is ordinary paragraph leading.
        val blocks = groupLines(
            listOf(
                line("The quick brown fox", top = 0),
                line("jumps over the lazy", top = 30),
                line("dog.", top = 60),
            ),
        )

        assertEquals(1, blocks.size)
        assertEquals("The quick brown fox\njumps over the lazy\ndog.", blocks.single().text)
    }

    @Test
    fun `a blank line splits two paragraphs`() {
        val blocks = groupLines(
            listOf(
                line("First paragraph", top = 0),
                line("still the first", top = 30),
                // A full blank line below: 60px gap against a 20px median height is well past
                // the threshold.
                line("Second paragraph", top = 120),
            ),
        )

        assertEquals(2, blocks.size)
        assertEquals("First paragraph\nstill the first", blocks[0].text)
        assertEquals("Second paragraph", blocks[1].text)
    }

    @Test
    fun `the block box contains every line`() {
        val blocks = groupLines(
            listOf(
                line("short", top = 0, left = 0, right = 100),
                line("much longer line", top = 30, left = 0, right = 400),
            ),
        )

        assertEquals(BoundingBox(left = 0, top = 0, right = 400, bottom = 50), blocks.single().box)
    }

    @Test
    fun `a receipt keeps every price with its own item`() {
        // Item names down the left, right-aligned prices down the right. Found on a real device:
        // merging first and ordering afterwards produced one tall item block that vertically
        // overlapped every price, collapsing them into a single row where their equal left edges
        // left the order arbitrary — the prices came out detached from their items and shuffled.
        val blocks = groupLines(
            listOf(
                line("Coffee", top = 0, left = 0, right = 200),
                line("2.50", top = 0, left = 400, right = 500),
                line("Sandwich", top = 30, left = 0, right = 200),
                line("6.00", top = 30, left = 400, right = 500),
                line("Cake", top = 60, left = 0, right = 200),
                line("4.25", top = 60, left = 400, right = 500),
            ),
        )

        // Interleaved line by line, which is how a receipt reads — never one column then the other.
        assertEquals(
            listOf("Coffee", "2.50", "Sandwich", "6.00", "Cake", "4.25"),
            blocks.map { it.text },
        )
    }

    @Test
    fun `columns do not merge into one another`() {
        val blocks = groupLines(
            listOf(
                line("left top", top = 0, left = 0, right = 200),
                line("right top", top = 0, left = 400, right = 600),
                line("left continues", top = 30, left = 0, right = 190),
            ),
        )

        // No horizontal overlap between the columns, so nothing merges across them.
        assertEquals(3, blocks.size)
        assertEquals(listOf("left top", "right top", "left continues"), blocks.map { it.text })
    }

    @Test
    fun `grouping is independent of input order`() {
        val lines = listOf(
            line("one", top = 0),
            line("two", top = 30),
            line("far below", top = 200),
        )

        assertEquals(groupLines(lines).map { it.text }, groupLines(lines.reversed()).map { it.text })
    }

    @Test
    fun `blocks come back in reading order`() {
        val blocks = groupLines(
            listOf(
                line("third", top = 400),
                line("first", top = 0),
                line("second", top = 200),
            ),
        )

        // Ordering happens here rather than in a later pass, so this is the contract.
        assertEquals(listOf("first", "second", "third"), blocks.map { it.text })
    }

    @Test
    fun `scale does not change the grouping`() {
        // The same page photographed from twice the distance. An absolute pixel threshold would
        // group these two differently; a median-relative one must not.
        val near = listOf(line("one", top = 0, height = 40), line("two", top = 60, height = 40))
        val far = listOf(line("one", top = 0, height = 20), line("two", top = 30, height = 20))

        assertEquals(groupLines(near).size, groupLines(far).size)
    }

    @Test
    fun `block confidence is the weakest line`() {
        val blocks = groupLines(
            listOf(
                line("clean", top = 0, confidence = 0.99f),
                line("smudged", top = 30, confidence = 0.42f),
            ),
        )

        assertEquals(0.42f, blocks.single().confidence)
    }

    @Test
    fun `confidence stays null when no line reported one`() {
        val blocks = groupLines(
            listOf(
                line("one", top = 0, confidence = null),
                line("two", top = 30, confidence = null),
            ),
        )

        // Null means "the model did not say", which must not become a number here.
        assertNull(blocks.single().confidence)
    }

    @Test
    fun `ids are unique across blocks`() {
        val blocks = groupLines(
            listOf(
                line("first", top = 0),
                line("second", top = 200),
                line("third", top = 400),
            ),
        )

        assertEquals(blocks.size, blocks.map { it.id }.toSet().size)
    }

    private fun line(
        text: String,
        top: Int,
        left: Int = 0,
        right: Int = 300,
        height: Int = 20,
        confidence: Float? = 0.9f,
    ) = RecognizedLine(
        text = text,
        box = BoundingBox(left = left, top = top, right = right, bottom = top + height),
        confidence = confidence,
    )
}
