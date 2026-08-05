package com.minion.scaffold.core.level.usecase

import com.minion.scaffold.core.level.Synthetic
import com.minion.scaffold.core.level.model.EdgeQuadrant
import com.minion.scaffold.core.level.model.LevelPose
import com.minion.scaffold.core.level.model.PoseState
import com.minion.scaffold.core.level.model.UpVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ResolvePoseUseCaseTest {

    private val resolvePose = ResolvePoseUseCase()

    // --- Basics ---------------------------------------------------------------------------

    @Test
    fun `settles into flat when lying face up`() {
        val state = feed(List(20) { UpVector(0.0, 0.0, 1.0) })

        assertEquals(LevelPose.Flat, state.pose)
    }

    @Test
    fun `face down is its own pose, not a mirrored flat`() {
        // A phone rests on its back, which is not planar because of the camera bump — so the two
        // orientations genuinely have different biases and must be distinguishable.
        val state = feed(List(20) { UpVector(0.0, 0.0, -1.0) })

        assertEquals(LevelPose.FaceDown, state.pose)
    }

    @Test
    fun `settles into edge when stood upright`() {
        val state = feed(List(20) { Synthetic.edgeUp(deviationDegrees = 0.0) })

        assertEquals(LevelPose.Edge(EdgeQuadrant.Bottom), state.pose)
    }

    // --- The five oscillation tests -------------------------------------------------------

    @Test
    fun `applying the same sample twice changes nothing`() {
        // Idempotence. Cheap, and it catches a surprising amount of accidental state churn.
        for (pitch in 0..90 step 5) {
            val up = Synthetic.up(pitchDegrees = pitch.toDouble())
            val once = resolvePose(PoseState(), up, 0L)
            val twice = resolvePose(once, up, SAMPLE_NANOS)

            assertEquals("pitch=$pitch", once.pose, twice.pose)
        }
    }

    @Test
    fun `does not transition while dithering on a threshold`() {
        // Pinned exactly at each threshold with ten times plausible sensor noise. The assertion is
        // the transition COUNT, not the final value — a test that only checks where it ends up
        // would pass while flickering the whole way there.
        for (threshold in listOf(30.0, 45.0, 60.0)) {
            val random = Random(7)
            val samples = List(2_000) {
                Synthetic.up(pitchDegrees = threshold + random.nextDouble(-0.5, 0.5))
            }

            val transitions = countTransitions(samples)

            // One settle at the start is allowed; nothing after it.
            assertTrue("threshold=$threshold transitions=$transitions", transitions <= 1)
        }
    }

    @Test
    fun `a monotone sweep never steps backwards`() {
        val samples = (0..90).map { Synthetic.up(pitchDegrees = it.toDouble()) }
        val poses = poseSequence(samples).compressRuns()

        // Transitional only at the start, while the first dwell runs. Crucially there is no second
        // Transitional between Flat and Edge: the hysteresis band holds the previous pose rather
        // than reporting a state of its own, so the user never passes through a visible limbo.
        assertEquals(
            listOf(LevelPose.Transitional, LevelPose.Flat, LevelPose.Edge(EdgeQuadrant.Bottom)),
            poses,
        )
    }

    @Test
    fun `the quadrant switch points differ by twice the hysteresis`() {
        // The sharp one. A naive "does it flicker" test passes even with zero hysteresis; this
        // asserts the band is actually the width it claims, in both directions.
        val up = sweepQuadrantBearing(ascending = true)
        val down = sweepQuadrantBearing(ascending = false)

        val expected = 2 * ResolvePoseUseCase.QUADRANT_HYSTERESIS

        assertEquals(expected, abs(up - down), 1.0)
    }

    @Test
    fun `a noisy round trip transitions exactly twice`() {
        val random = Random(42)
        val out = (0..90).map { Synthetic.up(it + random.nextDouble(-0.4, 0.4)) }
        val back = (90 downTo 0).map { Synthetic.up(it + random.nextDouble(-0.4, 0.4)) }

        val transitions = countTransitions(out + back)

        // Flat -> Edge -> Flat. Anything more is chatter in the band.
        assertEquals(2, transitions)
    }

    // --- The quadrant gate ----------------------------------------------------------------

    @Test
    fun `the quadrant does not spin while the phone lies flat`() {
        // Flat means the in-plane bearing is pure noise. Without the magnitude gate the quadrant
        // churns the whole time and the first frame after tipping up inherits garbage.
        val random = Random(3)
        var state = PoseState(quadrant = EdgeQuadrant.Bottom)

        repeat(500) { index ->
            val up = UpVector(
                x = random.nextDouble(-0.01, 0.01),
                y = random.nextDouble(-0.01, 0.01),
                z = 1.0,
            )
            state = resolvePose(state, up, index * SAMPLE_NANOS)
        }

        assertEquals(EdgeQuadrant.Bottom, state.quadrant)
    }

    @Test
    fun `tipping onto the left edge reports the left quadrant`() {
        // Left edge down: world up leans towards +x.
        val state = feed(List(20) { UpVector(x = 1.0, y = 0.0, z = 0.0) })

        assertEquals(LevelPose.Edge(EdgeQuadrant.Right), state.pose)
    }

    // --- Helpers --------------------------------------------------------------------------

    private fun feed(samples: List<UpVector>, initial: PoseState = PoseState()): PoseState =
        samples.foldIndexed(initial) { index, state, up ->
            resolvePose(state, up, index * SAMPLE_NANOS)
        }

    private fun poseSequence(samples: List<UpVector>): List<LevelPose> {
        var state = PoseState()
        return samples.mapIndexed { index, up ->
            state = resolvePose(state, up, index * SAMPLE_NANOS)
            state.pose
        }
    }

    /**
     * Counts pose changes after the machine has first settled, so the initial
     * Transitional -> Flat commit is not mistaken for chatter.
     */
    private fun countTransitions(samples: List<UpVector>): Int =
        poseSequence(samples)
            .dropWhile { it == LevelPose.Transitional }
            .zipWithNext()
            .count { (a, b) -> a != b }

    /**
     * The bearing at which the quadrant flips, sweeping in one direction or the other.
     *
     * Swept in tenths of a degree: a 1° sweep quantises each switch point outward by up to a
     * degree, and with two boundaries that is enough to make a correct 16° band measure 18°.
     */
    private fun sweepQuadrantBearing(ascending: Boolean): Double {
        var state = if (ascending) {
            feed(List(20) { edgeAtBearing(0.0) }, PoseState(quadrant = EdgeQuadrant.Bottom))
        } else {
            // Start already in the destination quadrant, so the sweep is genuinely returning.
            feed(List(20) { edgeAtBearing(90.0) }, PoseState(quadrant = EdgeQuadrant.Right))
        }

        var index = 0
        for (step in 0..900) {
            val bearing = if (ascending) step * 0.1 else 90.0 - step * 0.1
            val previous = state.quadrant
            state = resolvePose(state, edgeAtBearing(bearing), (index++) * SAMPLE_NANOS)
            if (state.quadrant != previous) return bearing
        }
        return Double.NaN
    }

    /** A vertical phone rolled so that "up" sits at [bearing] degrees from the device's +y axis. */
    private fun edgeAtBearing(bearing: Double): UpVector {
        val radians = Math.toRadians(bearing)
        return UpVector(x = kotlin.math.sin(radians), y = kotlin.math.cos(radians), z = 0.0)
    }

    private fun <T> List<T>.compressRuns(): List<T> =
        fold(mutableListOf<T>()) { acc, item ->
            if (acc.lastOrNull() != item) acc.add(item)
            acc
        }

    private companion object {
        /** 20ms, roughly SENSOR_DELAY_GAME. */
        const val SAMPLE_NANOS = 20_000_000L
    }
}
