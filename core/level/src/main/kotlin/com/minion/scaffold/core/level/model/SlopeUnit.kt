package com.minion.scaffold.core.level.model

import kotlin.math.abs
import kotlin.math.tan

/**
 * How a slope is expressed.
 *
 * Degrees is what a level shows; percent grade is what drainage, driveways, ramps and accessibility
 * regulations are written in — a wheelchair ramp is specified as 8.33%, not as 4.76°.
 *
 * Stored by [name] rather than ordinal wherever it is persisted, for the reason recorded on every
 * other enum in this codebase: an ordinal silently remaps every stored preference the moment someone
 * reorders the entries, and nothing fails loudly when it does.
 */
enum class SlopeUnit {

    Degrees,

    /** `100 · tan(angle)`. Singular at 90°, so [convert] caps it — see [MAX_GRADE_DEGREES]. */
    PercentGrade,

    ;

    companion object {

        /**
         * Beyond this, percent grade is not worth showing.
         *
         * `tan` runs away to infinity at 90°, and a grade of "5729%" is not a number anyone acts
         * on. Past this the UI shows a dash instead — an honest "this is vertical" rather than a
         * large meaningless figure.
         */
        const val MAX_GRADE_DEGREES = 89.0
    }
}

/**
 * [degrees] expressed in [unit], or `null` where the conversion has no useful answer.
 *
 * Null only happens for a percent grade at or beyond [SlopeUnit.MAX_GRADE_DEGREES]; the caller shows
 * a dash. Returning null rather than `Double.POSITIVE_INFINITY` keeps the "there is no number here"
 * case impossible to format by accident.
 *
 * @param degrees The slope angle in degrees.
 * @param unit    The unit to express it in.
 * @return The value in [unit], or `null` for a percent grade at or beyond the vertical cap.
 */
fun convertSlope(degrees: Double, unit: SlopeUnit): Double? = when (unit) {
    SlopeUnit.Degrees -> degrees
    SlopeUnit.PercentGrade ->
        if (abs(degrees) >= SlopeUnit.MAX_GRADE_DEGREES) null
        else tan(Math.toRadians(degrees)) * 100.0
}
