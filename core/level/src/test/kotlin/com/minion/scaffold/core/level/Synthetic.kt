package com.minion.scaffold.core.level

import com.minion.scaffold.core.level.model.GravitySample
import com.minion.scaffold.core.level.model.UpVector
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds gravity vectors for known orientations, so the maths can be checked against an answer
 * rather than against itself.
 *
 * This is the whole reason `:core:level` exists as a pure module. A level has no visible ground
 * truth — a wrong angle looks exactly like a right one on a phone — so correctness has to be
 * established by synthesising a vector from a known orientation, running it through the pipeline,
 * and asserting the original orientation comes back out.
 *
 * Deliberately written as the *forward* rotation, independently of the code under test. If these
 * helpers were expressed in terms of the use cases they check, the tests would only prove the code
 * agrees with itself.
 */
internal object Synthetic {

    const val G = 9.80665

    /**
     * A phone lying face-up, tipped by [pitchDegrees] about its x-axis then [rollDegrees] about y.
     *
     * At zero this is `(0, 0, 1)` — flat on its back, gravity's reaction pointing out of the
     * screen.
     */
    fun up(pitchDegrees: Double = 0.0, rollDegrees: Double = 0.0): UpVector {
        val pitch = Math.toRadians(pitchDegrees)
        val roll = Math.toRadians(rollDegrees)

        // World up, expressed in device axes after rotating the device by pitch then roll.
        return UpVector(
            x = -sin(roll) * cos(pitch),
            y = sin(pitch),
            z = cos(roll) * cos(pitch),
        )
    }

    /**
     * A phone stood on its bottom edge against a surface that is [deviationDegrees] off plumb, held
     * with [leanDegrees] of out-of-plane tip.
     *
     * The lean is the thing that separates a correct edge formula from a plausible one, so it is a
     * first-class parameter here.
     */
    fun edgeUp(deviationDegrees: Double, leanDegrees: Double = 0.0): UpVector {
        val theta = Math.toRadians(deviationDegrees)
        val psi = Math.toRadians(leanDegrees)

        return UpVector(
            x = sin(theta) * cos(psi),
            y = cos(theta),
            z = sin(theta) * sin(psi),
        )
    }

    /** [up] as a sensor reading, scaled to real gravity. */
    fun sample(
        pitchDegrees: Double = 0.0,
        rollDegrees: Double = 0.0,
        timestampNanos: Long = 0L,
    ): GravitySample = up(pitchDegrees, rollDegrees).toSample(timestampNanos)

    fun UpVector.toSample(timestampNanos: Long): GravitySample =
        GravitySample(x = x * G, y = y * G, z = z * G, timestampNanos = timestampNanos)
}
