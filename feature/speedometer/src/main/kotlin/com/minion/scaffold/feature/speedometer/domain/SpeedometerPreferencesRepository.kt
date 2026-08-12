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
    val speedUnit: Flow<SpeedUnit>
    val distanceUnit: Flow<DistanceUnit>
    val coordinateFormat: Flow<CoordinateFormat>

    suspend fun setSpeedUnit(unit: SpeedUnit)
    suspend fun setDistanceUnit(unit: DistanceUnit)
    suspend fun setCoordinateFormat(format: CoordinateFormat)
}

internal class ObserveSpeedometerPreferencesUseCase @Inject constructor(
    private val repository: SpeedometerPreferencesRepository,
) {
    val speedUnit: Flow<SpeedUnit> get() = repository.speedUnit
    val distanceUnit: Flow<DistanceUnit> get() = repository.distanceUnit
    val coordinateFormat: Flow<CoordinateFormat> get() = repository.coordinateFormat
}

internal class SetSpeedUnitUseCase @Inject constructor(
    private val repository: SpeedometerPreferencesRepository,
) {
    suspend operator fun invoke(unit: SpeedUnit) = repository.setSpeedUnit(unit)
}

internal class SetDistanceUnitUseCase @Inject constructor(
    private val repository: SpeedometerPreferencesRepository,
) {
    suspend operator fun invoke(unit: DistanceUnit) = repository.setDistanceUnit(unit)
}

internal class SetCoordinateFormatUseCase @Inject constructor(
    private val repository: SpeedometerPreferencesRepository,
) {
    suspend operator fun invoke(format: CoordinateFormat) = repository.setCoordinateFormat(format)
}
