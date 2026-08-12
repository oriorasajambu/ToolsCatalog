package com.minion.scaffold.feature.speedometer.domain

import com.minion.scaffold.core.gnss.model.CoordinateFormat
import com.minion.scaffold.core.gnss.model.DistanceUnit
import com.minion.scaffold.core.gnss.model.SpeedUnit
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * What the speedometer remembers.
 *
 * [speedUnit] is independent of [distanceUnit] on purpose. The real combinations do not line up:
 * knots pairs with metres at sea and with feet in aviation, but never with miles, so a single
 * metric-or-imperial switch would force knots into a box it does not fit. One switch drives altitude
 * and distance; speed has its own.
 */
internal interface SpeedometerPreferencesRepository {

    /** The unit the speed is shown in. */
    val speedUnit: Flow<SpeedUnit>

    /** The unit altitude and distance are shown in. */
    val distanceUnit: Flow<DistanceUnit>

    /** The format coordinates are shown in. */
    val coordinateFormat: Flow<CoordinateFormat>

    /**
     * Sets the speed unit.
     *
     * @param unit The speed unit to display in.
     */
    suspend fun setSpeedUnit(unit: SpeedUnit)

    /**
     * Sets the distance unit.
     *
     * @param unit The distance unit to display in.
     */
    suspend fun setDistanceUnit(unit: DistanceUnit)

    /**
     * Sets the coordinate format.
     *
     * @param format The coordinate format to display in.
     */
    suspend fun setCoordinateFormat(format: CoordinateFormat)
}

/** Observes the speedometer's three display preferences. */
internal class ObserveSpeedometerPreferencesUseCase @Inject constructor(
    private val repository: SpeedometerPreferencesRepository,
) {
    /** The speed unit. */
    val speedUnit: Flow<SpeedUnit> get() = repository.speedUnit

    /** The distance unit. */
    val distanceUnit: Flow<DistanceUnit> get() = repository.distanceUnit

    /** The coordinate format. */
    val coordinateFormat: Flow<CoordinateFormat> get() = repository.coordinateFormat
}

/** Sets the speed unit. */
internal class SetSpeedUnitUseCase @Inject constructor(
    private val repository: SpeedometerPreferencesRepository,
) {
    /** @param unit The speed unit to display in. */
    suspend operator fun invoke(unit: SpeedUnit) = repository.setSpeedUnit(unit)
}

/** Sets the distance unit. */
internal class SetDistanceUnitUseCase @Inject constructor(
    private val repository: SpeedometerPreferencesRepository,
) {
    /** @param unit The distance unit to display in. */
    suspend operator fun invoke(unit: DistanceUnit) = repository.setDistanceUnit(unit)
}

/** Sets the coordinate format. */
internal class SetCoordinateFormatUseCase @Inject constructor(
    private val repository: SpeedometerPreferencesRepository,
) {
    /** @param format The coordinate format to display in. */
    suspend operator fun invoke(format: CoordinateFormat) = repository.setCoordinateFormat(format)
}
