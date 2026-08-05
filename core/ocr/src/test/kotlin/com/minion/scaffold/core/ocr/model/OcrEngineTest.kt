package com.minion.scaffold.core.ocr.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrEngineTest {

    @Test
    fun `resolves a stored name`() {
        assertEquals(OcrEngine.PaddleOcr, OcrEngine.ofName("PaddleOcr"))
    }

    @Test
    fun `falls back when the name is unknown`() {
        // A preference written by a build that had an entry this one does not — it must degrade to
        // the default rather than throwing on the way to the settings screen.
        assertEquals(OcrEngine.DEFAULT, OcrEngine.ofName("Tesseract"))
    }

    @Test
    fun `falls back when nothing is stored`() {
        assertEquals(OcrEngine.DEFAULT, OcrEngine.ofName(null))
    }

    @Test
    fun `default is ML Kit`() {
        // Guards the upgrade path: an existing install must keep the engine it already had.
        assertEquals(OcrEngine.MlKit, OcrEngine.DEFAULT)
    }
}
