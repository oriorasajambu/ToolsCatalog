package com.minion.scaffold.core.level.model

/**
 * How the phone is being held, which decides what the screen shows.
 *
 * A phone lying on a worktop wants a two-axis bubble; a phone held against a door frame wants a
 * single plumb angle. The same reading means different things in each, so the pose is part of the
 * contract rather than a UI detail.
 */
sealed interface LevelPose {

    /** Screen up — a two-axis bubble reading of the surface underneath. */
    data object Flat : LevelPose

    /**
     * Screen down.
     *
     * A real measuring pose, not a mirrored [Flat] — and worth distinguishing because a phone rests
     * on its *back*, which on most handsets is not planar thanks to the camera bump. Face-down and
     * face-up therefore have genuinely different biases.
     */
    data object FaceDown : LevelPose

    /** Standing on an edge — a single plumb/slope angle, read along the long axis. */
    data class Edge(val quadrant: EdgeQuadrant) : LevelPose

    /**
     * Between poses, or not yet settled into one.
     *
     * Exists so the UI can decline to commit rather than snapping between layouts while the phone
     * is mid-rotation.
     */
    data object Transitional : LevelPose
}

/**
 * Which edge of the device is pointing down.
 *
 * Named from the device's own axes rather than the world's, so the meaning survives the phone being
 * turned over.
 */
enum class EdgeQuadrant {

    /** The bottom edge (`-y`) is down — the phone stood upright as you would normally hold it. */
    Bottom,

    /** The top edge (`+y`) is down — upside down. */
    Top,

    /** The left edge (`-x`) is down — landscape, rotated anticlockwise. */
    Left,

    /** The right edge (`+x`) is down — landscape, rotated clockwise. */
    Right,
}

/**
 * The pose machine's accumulated state.
 *
 * Carried by the caller and threaded back in, rather than held in the use case: that is what keeps
 * `:core:level` free of mutable state and makes "does not oscillate" testable by folding a list of
 * samples.
 */
data class PoseState(
    val pose: LevelPose = LevelPose.Transitional,

    /** The pose being considered, waiting out the dwell before it is committed. */
    val candidate: LevelPose = LevelPose.Transitional,

    /** When [candidate] was first seen. Compared only against other sensor timestamps. */
    val candidateSinceNanos: Long = 0L,

    /**
     * The last quadrant that was determined from a meaningful in-plane vector.
     *
     * Held across flat periods, where the in-plane direction is pure noise — see
     * [com.minion.scaffold.core.level.usecase.ResolvePoseUseCase].
     */
    val quadrant: EdgeQuadrant = EdgeQuadrant.Bottom,
)
