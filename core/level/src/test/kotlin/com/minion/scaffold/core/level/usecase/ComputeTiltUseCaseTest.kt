package com.minion.scaffold.core.level.usecase

import com.minion.scaffold.core.level.Synthetic
import com.minion.scaffold.core.level.model.UpVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class ComputeTiltUseCaseTest {

    private val computeTilt = ComputeTiltUseCase()

    // --- Sign conventions, pinned with literal vectors ------------------------------------

    @Test
    fun `flat on its back reads level`() {
        val tilt = computeTilt(UpVector(0.0, 0.0, 1.0))

        assertEquals(0.0, tilt.tiltX, EXACT)
        assertEquals(0.0, tilt.tiltY, EXACT)
        assertEquals(0.0, tilt.inclination, EXACT)
    }

    @Test
    fun `positive tiltX means the right edge is high`() {
        // Up leaning towards +x: the right-hand edge has been lifted.
        val tilt = computeTilt(UpVector(x = 0.5, y = 0.0, z = kotlin.math.sqrt(0.75)))

        assertEquals(30.0, tilt.tiltX, EXACT)
        assertEquals(0.0, tilt.tiltY, EXACT)
    }

    @Test
    fun `positive tiltY means the top edge is high`() {
        val tilt = computeTilt(UpVector(x = 0.0, y = 0.5, z = kotlin.math.sqrt(0.75)))

        assertEquals(30.0, tilt.tiltY, EXACT)
        assertEquals(0.0, tilt.tiltX, EXACT)
    }

    @Test
    fun `downhill points away from the raised edge`() {
        // Right edge high, so downhill is towards -x, which is a bearing of 180 degrees.
        val tilt = computeTilt(UpVector(x = 0.5, y = 0.0, z = kotlin.math.sqrt(0.75)))

        assertEquals(180.0, abs(tilt.downhillBearing), EXACT)
    }

    // --- The synthetic round trip: the proof the maths is right ---------------------------

    @Test
    fun `recovers the pitch and roll it was built from`() {
        for (pitch in -80..80 step 10) {
            for (roll in -80..80 step 10) {
                val tilt = computeTilt(Synthetic.up(pitch.toDouble(), roll.toDouble()))

                assertEquals("pitch $pitch/$roll", pitch.toDouble(), tilt.tiltY, PRECISE)
            }
        }
    }

    @Test
    fun `inclination matches the angle from vertical for random orientations`() {
        val random = Random(42)

        repeat(500) {
            val pitch = random.nextDouble(-85.0, 85.0)
            val roll = random.nextDouble(-85.0, 85.0)
            val up = Synthetic.up(pitch, roll)

            val expected = Math.toDegrees(up.angleTo(UpVector(0.0, 0.0, 1.0)))

            assertEquals(expected, computeTilt(up).inclination, PRECISE)
        }
    }

    @Test
    fun `inclination is not the hypotenuse of the two axis tilts`() {
        // The trap this guards: tiltX and tiltY are independent axis elevations, not vector
        // components. hypot() of them agrees near level and drifts badly by 40 degrees, which is
        // exactly the bug that survives testing on a nearly-flat desk.
        val tilt = computeTilt(Synthetic.up(pitchDegrees = 40.0, rollDegrees = 40.0))
        val naive = hypot(tilt.tiltX, tilt.tiltY)

        assertTrue(
            "naive=$naive true=${tilt.inclination}",
            abs(naive - tilt.inclination) > 2.0,
        )
    }

    @Test
    fun `the axis identity holds`() {
        // sin^2(tiltX) + sin^2(tiltY) + uz^2 == 1, the relationship that actually connects them.
        val up = Synthetic.up(pitchDegrees = 35.0, rollDegrees = -25.0)
        val tilt = computeTilt(up)

        val sum = square(Math.sin(Math.toRadians(tilt.tiltX))) +
            square(Math.sin(Math.toRadians(tilt.tiltY))) +
            square(up.z)

        assertEquals(1.0, sum, PRECISE)
    }

    // --- Edge mode: the finding that separates a correct formula from a plausible one ------

    @Test
    fun `edge deviation is unaffected by out-of-plane lean`() {
        // Nobody holds a phone perfectly flat against a door frame. The reading must not care.
        val readings = (0..30 step 5).map { lean ->
            computeTilt(Synthetic.edgeUp(deviationDegrees = 5.0, leanDegrees = lean.toDouble()))
                .edgeDeviation
        }

        readings.forEach { assertEquals(5.0, it, PRECISE) }
    }

    @Test
    fun `in-plane roll would have under-reported under lean`() {
        // Documents why edgeDeviation is not atan2(x, y): that formula scales by cos(lean), losing
        // 13% at 30 degrees. If this ever stops failing, the edge formula has regressed.
        val up = Synthetic.edgeUp(deviationDegrees = 5.0, leanDegrees = 30.0)
        val inPlaneRoll = Math.toDegrees(kotlin.math.atan2(up.x, up.y))

        assertTrue("inPlaneRoll=$inPlaneRoll", inPlaneRoll < 4.5)
        assertEquals(5.0, computeTilt(up).edgeDeviation, PRECISE)
    }

    @Test
    fun `edge deviation is signed towards the leaning side`() {
        val leaningRight = computeTilt(Synthetic.edgeUp(deviationDegrees = 5.0))

        // Top leaning towards screen-right gives ux < 0, so the sign is inverted relative to flat
        // mode. Guards against someone "simplifying" it to sign(ux).
        assertTrue(Synthetic.edgeUp(5.0).x > 0)
        assertTrue(leaningRight.signedEdgeDeviation < 0)
    }

    @Test
    fun `out-of-plane lean is reported`() {
        val tilt = computeTilt(Synthetic.edgeUp(deviationDegrees = 10.0, leanDegrees = 20.0))

        assertTrue("lean=${tilt.outOfPlaneLean}", tilt.outOfPlaneLean > 0.0)
    }

    // --- Conditioning -------------------------------------------------------------------

    @Test
    fun `tenths of a degree are distinguishable at level`() {
        // The acos() formula this module avoids has a 0.02 degree floor in Float32, a tenth of the
        // whole tolerance budget. Double plus atan2 must resolve far finer than that.
        val a = computeTilt(Synthetic.up(pitchDegrees = 0.05)).tiltY
        val b = computeTilt(Synthetic.up(pitchDegrees = 0.06)).tiltY

        assertTrue("a=$a b=$b", abs(b - a) > 0.009)
    }

    @Test
    fun `a face-down phone reports the same inclination as face-up`() {
        val faceUp = computeTilt(UpVector(0.0, 0.0, 1.0)).inclination
        val faceDown = computeTilt(UpVector(0.0, 0.0, -1.0)).inclination

        assertEquals(faceUp, faceDown, EXACT)
    }

    private fun square(value: Double) = value * value

    private companion object {
        /** Degrees. Tight enough that a wrong formula cannot pass, loose enough for Double error. */
        const val PRECISE = 1e-9
        const val EXACT = 1e-12
    }
}
