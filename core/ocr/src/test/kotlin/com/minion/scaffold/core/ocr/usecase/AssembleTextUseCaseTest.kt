package com.minion.scaffold.core.ocr.usecase

import com.minion.scaffold.core.ocr.model.BoundingBox
import com.minion.scaffold.core.ocr.model.RecognizedBlock
import com.minion.scaffold.core.ocr.model.RecognizedText
import org.junit.Assert.assertEquals
import org.junit.Test

class AssembleTextUseCaseTest {

    private val assemble = AssembleTextUseCase()

    private fun text(vararg lines: String) = RecognizedText(
        blocks = lines.mapIndexed { index, line ->
            RecognizedBlock(
                id = index.toString(),
                text = line,
                box = BoundingBox(0, index * 100, 100, index * 100 + 20),
                confidence = null,
            )
        },
    )

    private fun allIds(text: RecognizedText) = text.blocks.mapTo(mutableSetOf()) { it.id }

    @Test
    fun `selected blocks are joined by newline`() {
        val recognized = text("first", "second")

        assertEquals("first\nsecond", assemble(recognized, allIds(recognized)))
    }

    @Test
    fun `deselected blocks are dropped`() {
        val recognized = text("keep", "drop", "keep too")

        assertEquals("keep\nkeep too", assemble(recognized, setOf("0", "2")))
    }

    @Test
    fun `selecting nothing yields an empty string`() {
        assertEquals("", assemble(text("a", "b"), emptySet()))
    }

    @Test
    fun `output follows block order, not selection order`() {
        val recognized = text("first", "second")

        // A set has no order; the result must still read top-to-bottom.
        assertEquals("first\nsecond", assemble(recognized, setOf("1", "0")))
    }

    @Test
    fun `captures are separated by a blank line`() {
        val first = text("page one")
        val second = text("page two")

        val result = assemble.across(
            listOf(first to allIds(first), second to allIds(second)),
        )

        assertEquals("page one\n\npage two", result)
    }

    @Test
    fun `a capture with nothing selected leaves no gap`() {
        val first = text("page one")
        val empty = text("ignored")
        val third = text("page three")

        val result = assemble.across(
            listOf(first to allIds(first), empty to emptySet(), third to allIds(third)),
        )

        assertEquals("page one\n\npage three", result)
    }
}
