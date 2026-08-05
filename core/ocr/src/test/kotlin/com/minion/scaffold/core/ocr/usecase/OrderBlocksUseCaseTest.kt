package com.minion.scaffold.core.ocr.usecase

import com.minion.scaffold.core.ocr.model.BoundingBox
import com.minion.scaffold.core.ocr.model.RecognizedBlock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fixtures are shuffled relative to their reading order on purpose — ML Kit's detection order is
 * arbitrary, so a test that fed blocks in already-correct order would pass against an
 * implementation that did nothing.
 */
class OrderBlocksUseCaseTest {

    private val order = OrderBlocksUseCase()

    private fun block(id: String, left: Int, top: Int, right: Int, bottom: Int) = RecognizedBlock(
        id = id,
        text = id,
        box = BoundingBox(left = left, top = top, right = right, bottom = bottom),
        confidence = null,
    )

    private fun idsOf(blocks: List<RecognizedBlock>) = blocks.map { it.id }

    @Test
    fun `an empty list is returned untouched`() {
        assertEquals(emptyList<RecognizedBlock>(), order(emptyList()))
    }

    @Test
    fun `a single block is returned untouched`() {
        val only = listOf(block("a", 0, 0, 100, 20))

        assertEquals(only, order(only))
    }

    @Test
    fun `a single column is ordered top to bottom`() {
        val blocks = listOf(
            block("third", 0, 200, 100, 220),
            block("first", 0, 0, 100, 20),
            block("second", 0, 100, 100, 120),
        )

        assertEquals(listOf("first", "second", "third"), idsOf(order(blocks)))
    }

    @Test
    fun `blocks on the same line are ordered left to right`() {
        val blocks = listOf(
            block("right", 300, 0, 400, 20),
            block("left", 0, 0, 100, 20),
            block("middle", 150, 0, 250, 20),
        )

        assertEquals(listOf("left", "middle", "right"), idsOf(order(blocks)))
    }

    /**
     * The case that motivates the whole algorithm: an item name on the left and its price on the
     * right must stay on one line, and the next item must follow — not all names then all prices.
     */
    @Test
    fun `a receipt keeps each item beside its own price`() {
        val blocks = listOf(
            block("price-2", 300, 100, 380, 120),
            block("item-1", 0, 0, 200, 20),
            block("price-1", 300, 0, 380, 20),
            block("item-2", 0, 100, 200, 120),
        )

        assertEquals(listOf("item-1", "price-1", "item-2", "price-2"), idsOf(order(blocks)))
    }

    /**
     * A tall heading beside a short page number: measuring overlap against the shorter box is what
     * makes these group. Measured against the tall one they would not reach the threshold.
     */
    @Test
    fun `a tall block and a short block on the same line group together`() {
        val blocks = listOf(
            block("page-number", 300, 10, 340, 26),
            block("heading", 0, 0, 200, 60),
            block("body", 0, 200, 200, 220),
        )

        assertEquals(listOf("heading", "page-number", "body"), idsOf(order(blocks)))
    }

    @Test
    fun `vertically separated blocks do not group even when horizontally adjacent`() {
        val blocks = listOf(
            block("lower", 210, 100, 400, 120),
            block("upper", 0, 0, 200, 20),
        )

        assertEquals(listOf("upper", "lower"), idsOf(order(blocks)))
    }

    /**
     * Transitive grouping: `far-right` does not overlap `left` enough on its own, but joins via
     * `middle`. A single grouping pass would strand it in a row of its own below.
     */
    @Test
    fun `a block joins a row through another block rather than the seed`() {
        val blocks = listOf(
            block("far-right", 400, 12, 500, 34),
            block("left", 0, 0, 100, 20),
            block("middle", 150, 6, 250, 27),
        )

        assertEquals(listOf("left", "middle", "far-right"), idsOf(order(blocks)))
    }

    @Test
    fun `every input block appears exactly once in the output`() {
        val blocks = listOf(
            block("a", 0, 0, 100, 20),
            block("b", 300, 0, 400, 20),
            block("c", 0, 100, 100, 120),
            block("d", 0, 200, 400, 260),
        )

        val ordered = order(blocks)

        assertEquals(blocks.size, ordered.size)
        assertEquals(blocks.map { it.id }.toSet(), ordered.map { it.id }.toSet())
    }
}
