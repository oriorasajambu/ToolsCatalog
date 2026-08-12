package com.minion.scaffold.feature.level.domain

import com.minion.scaffold.core.level.model.GravitySample
import kotlinx.coroutines.flow.Flow

/**
 * Where gravity readings come from.
 *
 * An interface purely so the ViewModel is testable: the real implementation needs `SensorManager`,
 * which does not exist in a JVM unit test. A test feeds a `MutableSharedFlow` and drives the whole
 * pipeline without Robolectric or an emulator — the same seam, and the same reason, as
 * `:feature:ocr`'s `TextRecognizer`.
 */
internal interface GravitySource {

    /**
     * Which sensor is actually behind the readings.
     *
     * Resolved once at construction rather than per sample, and surfaced because the two behave
     * visibly differently — see [GravitySensor].
     */
    val sensor: GravitySensor

    /**
     * Readings, for as long as this is collected.
     *
     * Cold: the sensor is registered when collection starts and unregistered when it stops, so
     * nothing is powered while the screen is away.
     *
     * @return A cold [Flow] of gravity samples that registers the sensor while collected.
     */
    fun samples(): Flow<GravitySample>
}

/** Which stream the readings are coming from, and therefore how much to trust them. */
enum class GravitySensor {

    /**
     * `TYPE_GRAVITY` — the platform's fused estimate.
     *
     * Uses the gyroscope where there is one, and is already smoothed. Note this is not always the
     * same thing: without a gyro the platform synthesises it from a low-passed accelerometer with
     * parameters this app does not control, so on those devices the fallback below is effectively
     * what is running anyway, filtered twice.
     */
    Fused,

    /**
     * Raw `TYPE_ACCELEROMETER`, filtered here.
     *
     * Noisier and slower to settle, and — importantly — linear acceleration corrupts it directly
     * rather than merely adding noise, so the motion warning matters far more on this path. The UI
     * says which one is in use.
     */
    Accelerometer,

    /** Neither exists. The tool explains itself rather than showing a bubble that cannot move. */
    Unavailable,
}
