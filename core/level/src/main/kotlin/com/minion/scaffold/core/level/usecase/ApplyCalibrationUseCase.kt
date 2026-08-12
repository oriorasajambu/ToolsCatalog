package com.minion.scaffold.core.level.usecase

import com.minion.scaffold.core.level.model.Calibration
import com.minion.scaffold.core.level.model.UpVector
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

/**
 * Removes the device's own bias from a reading.
 *
 * Applied at the very top of the pipeline, before any angle exists, so that flat mode, edge mode,
 * relative mode and the bubble are all corrected by the same one thing in the same one place. A
 * correction applied per-readout would have to be applied four times and would drift apart.
 *
 * Rodrigues' rotation formula, by the negative of the stored bias — the stored rotation is the error
 * the device introduces, so undoing it means rotating the other way.
 */
class ApplyCalibrationUseCase @Inject constructor() {

    /**
     * Corrects [up] by removing the stored device bias.
     *
     * @param up          The raw up-vector to correct.
     * @param calibration The device bias to remove; [Calibration.NONE] is a no-op.
     * @return The corrected up-vector, or [up] unchanged when the bias is negligible.
     */
    operator fun invoke(up: UpVector, calibration: Calibration): UpVector {
        val angle = calibration.angleRadians
        if (angle < MIN_ANGLE_RADIANS) return up

        // Negated: we are undoing the bias, not reproducing it.
        val axisX = -calibration.x / angle
        val axisY = -calibration.y / angle
        val axisZ = -calibration.z / angle

        val cos = cos(angle)
        val sin = sin(angle)

        // v·cosθ + (k × v)·sinθ + k(k·v)(1 − cosθ)
        val crossX = axisY * up.z - axisZ * up.y
        val crossY = axisZ * up.x - axisX * up.z
        val crossZ = axisX * up.y - axisY * up.x

        val dot = axisX * up.x + axisY * up.y + axisZ * up.z
        val complement = 1.0 - cos

        return UpVector(
            x = up.x * cos + crossX * sin + axisX * dot * complement,
            y = up.y * cos + crossY * sin + axisY * dot * complement,
            z = up.z * cos + crossZ * sin + axisZ * dot * complement,
        )
    }

    private companion object {
        /** Below this the rotation is a no-op and the axis is not well defined. */
        const val MIN_ANGLE_RADIANS = 1e-12
    }
}
