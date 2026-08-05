package com.minion.scaffold.feature.weather.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minion.scaffold.core.weather.model.WeatherUnit
import com.minion.scaffold.feature.weather.domain.WeatherPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * DataStore lives in this feature, not `:core:data`, because only this feature reads it — the
 * repo's rule is that something moves into a core module once a *second* consumer appears, not in
 * anticipation of one.
 */
private val Context.weatherPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "weather_preferences",
)

internal class WeatherPreferencesDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WeatherPreferencesRepository {

    /**
     * Stored by [WeatherUnit.name], not by ordinal: an ordinal silently remaps every stored
     * preference the moment someone reorders the enum, and nothing fails loudly when it does.
     * An unrecognised name falls back to the default rather than throwing.
     */
    override val unit: Flow<WeatherUnit> = context.weatherPreferences.data.map { preferences ->
        preferences[UNIT_KEY]
            ?.let { stored -> WeatherUnit.entries.firstOrNull { it.name == stored } }
            ?: WeatherUnit.METRIC
    }

    override suspend fun setUnit(unit: WeatherUnit) {
        context.weatherPreferences.edit { preferences -> preferences[UNIT_KEY] = unit.name }
    }

    private companion object {
        val UNIT_KEY = stringPreferencesKey("weather_unit")
    }
}
