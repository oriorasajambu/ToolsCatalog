package com.minion.scaffold.core.gnss.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * What the speed is shown in.
 *
 * Chosen independently of [DistanceUnit], because the real combinations do not line up: knots pairs
 * with metres at sea and with feet in aviation, but never with miles. One switch driving everything
 * would force knots into a metric-or-imperial box it does not fit in.
 *
 * The factors are exact by definition, not measured — a knot is exactly 1852 m/h and a mile is
 * exactly 1609.344 m — so the tests assert them to full precision rather than to a tolerance.
 */
enum class SpeedUnit(
    /** The factor that converts m/s into this unit. */
    val metersPerSecondToUnit: Double,
) {
    KilometersPerHour(3.6),
    MilesPerHour(3600.0 / 1609.344),
    Knots(3600.0 / 1852.0),
    ;

    /**
     * Converts a speed in m/s into this unit.
     *
     * @param metersPerSecond The speed in metres per second.
     * @return The same speed expressed in this unit.
     */
    fun fromMetersPerSecond(metersPerSecond: Double): Double =
        metersPerSecond * metersPerSecondToUnit
}

/** What altitude and distance are shown in. */
enum class DistanceUnit {
    Metric,
    Imperial,
    ;

    /**
     * Altitude, in metres or feet.
     *
     * @param meters The altitude in metres.
     * @return The altitude in this unit — metres for [Metric], feet for [Imperial].
     */
    fun altitudeFromMeters(meters: Double): Double = when (this) {
        Metric -> meters
        Imperial -> meters * FEET_PER_METER
    }

    /**
     * Trip distance, in kilometres or miles.
     *
     * Deliberately not the same conversion as altitude: nobody measures a journey in feet or a
     * mountain in miles, so the pair of them is two conversions rather than one scale factor.
     *
     * @param meters The distance in metres.
     * @return The distance in this unit — kilometres for [Metric], miles for [Imperial].
     */
    fun journeyFromMeters(meters: Double): Double = when (this) {
        Metric -> meters / METERS_PER_KILOMETER
        Imperial -> meters / METERS_PER_MILE
    }

    private companion object {
        /** Exact: an international foot is 0.3048 m by definition. */
        const val FEET_PER_METER = 1.0 / 0.3048
        const val METERS_PER_KILOMETER = 1000.0
        const val METERS_PER_MILE = 1609.344
    }
}

/** How a coordinate pair is written. */
enum class CoordinateFormat {
    /** `3.595200°, 98.672200°` — what pastes into anything else. */
    Decimal,

    /** `3°35'42.7"N 98°40'19.9"E` — what appears on maps and in aviation. */
    DegreesMinutesSeconds,
}

/**
 * Formats a position for display.
 *
 * Pure string work, and worth testing carefully for one reason: **the hemisphere.** Decimal degrees
 * carry the sign, DMS carries a letter, and converting between them is where a southern latitude
 * quietly becomes a northern one. The EXIF tool met the same class of bug from the other direction,
 * where GPS magnitudes are stored unsigned with a separate reference byte.
 */
object CoordinateFormatter {

    /**
     * Formats a latitude/longitude pair.
     *
     * @param latitude  Latitude in decimal degrees.
     * @param longitude Longitude in decimal degrees.
     * @param format    The output format.
     * @return The formatted coordinate string.
     */
    fun format(latitude: Double, longitude: Double, format: CoordinateFormat): String =
        when (format) {
            CoordinateFormat.Decimal ->
                "%.6f°, %.6f°".format(latitude, longitude)

            CoordinateFormat.DegreesMinutesSeconds ->
                "${dms(latitude, "N", "S")} ${dms(longitude, "E", "W")}"
        }

    private fun dms(value: Double, positive: String, negative: String): String {
        val hemisphere = if (value < 0) negative else positive
        val magnitude = abs(value)

        val degrees = floor(magnitude).toInt()
        val minutesTotal = (magnitude - degrees) * MINUTES_PER_DEGREE
        val minutes = floor(minutesTotal).toInt()
        val seconds = (minutesTotal - minutes) * SECONDS_PER_MINUTE

        // Rounding the seconds can carry into the minutes and then into the degrees — 59.96" becomes
        // 60.0", which is not a time anyone writes. Carrying it properly is three lines; not carrying
        // it produces 3°35'60.0" on roughly one position in nine hundred.
        val roundedSeconds = (seconds * SECONDS_PRECISION).roundToInt() / SECONDS_PRECISION
        var finalSeconds = roundedSeconds
        var finalMinutes = minutes
        var finalDegrees = degrees

        if (finalSeconds >= SECONDS_PER_MINUTE) {
            finalSeconds -= SECONDS_PER_MINUTE
            finalMinutes += 1
        }
        if (finalMinutes >= MINUTES_PER_DEGREE.toInt()) {
            finalMinutes -= MINUTES_PER_DEGREE.toInt()
            finalDegrees += 1
        }

        return "%d°%02d'%04.1f\"%s".format(finalDegrees, finalMinutes, finalSeconds, hemisphere)
    }

    private const val MINUTES_PER_DEGREE = 60.0
    private const val SECONDS_PER_MINUTE = 60.0

    /** One decimal place on the seconds — about 3 m, which is finer than any consumer fix. */
    private const val SECONDS_PRECISION = 10.0
}
