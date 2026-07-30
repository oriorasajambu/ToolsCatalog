package com.minion.scaffold.feature.qrscan.presentation.camera

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/** How much of the shorter viewport edge the reticle spans. */
private const val RETICLE_FRACTION = 0.7f

/**
 * The aiming square for a viewfinder of [size]: centred, and sized off the shorter edge so it stays
 * square in either orientation.
 *
 * Pure and separate from the drawing so the same rectangle feeds both the outline and [isAimed] —
 * a reticle drawn from one rectangle and tested against another is a box that rejects what it
 * visibly contains.
 */
internal fun reticleIn(size: IntSize): Rect {
    val side = min(size.width, size.height) * RETICLE_FRACTION
    val left = (size.width - side) / 2f
    val top = (size.height - side) / 2f
    return Rect(left = left, top = top, right = left + side, bottom = top + side)
}

/**
 * Whether a detected code counts as aimed at the reticle.
 *
 * Two accepting cases, not one. Strict containment alone traps the user: holding the phone closer
 * is the natural response to a code that will not scan, and the moment the code grows larger than
 * the reticle it can never be *inside* it — the box would sit red while the code fills the screen.
 * A code that engulfs the reticle is unambiguously the one being aimed at, so it counts too.
 *
 * Both rectangles are in the same space — view pixels. Mapping the detection into that space is
 * `CameraPreview`'s job and the part that needs a real camera; this is the part worth testing.
 *
 * `androidx.compose.ui.geometry.Rect`, not `android.graphics.Rect`: the framework class is a stub
 * in a JVM unit test and every method on it throws, which would make this function — the only
 * testable piece of the viewfinder — untestable.
 */
internal fun isAimed(code: Rect, reticle: Rect): Boolean =
    reticle.containsFully(code) || code.containsFully(reticle)

/**
 * True when [other] lies entirely within the receiver, edges included.
 *
 * Compose's `Rect` offers `overlaps` and `contains(Offset)` but no rectangle containment, so this
 * spells it out. An empty rectangle contains nothing and is contained by nothing — a zero-area
 * detection is a detection that has not really happened.
 */
private fun Rect.containsFully(other: Rect): Boolean = when {
    isEmpty || other.isEmpty -> false
    else -> left <= other.left &&
        top <= other.top &&
        right >= other.right &&
        bottom >= other.bottom
}
