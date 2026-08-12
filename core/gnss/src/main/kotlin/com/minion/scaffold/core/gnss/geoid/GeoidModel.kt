package com.minion.scaffold.core.gnss.geoid

import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/**
 * Turns a satellite's ellipsoidal height into a height above mean sea level.
 *
 * ## The problem this exists to solve
 *
 * A GNSS receiver reports height above the **WGS-84 ellipsoid** — a smooth mathematical figure fitted
 * to the planet. "Altitude above sea level" means height above the **geoid**, the surface the oceans
 * would settle into under gravity alone. The two disagree by between about −107 m and +85 m depending
 * where you stand, and the difference is not a constant, a bias or noise: it is a fixed property of
 * the location.
 *
 * Most GPS altimeter apps display the raw figure and label it "above sea level". That is wrong by
 * tens of metres almost everywhere, silently, and it is the single reason this module exists.
 *
 * ## Why a shipped table rather than the platform
 *
 * `Location.getMslAltitudeMeters()` does the same job with a system geoid, but arrived in API 34
 * against this app's minSdk of 29. Using it would mean roughly half the install base seeing true mean
 * sea level and the rest seeing ellipsoidal height under the same label — one number meaning two
 * different things depending on the phone, which is the kind of inconsistency nobody ever traces back
 * to its cause.
 *
 * A table also keeps the promise in the feature's name: it works with no network, on every supported
 * device, identically.
 *
 * @see scripts/generate_geoid.py for the data's provenance, licence, and why it is half a degree.
 */
@Singleton
class GeoidModel @Inject constructor() {

    /**
     * Loaded once, on first use, and kept.
     *
     * Half a megabyte of `ShortArray` held for the life of the process, which is the right trade for
     * something consulted on every position fix. Lazy rather than eager so that a build which never
     * opens the speedometer never pays for it.
     */
    private val grid: GeoidGrid? by lazy { load() }

    /**
     * The geoid separation at a position, in metres, or `null` if the table could not be read.
     *
     * Positive where the geoid is above the ellipsoid. Subtract it from an ellipsoidal height to get
     * a height above mean sea level — the direction matters, and getting it backwards produces an
     * entirely plausible number wrong by twice the separation.
     *
     * @param latitude  Latitude in decimal degrees.
     * @param longitude Longitude in decimal degrees.
     * @return The geoid separation in metres, or `null` when the table could not be read.
     */
    fun separationMeters(latitude: Double, longitude: Double): Double? {
        val grid = grid ?: return null
        return grid.interpolate(latitude, longitude)
    }

    /**
     * Height above mean sea level, from a height above the WGS-84 ellipsoid.
     *
     * `null` when the table is unavailable, so a caller has to decide what to show rather than being
     * handed an uncorrected figure that looks like a corrected one.
     *
     * @param ellipsoidalAltitudeMeters Height above the WGS-84 ellipsoid, from the receiver.
     * @param latitude                  Latitude in decimal degrees.
     * @param longitude                 Longitude in decimal degrees.
     * @return Height above mean sea level in metres, or `null` when the table is unavailable.
     */
    fun mslAltitudeMeters(
        ellipsoidalAltitudeMeters: Double,
        latitude: Double,
        longitude: Double,
    ): Double? = separationMeters(latitude, longitude)
        ?.let { ellipsoidalAltitudeMeters - it }

    private fun load(): GeoidGrid? = try {
        javaClass.getResourceAsStream(RESOURCE_PATH)?.use { stream ->
            DataInputStream(stream.buffered()).use(::read)
        }
    } catch (_: IOException) {
        // A missing or truncated resource means altitude is reported as unavailable rather than
        // uncorrected. Showing an ellipsoidal height under a "sea level" label is the failure this
        // whole class exists to prevent, so degrading to it would be worse than degrading to nothing.
        null
    }

    private fun read(input: DataInputStream): GeoidGrid? {
        val magic = ByteArray(MAGIC.size)
        input.readFully(magic)
        if (!magic.contentEquals(MAGIC)) return null
        if (input.readUnsignedByte() != FORMAT_VERSION) return null

        val rows = input.readUnsignedShort()
        val columns = input.readUnsignedShort()
        if (rows < 2 || columns < 2) return null

        val bytes = ByteArray(rows * columns * Short.SIZE_BYTES)
        input.readFully(bytes)

        val centimetres = ShortArray(rows * columns)
        ByteBuffer.wrap(bytes).asShortBuffer().get(centimetres)

        return GeoidGrid(rows, columns, centimetres)
    }

    private companion object {
        const val RESOURCE_PATH = "/egm96_geoid.bin"
        val MAGIC = "GEOD".toByteArray(Charsets.US_ASCII)
        const val FORMAT_VERSION = 1
    }
}

/**
 * The table itself: separations in centimetres, north-to-south then west-to-east.
 *
 * Row 0 is the north pole and column 0 is the antimeridian going east. The generator duplicates
 * longitude +180 as a final column, which costs 722 bytes and removes modulo arithmetic from the
 * lookup below — the one place a table like this otherwise fails, on exactly one meridian, in a way
 * nobody notices until someone sails across it.
 */
internal class GeoidGrid(
    private val rows: Int,
    private val columns: Int,
    private val centimetres: ShortArray,
) {

    private val latitudeStep = LATITUDE_SPAN / (rows - 1)
    private val longitudeStep = LONGITUDE_SPAN / (columns - 1)

    /**
     * Bilinear interpolation between the four surrounding posts.
     *
     * The geoid is a long-wavelength field, so linear interpolation between posts half a degree apart
     * costs about 0.1 m RMS against the source grid — two orders of magnitude below the ±10–30 m that
     * GNSS vertical accuracy contributes.
     *
     * @param latitude  Latitude in decimal degrees; clamped to the grid's range.
     * @param longitude Longitude in decimal degrees; wrapped into `[-180, 180)`.
     * @return The interpolated geoid separation in metres.
     */
    fun interpolate(latitude: Double, longitude: Double): Double {
        val clampedLatitude = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val normalisedLongitude = normaliseLongitude(longitude)

        val row = (MAX_LATITUDE - clampedLatitude) / latitudeStep
        val column = (normalisedLongitude + MAX_LONGITUDE) / longitudeStep

        // Clamped to the second-to-last post so that r0 + 1 and c0 + 1 are always in range. At the
        // exact south pole or the exact antimeridian this makes the fraction 1.0 rather than 0.0 on
        // the next cell, which lands on the same value either way.
        val row0 = floor(row).toInt().coerceIn(0, rows - 2)
        val column0 = floor(column).toInt().coerceIn(0, columns - 2)
        val rowFraction = (row - row0).coerceIn(0.0, 1.0)
        val columnFraction = (column - column0).coerceIn(0.0, 1.0)

        val topLeft = at(row0, column0)
        val topRight = at(row0, column0 + 1)
        val bottomLeft = at(row0 + 1, column0)
        val bottomRight = at(row0 + 1, column0 + 1)

        val top = topLeft + (topRight - topLeft) * columnFraction
        val bottom = bottomLeft + (bottomRight - bottomLeft) * columnFraction

        return top + (bottom - top) * rowFraction
    }

    private fun at(row: Int, column: Int): Double =
        centimetres[row * columns + column] / CENTIMETRES_PER_METRE

    /**
     * Folds any longitude into `[-180, 180)`.
     *
     * Kotlin's `%` keeps the sign of the dividend, so the extra `+ 360` is what makes a western
     * longitude land in range rather than negative — the classic way this arithmetic goes wrong for
     * exactly half the planet.
     */
    private fun normaliseLongitude(longitude: Double): Double {
        val wrapped = (longitude + MAX_LONGITUDE) % LONGITUDE_SPAN
        val positive = if (wrapped < 0) wrapped + LONGITUDE_SPAN else wrapped
        return positive - MAX_LONGITUDE
    }

    private companion object {
        const val MAX_LATITUDE = 90.0
        const val MAX_LONGITUDE = 180.0
        const val LATITUDE_SPAN = 180.0
        const val LONGITUDE_SPAN = 360.0
        const val CENTIMETRES_PER_METRE = 100.0
    }
}
