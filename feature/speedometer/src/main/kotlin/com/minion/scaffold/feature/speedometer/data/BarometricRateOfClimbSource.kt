package com.minion.scaffold.feature.speedometer.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.minion.scaffold.feature.speedometer.domain.RateOfClimbSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

/**
 * Rate of climb, from the pressure sensor.
 *
 * ## Why this is a *rate* and not an altitude
 *
 * A barometer is precise to tenths of a metre for a **change** in height and has no idea of its
 * absolute reference. That reference is the sea-level pressure, which moves about 8–10 m worth as a
 * weather front passes — so a barometric altitude drifts by tens of metres over an afternoon while
 * the device sits still. GNSS is the mirror image: absolutely anchored, vertically noisy.
 *
 * The tempting move is to fuse them, taking the reference from a good fix and letting the barometer
 * carry the altitude between fixes. It works, and it produces one number whose accuracy depends on
 * when it was last anchored — which is invisible on screen and impossible to explain in a caption.
 *
 * Keeping them separate means neither is quietly wrong. The satellites give the height above sea
 * level; the barometer gives how fast it is changing, which is the part a barometer is actually good
 * at and the part a hiker or a pilot wants.
 *
 * ## The conversion
 *
 * The barometric formula, differentiated over a short interval. Only the *ratio* of pressures
 * matters, so the sea-level reference cancels out entirely — which is exactly why a rate needs no
 * calibration while an absolute altitude does.
 */
@Singleton
internal class BarometricRateOfClimbSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RateOfClimbSource {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val pressureSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val isAvailable: Boolean get() = pressureSensor != null

    override fun ratePerMinute(): Flow<Double> = callbackFlow {
        val manager = sensorManager
        val sensor = pressureSensor

        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }

        // Smoothed heavily. A raw pressure reading is noisy at the resolution that matters here, and
        // an unfiltered rate of climb reads +/-50 m/min on a stationary device — the same class of
        // problem as an unfiltered speed, and the same answer.
        var smoothedPressure = Double.NaN
        var lastAltitude = Double.NaN
        var lastTimestampNanos = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val hectopascals = event.values[0].toDouble()

                smoothedPressure = if (smoothedPressure.isNaN()) {
                    hectopascals
                } else {
                    smoothedPressure + SMOOTHING * (hectopascals - smoothedPressure)
                }

                val altitude = altitudeFromPressure(smoothedPressure)

                if (!lastAltitude.isNaN() && lastTimestampNanos != 0L) {
                    val elapsedMinutes =
                        (event.timestamp - lastTimestampNanos) / NANOS_PER_MINUTE
                    if (elapsedMinutes >= MIN_INTERVAL_MINUTES) {
                        trySend((altitude - lastAltitude) / elapsedMinutes)
                        lastAltitude = altitude
                        lastTimestampNanos = event.timestamp
                    }
                } else {
                    lastAltitude = altitude
                    lastTimestampNanos = event.timestamp
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = manager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )

        if (!registered) {
            close()
            return@callbackFlow
        }

        awaitClose { manager.unregisterListener(listener) }
    }
        .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * The international barometric formula.
     *
     * The absolute value is meaningless without a real sea-level reference — [STANDARD_PRESSURE] is
     * just a fixed constant, so on a stormy day this is tens of metres out. That does not matter: it
     * is only ever differenced, and a constant offset vanishes in a difference.
     */
    private fun altitudeFromPressure(hectopascals: Double): Double =
        SCALE_HEIGHT_METERS * ln(STANDARD_PRESSURE / hectopascals)

    private companion object {
        /** ICAO standard atmosphere at sea level, hPa. Cancels out; see the note above. */
        const val STANDARD_PRESSURE = 1013.25

        /** RT/Mg for the standard atmosphere — about 8434 m per e-fold of pressure. */
        const val SCALE_HEIGHT_METERS = 8434.5

        /** Heavy smoothing: a rate of climb that jitters is worse than none. */
        const val SMOOTHING = 0.05

        const val NANOS_PER_MINUTE = 60_000_000_000.0

        /** Roughly three seconds. Shorter intervals divide noise by a small number. */
        const val MIN_INTERVAL_MINUTES = 0.05
    }
}
