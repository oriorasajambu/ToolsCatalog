package com.minion.scaffold.core.level.usecase

import com.minion.scaffold.core.level.Synthetic
import com.minion.scaffold.core.level.model.Calibration
import com.minion.scaffold.core.level.model.UpVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The proof that the flip calibration is right.
 *
 * Every test here injects a *known* bias into synthesised readings and asserts it comes back out.
 * That is the only way to establish correctness for this feature: on a real phone, a wrong bias and
 * a right one produce readings that look exactly alike.
 */
class FlipCalibrationTest {

    private val solve = SolveFlipCalibrationUseCase()
    private val applyCalibration = ApplyCalibrationUseCase()
    private val computeTilt = ComputeTiltUseCase()

    @Test
    fun `recovers an injected bias`() {
        val bias = rotationVector(axisX = 0.6, axisY = -0.8, degrees = 0.7)

        val outcome = solveFlipOn(surfacePitch = 1.3, surfaceRoll = -0.8, bias = bias)
        val solved = (outcome as CalibrationOutcome.Solved).calibration

        assertEquals(bias.x, solved.x, TIGHT)
        assertEquals(bias.y, solved.y, TIGHT)
        assertEquals(bias.z, solved.z, TIGHT)
    }

    @Test
    fun `recovers the bias even on a sloped surface`() {
        // The entire reason for a two-point flip. A single-point zero would bake this 4-degree
        // slope into the calibration permanently and invisibly.
        val bias = rotationVector(axisX = 1.0, axisY = 0.0, degrees = 0.4)

        val outcome = solveFlipOn(surfacePitch = 4.0, surfaceRoll = 3.0, bias = bias)
        val solved = (outcome as CalibrationOutcome.Solved).calibration

        assertEquals(bias.x, solved.x, TIGHT)
        assertEquals(bias.y, solved.y, TIGHT)
    }

    @Test
    fun `reports the calibration surface's own tilt`() {
        val outcome = solveFlipOn(surfacePitch = 3.0, surfaceRoll = 0.0, bias = Calibration.NONE)
        val solved = (outcome as CalibrationOutcome.Solved).calibration

        assertEquals(3.0, solved.surfaceTiltDegrees, 0.05)
    }

    @Test
    fun `applying the solved calibration makes a level surface read level`() {
        // End to end: bias the readings, solve, apply, and the residual must vanish.
        val bias = rotationVector(axisX = 0.3, axisY = 0.95, degrees = 0.9)
        val outcome = solveFlipOn(surfacePitch = 0.0, surfaceRoll = 0.0, bias = bias)
        val solved = (outcome as CalibrationOutcome.Solved).calibration

        val biased = biasedReading(Synthetic.up(0.0, 0.0), bias)
        val corrected = applyCalibration(biased, solved)
        val tilt = computeTilt(corrected)

        assertEquals(0.0, tilt.inclination, 1e-6)
    }

    @Test
    fun `an uncalibrated device reads its bias as real tilt`() {
        // Documents what calibration is for: without it, this much error is indistinguishable from
        // a genuinely sloped surface.
        val bias = rotationVector(axisX = 1.0, axisY = 0.0, degrees = 0.6)
        val tilt = computeTilt(biasedReading(Synthetic.up(0.0, 0.0), bias))

        assertEquals(0.6, tilt.inclination, 0.01)
    }

    @Test
    fun `a flat flip is marked as not correcting edge mode`() {
        // A 180-degree flip observes only the components perpendicular to the current up direction,
        // so a flat calibration is blind to the one an edge reading needs. The UI says so.
        val outcome = solveFlipOn(surfacePitch = 1.0, surfaceRoll = 0.0, bias = Calibration.NONE)
        val solved = (outcome as CalibrationOutcome.Solved).calibration

        assertFalse(solved.correctsEdgePose)
        assertEquals(Calibration.MASK_X or Calibration.MASK_Y, solved.measuredMask)
        assertEquals(0.0, solved.z, TIGHT)
    }

    // --- Rejections -----------------------------------------------------------------------

    @Test
    fun `rejects a phone that was turned over instead of spun`() {
        // The most likely user error by far, and it produces a plausible-looking wrong answer.
        val first = Synthetic.up(pitchDegrees = 2.0)
        val turnedOver = UpVector(first.x, first.y, -first.z)

        val outcome = solve(first, turnedOver, bothSteady = true, takenAtMillis = 0L)

        assertEquals(
            CalibrationOutcome.Rejected(CalibrationRejection.NotAFlip),
            outcome,
        )
    }

    @Test
    fun `rejects a 90 degree turn on a sloped surface`() {
        // A quarter-turn leaves the mean well away from vertical, so it lands on the magnitude
        // bound. Reported as ImplausibleResult rather than NotAFlip because the two are genuinely
        // indistinguishable from the data — and the advice is the same either way: do it again,
        // rotating flat through 180 degrees.
        val first = Synthetic.up(pitchDegrees = 10.0)
        val quarterTurn = UpVector(x = first.y, y = -first.x, z = first.z)

        val outcome = solve(first, quarterTurn, bothSteady = true, takenAtMillis = 0L)

        assertEquals(
            CalibrationOutcome.Rejected(CalibrationRejection.ImplausibleResult),
            outcome,
        )
    }

    @Test
    fun `a 90 degree turn on a level surface is undetectable and under-corrects`() {
        // Pins the one gap in the validation, so it stays a known limitation rather than becoming
        // a surprise. On a level surface both readings are dominated by the bias, and averaging two
        // copies of it a quarter-turn apart recovers 1/sqrt(2) of its magnitude — too small to trip
        // the plausibility bound, so it is accepted.
        //
        // The result is under-corrected rather than wrong, which is why the defence against this is
        // the calibration flow's wording and not arithmetic.
        val bias = rotationVector(axisX = 1.0, axisY = 0.0, degrees = 0.5)
        val first = biasedReading(Synthetic.up(0.0, 0.0), bias)
        val quarterTurn = UpVector(x = first.y, y = -first.x, z = first.z)

        val outcome = solve(first, quarterTurn, bothSteady = true, takenAtMillis = 0L)
        val solved = (outcome as CalibrationOutcome.Solved).calibration

        // 1/sqrt(2) is the small-angle limit; at half a degree the next-order term is already
        // visible around the eighth significant figure, hence the looser bound here than elsewhere.
        assertEquals(bias.angleRadians / kotlin.math.sqrt(2.0), solved.angleRadians, 1e-7)
    }

    @Test
    fun `accepts a flip on a perfectly level surface`() {
        // The direction check must be magnitude-gated: on a level surface both readings equal the
        // bias, so there is no direction to compare and an ungated test would reject the best
        // possible calibration.
        val outcome = solveFlipOn(surfacePitch = 0.0, surfaceRoll = 0.0, bias = Calibration.NONE)

        assertTrue(outcome is CalibrationOutcome.Solved)
    }

    @Test
    fun `rejects readings taken while moving`() {
        val outcome = solve(
            Synthetic.up(1.0),
            flipped(Synthetic.up(1.0)),
            bothSteady = false,
            takenAtMillis = 0L,
        )

        assertEquals(CalibrationOutcome.Rejected(CalibrationRejection.NotSteady), outcome)
    }

    @Test
    fun `rejects an implausibly large bias`() {
        val bias = rotationVector(axisX = 1.0, axisY = 0.0, degrees = 8.0)
        val outcome = solveFlipOn(surfacePitch = 0.0, surfaceRoll = 0.0, bias = bias)

        assertEquals(
            CalibrationOutcome.Rejected(CalibrationRejection.ImplausibleResult),
            outcome,
        )
    }

    @Test
    fun `no calibration leaves a reading untouched`() {
        val up = Synthetic.up(pitchDegrees = 12.0, rollDegrees = -5.0)
        val corrected = applyCalibration(up, Calibration.NONE)

        assertEquals(up.x, corrected.x, TIGHT)
        assertEquals(up.y, corrected.y, TIGHT)
        assertEquals(up.z, corrected.z, TIGHT)
    }

    @Test
    fun `applying a calibration preserves unit length`() {
        val bias = rotationVector(axisX = 0.5, axisY = 0.5, degrees = 2.0)
        val corrected = applyCalibration(Synthetic.up(20.0, 30.0), bias)
        val magnitude = kotlin.math.sqrt(
            corrected.x * corrected.x + corrected.y * corrected.y + corrected.z * corrected.z,
        )

        assertEquals(1.0, magnitude, 1e-12)
    }

    // --- Helpers --------------------------------------------------------------------------

    /** Solves a flip performed on a surface of the given tilt, with [bias] baked into both reads. */
    private fun solveFlipOn(
        surfacePitch: Double,
        surfaceRoll: Double,
        bias: Calibration,
    ): CalibrationOutcome {
        val truth = Synthetic.up(surfacePitch, surfaceRoll)
        val first = biasedReading(truth, bias)
        val second = biasedReading(flipped(truth), bias)

        return solve(first, second, bothSteady = true, takenAtMillis = 1_000L)
    }

    /** The same surface after spinning the phone 180 degrees about the vertical. */
    private fun flipped(up: UpVector) = UpVector(x = -up.x, y = -up.y, z = up.z)

    /**
     * What a device with [bias] would report for a true orientation of [truth].
     *
     * The forward direction of the rotation, written independently of ApplyCalibrationUseCase so
     * the tests are not just checking the code agrees with itself.
     */
    private fun biasedReading(truth: UpVector, bias: Calibration): UpVector {
        val angle = bias.angleRadians
        if (angle < 1e-12) return truth

        val kx = bias.x / angle
        val ky = bias.y / angle
        val kz = bias.z / angle

        val c = cos(angle)
        val s = sin(angle)
        val dot = kx * truth.x + ky * truth.y + kz * truth.z

        return UpVector(
            x = truth.x * c + (ky * truth.z - kz * truth.y) * s + kx * dot * (1 - c),
            y = truth.y * c + (kz * truth.x - kx * truth.z) * s + ky * dot * (1 - c),
            z = truth.z * c + (kx * truth.y - ky * truth.x) * s + kz * dot * (1 - c),
        )
    }

    /** A bias rotation of [degrees] about the given in-plane axis. */
    private fun rotationVector(axisX: Double, axisY: Double, degrees: Double): Calibration {
        val magnitude = kotlin.math.hypot(axisX, axisY)
        val radians = Math.toRadians(degrees)

        return Calibration.NONE.copy(
            x = axisX / magnitude * radians,
            y = axisY / magnitude * radians,
            z = 0.0,
        )
    }

    private companion object {
        /** Radians. The solve is exact, so this only absorbs Double rounding. */
        const val TIGHT = 1e-9
    }
}
