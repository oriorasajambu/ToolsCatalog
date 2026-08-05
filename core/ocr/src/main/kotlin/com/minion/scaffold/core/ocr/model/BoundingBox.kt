package com.minion.scaffold.core.ocr.model

/**
 * Where a piece of recognised text sits in the image, in source-image pixels.
 *
 * Deliberately not `android.graphics.Rect`, and not `androidx.compose.ui.geometry.Rect` either.
 * The framework class is a throwing stub in JVM unit tests — `:feature:qrscan`'s `AimTest.kt`
 * carries the same note — and the Compose one would drag a UI dependency into a module that is
 * pure geometry. `:feature:ocr` maps ML Kit's rects into this on the way in.
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {

    val width: Int get() = right - left

    val height: Int get() = bottom - top

    /**
     * Whether these two boxes sit on the same visual line.
     *
     * Measured as vertical overlap against the *shorter* of the two, not against either one
     * specifically: a tall block and a short one on the same line (a heading beside a page number,
     * an item name beside its price) overlap almost completely from the short box's point of view
     * and barely at all from the tall one's. Taking the shorter makes the test symmetric and
     * matches what the eye does.
     */
    fun sharesRowWith(other: BoundingBox): Boolean {
        val overlap = minOf(bottom, other.bottom) - maxOf(top, other.top)
        if (overlap <= 0) return false

        val shorter = minOf(height, other.height)
        return shorter > 0 && overlap.toFloat() / shorter >= ROW_OVERLAP_FRACTION
    }

    /**
     * How much these two boxes overlap horizontally, as a fraction of the narrower one.
     *
     * The horizontal counterpart to [sharesRowWith], and narrower-relative for the same reason:
     * a short line inside a wide paragraph belongs to it, even though it covers little of the
     * paragraph's width. Returns `0` when they do not overlap at all.
     */
    fun horizontalOverlapWith(other: BoundingBox): Float {
        val overlap = minOf(right, other.right) - maxOf(left, other.left)
        if (overlap <= 0) return 0f

        val narrower = minOf(width, other.width)
        return if (narrower > 0) overlap.toFloat() / narrower else 0f
    }

    /** The smallest box containing both. */
    fun union(other: BoundingBox): BoundingBox = BoundingBox(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

    private companion object {

        /**
         * How much of the shorter box must be vertically covered to count as the same row.
         *
         * Half is deliberately forgiving. Text photographed at a slight angle drifts, and a
         * stricter threshold splits one printed line into two output lines — which reads worse
         * than the occasional over-merge this allows.
         */
        const val ROW_OVERLAP_FRACTION = 0.5f
    }
}
