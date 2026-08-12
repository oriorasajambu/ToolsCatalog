package com.minion.scaffold.core.level.usecase

import com.minion.scaffold.core.level.model.Calibration
import com.minion.scaffold.core.level.model.UpVector
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** Why a flip calibration was rejected. Each maps to specific advice, not a generic failure. */
enum class CalibrationRejection {

    /** The phone was not still enough during one of the captures. */
    NotSteady,

    /** One of the readings was not plausibly gravity at rest. */
    ImplausibleReading,

    /**
     * The second capture was not a 180° turn about the vertical.
     *
     * Overwhelmingly the most common user error: turning the phone over like a page instead of
     * spinning it flat. That negates a different pair of components and makes the result garbage.
     */
    NotAFlip,

    /** The solved bias is larger than any real device misalignment. Something else went wrong. */
    ImplausibleResult,
}

sealed interface CalibrationOutcome {

    /**
     * The flip produced a usable bias.
     *
     * @property calibration The solved device bias.
     */
    data class Solved(val calibration: Calibration) : CalibrationOutcome

    /**
     * The flip was rejected and no bias was stored.
     *
     * @property reason Why the flip was rejected.
     */
    data class Rejected(val reason: CalibrationRejection) : CalibrationOutcome
}

/**
 * Works out the device's own tilt bias from two readings taken 180° apart.
 *
 * ## The idea
 *
 * A single reading cannot separate "this surface is sloped" from "this phone is biased" — they look
 * identical. Two readings can. Spin the phone 180° about the vertical without moving it off the
 * surface, and the *surface's* contribution reverses while the *device's* stays put:
 *
 * ```
 * reading₁ =  true + bias
 * reading₂ = −true + bias        ⟹  bias = (reading₁ + reading₂) / 2
 * ```
 *
 * That is why this works on a surface that is not level, which is the whole point — the user has no
 * way to supply a guaranteed-level reference, and a single-point zero would bake the surface's error
 * in permanently and invisibly.
 *
 * `(reading₁ − reading₂) / 2` is the surface's true tilt, which comes out for free and is worth
 * showing back to the user as confirmation.
 *
 * ## The construction
 *
 * Rather than the small-angle algebra above, the solve is done exactly. Averaging the two unit
 * vectors gives where the device *thinks* vertical is; the bias is the minimum-angle rotation
 * carrying true vertical onto that — yaw-free by construction, so a flat flip cannot invent a bias
 * about the screen normal it never observed. [ApplyCalibrationUseCase] rotates back by it.
 */
class SolveFlipCalibrationUseCase @Inject constructor() {

    /**
     * Solves the device bias from two readings taken 180° apart.
     *
     * @param first         The reading before the flip, already averaged over a settled window.
     * @param second        The reading after.
     * @param bothSteady    Whether the stability detector reported Steady for both captures.
     * @param takenAtMillis When the calibration was taken, epoch millis, stamped onto the result.
     * @return [CalibrationOutcome.Solved] with the bias, or [CalibrationOutcome.Rejected] with the
     *         reason the flip could not be used.
     */
    operator fun invoke(
        first: UpVector,
        second: UpVector,
        bothSteady: Boolean,
        takenAtMillis: Long,
    ): CalibrationOutcome {
        if (!bothSteady) return CalibrationOutcome.Rejected(CalibrationRejection.NotSteady)

        if (!first.isUnit() || !second.isUnit()) {
            return CalibrationOutcome.Rejected(CalibrationRejection.ImplausibleReading)
        }

        // A rotation about world vertical cannot change how much of gravity lies along the screen
        // normal. If uz moved, the phone was turned over rather than spun — or it was lifted and
        // set down somewhere else. Deliberately chosen over the more obvious "are the two in-plane
        // readings opposite?", which is degenerate on a perfectly level surface: there both
        // readings equal the bias and there is no direction to compare.
        if (abs(first.z - second.z) > FLIP_Z_TOLERANCE) {
            return CalibrationOutcome.Rejected(CalibrationRejection.NotAFlip)
        }

        val mean = midpointOrNull(first, second)
            ?: return CalibrationOutcome.Rejected(CalibrationRejection.ImplausibleReading)

        // The magnitude bound does the rest of the work. A quarter-turn instead of a half-turn
        // leaves the mean well away from vertical, so it lands here on any surface with meaningful
        // tilt.
        //
        // **The gap, stated honestly:** a quarter-turn on a surface that is already level is not
        // detectable. Both readings are then dominated by the bias itself, and averaging two copies
        // of it a quarter-turn apart recovers `1/√2` of its magnitude pointing somewhere between
        // the two — so the result is under-corrected rather than plainly wrong, and small enough to
        // pass this bound. The defence is the instruction copy, not arithmetic: the calibration
        // flow has to say "rotate it flat, 180°, without lifting it", because no check here can
        // recover what the gesture failed to encode.
        val rotation = biasFrom(mean)
        if (rotation.angleRadians > MAX_BIAS_RADIANS) {
            return CalibrationOutcome.Rejected(CalibrationRejection.ImplausibleResult)
        }

        return CalibrationOutcome.Solved(
            rotation.copy(
                measuredMask = Calibration.MASK_X or Calibration.MASK_Y,
                takenAtMillis = takenAtMillis,
                surfaceTiltDegrees = surfaceTiltDegrees(first, second),
            ),
        )
    }

    /** Where the device thinks vertical is: the two readings averaged and renormalised. */
    private fun midpointOrNull(first: UpVector, second: UpVector): UpVector? {
        val x = first.x + second.x
        val y = first.y + second.y
        val z = first.z + second.z
        val magnitude = sqrt(x * x + y * y + z * z)

        return if (magnitude > 1e-6) UpVector(x / magnitude, y / magnitude, z / magnitude) else null
    }

    /**
     * The device's bias, as the minimum-angle rotation carrying true vertical onto [mean].
     *
     * **Direction matters and is easy to get backwards.** [mean] is where the device *thinks*
     * vertical is, so the rotation `ẑ → mean` is the error the device introduces — which is what
     * [Calibration] is documented to hold, and what [ApplyCalibrationUseCase] then rotates *back*
     * by. Solving `mean → ẑ` instead stores the correction rather than the bias, and since
     * applying negates it too, the two compound instead of cancelling and every reading comes out
     * twice as wrong as it started.
     *
     * Minimum-angle means no component about the vertical axis, which is exactly right: a flat flip
     * has no information about rotation around the screen normal, so this construction declines to
     * invent any.
     */
    private fun biasFrom(mean: UpVector): Calibration {
        // ẑ × mean, with ẑ = (0, 0, 1).
        val axisX = -mean.y
        val axisY = mean.x
        val axisZ = 0.0

        val crossMagnitude = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
        if (crossMagnitude < 1e-12) return Calibration.NONE

        val angle = atan2(crossMagnitude, mean.z)
        val scale = angle / crossMagnitude

        return Calibration.NONE.copy(x = axisX * scale, y = axisY * scale, z = axisZ * scale)
    }

    /**
     * How far off level the calibration surface itself was — free, and worth telling the user.
     *
     * `(first − second) / 2` cancels the bias and leaves the surface, the mirror of the sum that
     * cancels the surface and leaves the bias.
     */
    private fun surfaceTiltDegrees(first: UpVector, second: UpVector): Double {
        val x = (first.x - second.x) / 2.0
        val y = (first.y - second.y) / 2.0
        val inPlane = sqrt(x * x + y * y).coerceAtMost(1.0)

        return Math.toDegrees(atan2(inPlane, sqrt(1.0 - inPlane * inPlane)))
    }

    private fun UpVector.isUnit(): Boolean {
        val magnitude = sqrt(x * x + y * y + z * z)
        return abs(magnitude - 1.0) < 1e-3
    }

    private companion object {

        /** ~0.3°. A genuine flip about the vertical leaves uz untouched. */
        const val FLIP_Z_TOLERANCE = 0.005

        /** Real die-to-case misalignment is tenths of a degree. 3° means something else broke. */
        val MAX_BIAS_RADIANS = Math.toRadians(3.0)
    }
}
