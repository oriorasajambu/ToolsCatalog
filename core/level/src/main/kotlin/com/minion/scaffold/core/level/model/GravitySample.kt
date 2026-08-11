package com.minion.scaffold.core.level.model

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * One reading of the gravity vector, in device coordinates and metres per second squared.
 *
 * **The framing that makes every sign in this module tractable:** Android's accelerometer and
 * gravity sensors report the *reaction* force, so a phone lying flat on its back reads
 * `(0, 0, +9.81)`. Normalised, this vector is therefore **world "up", expressed in device
 * coordinates** — and every angle the level computes is a dot or cross product with it. Reason about
 * it that way and the signs stop being guesswork.
 *
 * Device axes, with the phone held upright in its natural orientation: `+x` right, `+y` up the
 * screen, `+z` out of the screen towards the user.
 *
 * `Double`, not `Float`, even though `SensorEvent.values` is a `FloatArray` — the conversion happens
 * at the sensor boundary. Float32 has an ulp of 6e-8 just below 1.0, which puts a **0.02° floor** on
 * any angle derived near level. Against a ±0.2° tolerance that is a tenth of the whole budget, and
 * it shows up as the last digit ticking in steps for reasons nobody can explain.
 *
 * [timestampNanos] is `SensorEvent.timestamp`, whose base is unspecified — usually
 * `elapsedRealtimeNanos`, but OEMs have shipped others. It is used only for differences, never
 * compared against a wall clock.
 */
data class GravitySample(
    /** The device `+x` (right) component, in m/s². */
    val x: Double,
    /** The device `+y` (up the screen) component, in m/s². */
    val y: Double,
    /** The device `+z` (out of the screen) component, in m/s². */
    val z: Double,
    /** `SensorEvent.timestamp`, used only for differences, never against a wall clock. */
    val timestampNanos: Long,
) {

    /** The vector's length in m/s² — near 9.81 for a device at rest. */
    val magnitude: Double get() = sqrt(x * x + y * y + z * z)

    /**
     * Whether this reading could plausibly be gravity at rest.
     *
     * Rejects freefall, a hard knock, and the occasional garbage sample a driver emits on
     * registration. Deliberately wide: this is a sanity gate, not a stillness test — that is
     * [com.minion.scaffold.core.level.usecase.DetectStabilityUseCase]'s job.
     */
    val isPlausible: Boolean get() = magnitude in MIN_PLAUSIBLE..MAX_PLAUSIBLE

    /**
     * Unit-length, or `null` when the vector has no direction to speak of.
     *
     * @return The normalised [UpVector], or `null` when the magnitude is zero.
     */
    fun normalizedOrNull(): UpVector? {
        val m = magnitude
        return if (m > 0.0) UpVector(x / m, y / m, z / m) else null
    }

    private companion object {
        const val MIN_PLAUSIBLE = 4.0
        const val MAX_PLAUSIBLE = 16.0
    }
}

/**
 * The direction of world "up" in device coordinates — [GravitySample] normalised.
 *
 * A separate type because almost every calculation in this module wants the direction and not the
 * magnitude, and because the unit-length invariant is worth stating once rather than re-deriving at
 * each call site.
 */
data class UpVector(
    /** The device `+x` (right) component, unit-length. */
    val x: Double,
    /** The device `+y` (up the screen) component, unit-length. */
    val y: Double,
    /** The device `+z` (out of the screen) component, unit-length. */
    val z: Double,
) {

    /**
     * How far this is from [other], as the angle between them in radians. Always non-negative.
     *
     * @param other The vector to measure against.
     * @return The angle between the two vectors in radians.
     */
    fun angleTo(other: UpVector): Double {
        val cx = y * other.z - z * other.y
        val cy = z * other.x - x * other.z
        val cz = x * other.y - y * other.x
        val cross = sqrt(cx * cx + cy * cy + cz * cz)
        val dot = x * other.x + y * other.y + z * other.z

        // atan2 of the cross and dot magnitudes rather than acos(dot): acos is ill-conditioned at
        // exactly the angle this feature cares most about — zero. See ComputeTiltUseCase.
        return kotlin.math.atan2(cross, dot)
    }

    /** How far the screen plane is from horizontal — the in-plane component against the normal. */
    internal val inPlaneMagnitude: Double get() = hypot(x, y)

    internal val absZ: Double get() = abs(z)

    /**
     * Rotated about the device's z-axis by [degrees].
     *
     * The sensor frame is the device's *natural* orientation, which is portrait on phones but
     * landscape on many tablets and foldables. The feature reads the display rotation once and
     * passes the offset in here, so the correction is a plain 2D rotation rather than a call to
     * `SensorManager.remapCoordinateSystem` — which is overkill for an in-plane turn and far harder
     * to test.
     *
     * @param degrees The in-plane rotation to apply, in degrees.
     * @return The rotated vector; the same instance when [degrees] is zero.
     */
    fun rotatedInPlane(degrees: Double): UpVector {
        if (degrees == 0.0) return this

        val radians = Math.toRadians(degrees)
        val cos = kotlin.math.cos(radians)
        val sin = kotlin.math.sin(radians)

        return UpVector(x = x * cos - y * sin, y = x * sin + y * cos, z = z)
    }
}
