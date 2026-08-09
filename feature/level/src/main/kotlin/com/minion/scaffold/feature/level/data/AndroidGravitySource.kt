package com.minion.scaffold.feature.level.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.minion.scaffold.core.level.model.GravitySample
import com.minion.scaffold.feature.level.domain.GravitySensor
import com.minion.scaffold.feature.level.domain.GravitySource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `SensorManager` bridge — this repo's first `callbackFlow`, so the shape here is a precedent.
 *
 * `TYPE_GRAVITY` where it exists, raw `TYPE_ACCELEROMETER` where it does not.
 * **`TYPE_ROTATION_VECTOR` is deliberately not used**, even though it would give a cleaner
 * orientation: it fuses in the magnetometer, so a level used near steel studs, rebar or a toolbox
 * would read wrong for reasons entirely invisible to the person holding it. A level has no visible
 * ground truth, so a silent error is the worst kind of bug this feature can have.
 *
 * The flow is cold. Registration happens on collection and unregistration in [awaitClose], so a
 * screen that is not being looked at is not powering a sensor.
 */
@Singleton
internal class AndroidGravitySource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : GravitySource {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val gravitySensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override val sensor: GravitySensor = when (gravitySensor?.type) {
        Sensor.TYPE_GRAVITY -> GravitySensor.Fused
        Sensor.TYPE_ACCELEROMETER -> GravitySensor.Accelerometer
        else -> GravitySensor.Unavailable
    }

    override fun samples(): Flow<GravitySample> = callbackFlow {
        val manager = sensorManager
        val target = gravitySensor

        if (manager == null || target == null) {
            close()
            return@callbackFlow
        }

        // Registration time, so the settling window below is measured from the right moment.
        var firstTimestampNanos = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (firstTimestampNanos == 0L) firstTimestampNanos = event.timestamp

                // The fusion has a start-up transient, and the very first event is sometimes a
                // stale value cached from a previous registration. Neither is a reading of
                // anything, so both are dropped rather than filtered.
                if (event.timestamp - firstTimestampNanos < SETTLE_NANOS) return

                trySend(
                    GravitySample(
                        // Widened to Double here, at the boundary, and never narrowed again.
                        // Float32 has an ulp of 6e-8 near 1.0, which puts a 0.02 degree floor on
                        // anything derived close to level — half the tolerance budget, for free.
                        x = event.values[0].toDouble(),
                        y = event.values[1].toDouble(),
                        z = event.values[2].toDouble(),
                        timestampNanos = event.timestamp,
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        // The return value is checked rather than assumed: getDefaultSensor can hand back a sensor
        // the device then declines to register, and a null check alone would leave the screen
        // waiting forever for samples that never arrive.
        val registered = manager.registerListener(
            listener,
            target,
            SAMPLING_PERIOD_MICROS,
            // Zero explicitly. A non-zero latency lets the hardware batch and deliver in bursts,
            // which is fatal for a live readout — and the default is not guaranteed to be zero.
            0,
        )

        if (!registered) {
            close()
            return@callbackFlow
        }

        awaitClose { manager.unregisterListener(listener) }
    }
        // Conflated, so a slow collector can never back-pressure the sensor thread. The newest
        // reading is the only one worth having: an old angle is not a measurement of anything.
        .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .filter { it.isPlausible }

    private companion object {

        /**
         * Roughly `SENSOR_DELAY_GAME`, in microseconds.
         *
         * Only ever a hint — devices deliver anywhere from 30Hz to 200Hz whatever is asked for,
         * which is why the filter downstream is dt-based rather than assuming a rate.
         */
        const val SAMPLING_PERIOD_MICROS = 20_000

        /** Readings discarded after registration, while the fusion settles. */
        const val SETTLE_NANOS = 200_000_000L
    }
}
