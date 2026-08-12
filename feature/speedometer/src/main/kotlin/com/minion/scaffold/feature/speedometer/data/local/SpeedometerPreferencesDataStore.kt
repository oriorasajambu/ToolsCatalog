package com.minion.scaffold.feature.speedometer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minion.scaffold.core.gnss.model.CoordinateFormat
import com.minion.scaffold.core.gnss.model.DistanceUnit
import com.minion.scaffold.core.gnss.model.SpeedUnit
import com.minion.scaffold.feature.speedometer.domain.SpeedometerPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * DataStore lives in this feature, not `:core:data` — only this feature reads it, and the repo
 * promotes on the *second* consumer rather than in anticipation of one.
 */
private val Context.speedometerPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "speedometer_preferences",
)

internal class SpeedometerPreferencesDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SpeedometerPreferencesRepository {

    /**
     * Stored by `name`, never by ordinal.
     *
     * An ordinal would silently rebind the moment an entry was added or reordered, and km/h read as
     * knots is a factor of 1.85 with nothing on screen to suggest anything changed.
     */
    override val speedUnit: Flow<SpeedUnit> = context.speedometerPreferences.data.map { preferences ->
        preferences[SPEED_UNIT_KEY]
            ?.let { name -> SpeedUnit.entries.firstOrNull { it.name == name } }
            ?: SpeedUnit.KilometersPerHour
    }

    override val distanceUnit: Flow<DistanceUnit> =
        context.speedometerPreferences.data.map { preferences ->
            preferences[DISTANCE_UNIT_KEY]
                ?.let { name -> DistanceUnit.entries.firstOrNull { it.name == name } }
                ?: DistanceUnit.Metric
        }

    override val coordinateFormat: Flow<CoordinateFormat> =
        context.speedometerPreferences.data.map { preferences ->
            preferences[COORDINATE_FORMAT_KEY]
                ?.let { name -> CoordinateFormat.entries.firstOrNull { it.name == name } }
                ?: CoordinateFormat.Decimal
        }

    override suspend fun setSpeedUnit(unit: SpeedUnit) {
        context.speedometerPreferences.edit { it[SPEED_UNIT_KEY] = unit.name }
    }

    override suspend fun setDistanceUnit(unit: DistanceUnit) {
        context.speedometerPreferences.edit { it[DISTANCE_UNIT_KEY] = unit.name }
    }

    override suspend fun setCoordinateFormat(format: CoordinateFormat) {
        context.speedometerPreferences.edit { it[COORDINATE_FORMAT_KEY] = format.name }
    }

    private companion object {
        val SPEED_UNIT_KEY = stringPreferencesKey("speedometer_speed_unit")
        val DISTANCE_UNIT_KEY = stringPreferencesKey("speedometer_distance_unit")
        val COORDINATE_FORMAT_KEY = stringPreferencesKey("speedometer_coordinate_format")
    }
}
