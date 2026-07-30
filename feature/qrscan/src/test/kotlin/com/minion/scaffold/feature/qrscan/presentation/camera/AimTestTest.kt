package com.minion.scaffold.feature.qrscan.presentation.camera

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AimTestTest {

    private val reticle = Rect(left = 100f, top = 100f, right = 300f, bottom = 300f)

    @Test
    fun `a code inside the reticle is aimed`() {
        assertTrue(isAimed(code = Rect(150f, 150f, 250f, 250f), reticle = reticle))
    }

    @Test
    fun `a code exactly filling the reticle is aimed`() {
        assertTrue(isAimed(code = reticle, reticle = reticle))
    }

    @Test
    fun `a code touching the reticle edge from inside is aimed`() {
        assertTrue(isAimed(code = Rect(100f, 100f, 200f, 200f), reticle = reticle))
    }

    /**
     * The clause that stops the feature trapping its user: once the phone is close enough that the
     * code is bigger than the box, strict containment could never be satisfied.
     */
    @Test
    fun `a code engulfing the reticle is aimed`() {
        assertTrue(isAimed(code = Rect(0f, 0f, 500f, 500f), reticle = reticle))
    }

    @Test
    fun `a code straddling the reticle edge is not aimed`() {
        assertFalse(isAimed(code = Rect(250f, 150f, 350f, 250f), reticle = reticle))
    }

    @Test
    fun `a code entirely outside the reticle is not aimed`() {
        assertFalse(isAimed(code = Rect(400f, 400f, 450f, 450f), reticle = reticle))
    }

    /** Overlapping is not aiming — a code half in the box is the case the box exists to reject. */
    @Test
    fun `a code overlapping a corner is not aimed`() {
        assertFalse(isAimed(code = Rect(50f, 50f, 150f, 150f), reticle = reticle))
    }

    @Test
    fun `a code taller than the reticle but narrower is not aimed`() {
        assertFalse(isAimed(code = Rect(150f, 0f, 250f, 500f), reticle = reticle))
    }

    @Test
    fun `a zero-area detection is not aimed`() {
        assertFalse(isAimed(code = Rect(200f, 200f, 200f, 200f), reticle = reticle))
    }

    @Test
    fun `nothing is aimed at a zero-area reticle`() {
        assertFalse(
            isAimed(code = Rect(150f, 150f, 250f, 250f), reticle = Rect(200f, 200f, 200f, 200f)),
        )
    }

    @Test
    fun `the reticle is square and centred in a portrait viewport`() {
        val reticle = reticleIn(IntSize(width = 1000, height = 2000))

        assertEquals(700f, reticle.width)
        assertEquals(700f, reticle.height)
        assertEquals(500f, reticle.center.x)
        assertEquals(1000f, reticle.center.y)
    }

    /** Sized off the shorter edge, so rotating the device does not change the aiming area. */
    @Test
    fun `the reticle is square and centred in a landscape viewport`() {
        val reticle = reticleIn(IntSize(width = 2000, height = 1000))

        assertEquals(700f, reticle.width)
        assertEquals(700f, reticle.height)
        assertEquals(1000f, reticle.center.x)
        assertEquals(500f, reticle.center.y)
    }

    @Test
    fun `a viewport with no size yields an empty reticle that nothing can aim at`() {
        val reticle = reticleIn(IntSize.Zero)

        assertTrue(reticle.isEmpty)
        assertFalse(isAimed(code = Rect(0f, 0f, 10f, 10f), reticle = reticle))
    }
}
