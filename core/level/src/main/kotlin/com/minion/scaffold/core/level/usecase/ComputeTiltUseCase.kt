package com.minion.scaffold.core.level.usecase

import com.minion.scaffold.core.level.model.Tilt
import com.minion.scaffold.core.level.model.UpVector
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sign

/**
 * Turns the direction of gravity into the angles the level displays.
 *
 * ## Why every formula here is `atan2`
 *
 * The obvious spellings are `asin(ux)` for an axis elevation and `acos(|uz|)` for the total tilt.
 * Both are avoided, for two different reasons.
 *
 * `atan2(ux, hypot(uy, uz))` is *identically* `asin(ux)` — not an approximation, the same function,
 * since `hypot(uy, uz) = sqrt(1 - ux²)` for a unit vector. What differs is everything around it:
 * `asin` needs its argument clamped to ±1, and a clamp turns a genuinely broken sample (freefall, a
 * NaN, a driver glitch) into a plausible-looking exactly-90° reading. `atan2` has domain ℝ², needs
 * no clamp, and never divides by the magnitude at all.
 *
 * `acos` is worse, and its failure lands precisely where this feature lives. Its derivative is
 * infinite at argument 1 — which *is* level. Near zero tilt, `|uz| ≈ 1 − θ²/2`, so the smallest
 * distinguishable angle is set by the ulp near 1.0. That is 0.02° in Float32 and ~1e-6° in Double.
 * Working in `Double` fixes it; using `atan2` means it was never a question.
 *
 * ## Sign conventions, stated once
 *
 * With the phone in its natural orientation: `+x` right, `+y` up the screen, `+z` out of the screen.
 * Gravity is the reaction force, so `u` points *up* in the world. Therefore `ux > 0` means the right
 * edge is high, and `uy > 0` means the top edge is high.
 */
class ComputeTiltUseCase @Inject constructor() {

    /**
     * Derives every displayed angle from the direction of gravity.
     *
     * @param up The corrected, unit-length up-vector.
     * @return The [Tilt] holding all axis, inclination, bearing and edge readings in degrees.
     */
    operator fun invoke(up: UpVector): Tilt {
        val (x, y, z) = up

        // Independent elevations of each in-plane axis above horizontal. These are not Euler
        // angles and are not components of a vector — see Tilt.inclination.
        val tiltX = atan2(x, hypot(y, z))
        val tiltY = atan2(y, hypot(x, z))

        // Total tilt of the screen plane. abs(z) so a face-down phone reports the same tilt as a
        // face-up one rather than 180° minus it.
        val inclination = atan2(hypot(x, y), abs(z))

        // Downhill is opposite the up-vector's in-plane component.
        val downhill = atan2(-y, -x)

        return Tilt(
            tiltX = Math.toDegrees(tiltX),
            tiltY = Math.toDegrees(tiltY),
            inclination = Math.toDegrees(inclination),
            downhillBearing = Math.toDegrees(downhill),
            edgeDeviation = Math.toDegrees(edgeDeviation(up)),
            signedEdgeDeviation = Math.toDegrees(signedEdgeDeviation(up)),
            outOfPlaneLean = Math.toDegrees(outOfPlaneLean(up)),
        )
    }

    /**
     * How far the device's long axis is from vertical — the plumb reading.
     *
     * **This is the formula that makes edge mode correct, and the obvious alternative is wrong.**
     * The tempting choice is in-plane roll, `atan2(ux, uy)`. But nobody presses a phone perfectly
     * flat against a door frame; there is always some out-of-plane lean ψ. Writing the up-vector for
     * a frame that is θ off plumb, held with lean ψ:
     *
     * ```
     * u = (sin θ · cos ψ,  cos θ,  sin θ · sin ψ)
     * ```
     *
     * In-plane roll then reads `atan2(sin θ cos ψ, cos θ) ≈ θ · cos ψ` — it **under-reports by
     * cos ψ**, which is −1.5% at 10° of lean and −13% at 30°. The angle between the `+y` axis and
     * up, however, is `atan2(sin θ, cos θ) = θ` exactly, for any ψ. The lean cancels completely.
     *
     * `atan2(hypot(x, z), y)` rather than `acos(y)` for the reason in the class KDoc: a plumb check
     * is a measurement *at* zero, which is where `acos` is worst.
     */
    private fun edgeDeviation(up: UpVector): Double = atan2(hypot(up.x, up.z), up.y)

    /**
     * [edgeDeviation] with a direction attached.
     *
     * `sign(-x)`, note — and this is a genuine trap. Rotating the phone so its top leans towards
     * screen-right gives `ux < 0`, the opposite of flat mode where `ux > 0` means the right side is
     * high. Reusing `ux`'s sign across both poses inverts the arrow in exactly one of them, and it
     * is the kind of thing that survives review because each pose looks right on its own.
     */
    private fun signedEdgeDeviation(up: UpVector): Double {
        val magnitude = edgeDeviation(up)
        val direction = if (up.x == 0.0) 1.0 else sign(-up.x)
        return magnitude * direction
    }

    /** How far off the plane the phone is being held — the confidence signal for edge mode. */
    private fun outOfPlaneLean(up: UpVector): Double = atan2(abs(up.z), hypot(up.x, up.y))
}
