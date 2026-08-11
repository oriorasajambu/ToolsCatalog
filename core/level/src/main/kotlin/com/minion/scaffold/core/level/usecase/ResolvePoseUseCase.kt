package com.minion.scaffold.core.level.usecase

import com.minion.scaffold.core.level.model.EdgeQuadrant
import com.minion.scaffold.core.level.model.LevelPose
import com.minion.scaffold.core.level.model.PoseState
import com.minion.scaffold.core.level.model.UpVector
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Decides whether the phone is lying flat or standing on an edge, without flickering.
 *
 * A pure function of `(previous state, reading)` — nothing is held here and no clock is read, which
 * is what lets "this does not oscillate" be a real test: fold a list of samples and count how many
 * times the answer changed.
 *
 * ## Two mechanisms, because one is not enough
 *
 * A plain threshold at 45° chatters when the phone sits near it. A Schmitt trigger — enter flat
 * above 30°, leave it below 60°, keep the previous answer in between — fixes the *static* case. It
 * does not fix the *dynamic* one: swing a phone quickly through the band and a single sample landing
 * on the far side flips the pose and back again. So there is also a **dwell**: a new pose has to
 * hold for [DWELL_NANOS] before it is committed.
 *
 * ## Comparing on `|uz|` rather than an angle
 *
 * `|uz|` is monotone in the tilt, so a threshold on it *is* a threshold on the angle — with no
 * trigonometry and none of the conditioning problems that come with it.
 */
class ResolvePoseUseCase @Inject constructor() {

    /**
     * Folds one reading into the pose machine.
     *
     * @param previous       The accumulated pose state from the previous call.
     * @param up             The current up-vector.
     * @param timestampNanos The current sample's timestamp in nanoseconds, for the dwell.
     * @return The updated [PoseState], committing a new pose only once it has held for the dwell.
     */
    operator fun invoke(previous: PoseState, up: UpVector, timestampNanos: Long): PoseState {
        val quadrant = resolveQuadrant(previous.quadrant, up)
        val observed = observedPose(previous.pose, up, quadrant)

        // A new candidate restarts the dwell; the same one keeps its clock running.
        val candidateSince =
            if (observed == previous.candidate) previous.candidateSinceNanos else timestampNanos

        val settled = timestampNanos - candidateSince >= DWELL_NANOS
        val pose = if (settled) observed else previous.pose

        return PoseState(
            pose = pose,
            candidate = observed,
            candidateSinceNanos = candidateSince,
            quadrant = quadrant,
        )
    }

    /**
     * The pose this reading suggests, before the dwell has its say.
     *
     * The in-between band returns the previous pose rather than [LevelPose.Transitional] — the
     * hysteresis is the point, and reporting Transitional there would make the band itself a
     * visible state the user passes through.
     */
    private fun observedPose(
        previous: LevelPose,
        up: UpVector,
        quadrant: EdgeQuadrant,
    ): LevelPose {
        val flatness = up.absZ

        return when {
            flatness > FLAT_ENTER -> if (up.z > 0) LevelPose.Flat else LevelPose.FaceDown
            flatness < EDGE_ENTER -> LevelPose.Edge(quadrant)

            // In the band: hold whatever we had, but let an Edge pose track its quadrant.
            previous is LevelPose.Edge -> LevelPose.Edge(quadrant)
            else -> previous
        }
    }

    /**
     * Which edge is down, with hysteresis so it does not flicker at 45° of roll.
     *
     * Two non-obvious parts.
     *
     * **Circular hysteresis, not `argmax(|ux|, |uy|)`.** Picking the larger component flips
     * whenever the two are equal, which is precisely what happens when the phone is held diagonally.
     * Instead the in-plane bearing is compared against the *current* quadrant's centre and only
     * snaps away once it is more than 45° + [QUADRANT_HYSTERESIS] from it. One parameter, and the
     * ±180° seam takes care of itself because the comparison is on a wrapped difference.
     *
     * **The magnitude gate matters more than it looks.** When the phone is flat, `ux` and `uy` are
     * both near zero and their bearing is pure sensor noise — so without the gate the quadrant spins
     * randomly the entire time the phone is lying on a table, and the first frame after tipping it
     * up inherits whatever garbage it happened to land on. Holding the last meaningful quadrant
     * instead means the transition into edge mode starts from the right answer.
     */
    private fun resolveQuadrant(previous: EdgeQuadrant, up: UpVector): EdgeQuadrant {
        if (up.inPlaneMagnitude < QUADRANT_GATE) return previous

        // Bearing of "up" in the screen plane, measured from +y so that 0° is the bottom edge
        // being down — the natural upright pose.
        val bearing = Math.toDegrees(atan2(up.x, up.y))
        val previousCentre = previous.centreDegrees
        val offset = wrapDegrees(bearing - previousCentre)

        if (abs(offset) <= QUADRANT_HALF_WIDTH + QUADRANT_HYSTERESIS) return previous

        val snapped = (wrapDegrees(bearing) / 90.0).roundToInt().mod(4)
        return EdgeQuadrant.entries.first { it.index == snapped }
    }

    /** Wraps to (−180, 180], so the comparison above is safe across the seam. */
    private fun wrapDegrees(degrees: Double): Double {
        var wrapped = degrees % 360.0
        if (wrapped > 180.0) wrapped -= 360.0
        if (wrapped <= -180.0) wrapped += 360.0
        return wrapped
    }

    private val EdgeQuadrant.index: Int
        get() = when (this) {
            EdgeQuadrant.Bottom -> 0
            EdgeQuadrant.Right -> 1
            EdgeQuadrant.Top -> 2
            EdgeQuadrant.Left -> 3
        }

    private val EdgeQuadrant.centreDegrees: Double
        get() = when (this) {
            EdgeQuadrant.Bottom -> 0.0
            EdgeQuadrant.Right -> 90.0
            EdgeQuadrant.Top -> 180.0
            EdgeQuadrant.Left -> -90.0
        }

    companion object {

        /** Above this much of gravity along z, the phone is lying down. `cos(30°)`. */
        const val FLAT_ENTER = 0.866

        /** Below this, it is standing on an edge. `cos(60°)`. */
        const val EDGE_ENTER = 0.5

        /**
         * How long a new pose must persist before it is shown.
         *
         * 150ms is long enough that a fast rotation through the band does not commit a pose on the
         * way past, and short enough that a deliberate change feels immediate.
         */
        const val DWELL_NANOS = 150_000_000L

        /** Half of a quadrant. */
        const val QUADRANT_HALF_WIDTH = 45.0

        /** Extra angle a bearing must travel past a quadrant boundary before the quadrant changes. */
        const val QUADRANT_HYSTERESIS = 8.0

        /** Below this in-plane magnitude the bearing is noise and the quadrant is not updated. */
        const val QUADRANT_GATE = 0.3
    }
}
