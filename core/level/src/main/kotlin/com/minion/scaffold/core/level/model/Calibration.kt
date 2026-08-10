package com.minion.scaffold.core.level.model

import kotlin.math.sqrt

/**
 * The device's own tilt bias, as a rotation.
 *
 * ## Why a rotation and not a pair of angle offsets
 *
 * The tempting representation is "subtract 0.3° from pitch and 0.1° from roll". That is only
 * equivalent to the truth near flat. The physical error — the sensor die sitting slightly askew in
 * the case, plus a camera bump or a case lip changing how the phone rests — **is a rotation**, and
 * applying it as a constant angle pair means applying it to a quantity it does not correspond to as
 * soon as the phone leaves the flat pose. Correcting the gravity vector by a rotation is
 * geometrically valid in every pose.
 *
 * Stored as a **rotation vector** (an axis scaled by the angle, in radians) rather than a
 * quaternion: three doubles serialise trivially, they average if multi-sample calibration is ever
 * added, [angleRadians] falls straight out as the displayable "your device is 0.4° off", and there
 * is no unit-length invariant to be silently broken by a value read back from disk.
 *
 * ## What a single flip can and cannot see
 *
 * A 180° flip about the vertical observes the two bias components *perpendicular to the current up
 * direction*. Flat calibration therefore measures `{x, y}` and is blind to `z`; an edge/plumb
 * reading depends on `{x, z}`. So a flat calibration corrects flat mode fully and edge mode only
 * partly — it is never *wrong* there, just incomplete. [measuredMask] records which components were
 * actually observed so the UI can say so, and so adding an edge calibration later is a feature
 * rather than a data migration.
 */
data class Calibration(
    val x: Double,
    val y: Double,
    val z: Double,

    /** Bitmask of which components were measured. See [MASK_X], [MASK_Y], [MASK_Z]. */
    val measuredMask: Int,

    /** When it was taken, epoch millis — so the UI can show it and prompt a refresh. */
    val takenAtMillis: Long,

    /**
     * How far off level the calibration surface itself was, in degrees.
     *
     * Falls out of the flip for free, and is worth showing: it confirms the procedure worked, and
     * a large value means the user calibrated on something sloped.
     */
    val surfaceTiltDegrees: Double,

    /** Schema version, so a future shape change can be migrated rather than guessed at. */
    val version: Int = CURRENT_VERSION,
) {

    val angleRadians: Double get() = sqrt(x * x + y * y + z * z)

    val angleDegrees: Double get() = Math.toDegrees(angleRadians)

    /** Whether this covers everything an edge/plumb reading needs. False for a flat-only flip. */
    val correctsEdgePose: Boolean get() = measuredMask and MASK_Z != 0

    companion object {

        const val MASK_X = 1
        const val MASK_Y = 2
        const val MASK_Z = 4

        const val CURRENT_VERSION = 1

        /** No correction — what an uncalibrated device uses. */
        val NONE = Calibration(
            x = 0.0,
            y = 0.0,
            z = 0.0,
            measuredMask = 0,
            takenAtMillis = 0L,
            surfaceTiltDegrees = 0.0,
        )
    }
}
