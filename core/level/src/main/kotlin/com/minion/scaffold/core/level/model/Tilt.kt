package com.minion.scaffold.core.level.model

/**
 * What one gravity reading says about the surface the phone is resting on.
 *
 * Every angle is in **degrees**, because that is what the UI shows and what a user would say out
 * loud. The trigonometry that produces them works in radians and converts once, at the boundary.
 */
data class Tilt(

    /**
     * Elevation of the device's `+x` (short) axis above horizontal, −90°..+90°.
     *
     * Positive means the **right edge is physically higher**. Pinned by a test with a literal
     * vector, because the sign is a coin flip in review.
     */
    val tiltX: Double,

    /**
     * Elevation of the device's `+y` (long) axis above horizontal, −90°..+90°.
     *
     * Positive means the **top edge is physically higher**.
     */
    val tiltY: Double,

    /**
     * Total tilt of the screen plane away from horizontal, 0°..90°.
     *
     * **Not** `hypot(tiltX, tiltY)`. Those two are independent axis elevations, not components of a
     * vector, and they do not compose — the true identity is
     * `sin²(tiltX) + sin²(tiltY) + uz² = 1`. Combining them as a hypotenuse is right for small
     * angles and drifts to several degrees of error by 40°, which is exactly the kind of bug that
     * looks fine in testing on a nearly-level desk.
     */
    val inclination: Double,

    /**
     * Which way is downhill, as a bearing in the screen plane, −180°..180°.
     *
     * 0° means downhill is towards the device's `+x` edge (the right), 90° towards `+y` (the top).
     * A slope reading without a direction is half a measurement — it tells you the ramp is 3° but
     * not which end to shim — so this drives the arrow on the bullseye.
     *
     * Meaningless when [inclination] is near zero, where it is pure noise. The UI hides the arrow
     * rather than spinning it.
     */
    val downhillBearing: Double,

    /**
     * Deviation of the device's `+y` axis from vertical, 0°..180° — the edge/plumb reading.
     *
     * Reported separately from [inclination] because pressing a phone against a door frame, the
     * user always tips it out of plane a little, and the two candidate formulas disagree under
     * that. See [com.minion.scaffold.core.level.usecase.ComputeTiltUseCase].
     */
    val edgeDeviation: Double,

    /** [edgeDeviation] signed so positive means the top leans towards the device's right. */
    val signedEdgeDeviation: Double,

    /**
     * How far the phone is tipped out of the plane it is being held against, 0°..90°.
     *
     * [edgeDeviation] stays *correct* regardless of this, but the direction becomes less certain
     * and the user is probably not holding the phone the way they think. Above roughly 15° the UI
     * says so.
     */
    val outOfPlaneLean: Double,
) {

    companion object {
        val LEVEL = Tilt(0.0, 0.0, 0.0, 0.0, 90.0, 90.0, 0.0)
    }
}
