package com.minion.scaffold.feature.soundmeter.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.core.sound.model.TimeWeighting
import com.minion.scaffold.core.sound.model.Weighting
import com.minion.scaffold.feature.soundmeter.domain.SoundMeterPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * DataStore lives in this feature, not `:core:data` — only this feature reads it, and the repo
 * promotes to a core module on the *second* consumer rather than in anticipation of one. Same
 * placement and same reasoning as `LevelPreferencesDataStore` and `OcrPreferencesDataStore`.
 */
private val Context.soundMeterPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "sound_meter_preferences",
)

internal class SoundMeterPreferencesDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SoundMeterPreferencesRepository {

    /**
     * Clamped on the way out as well as on the way in.
     *
     * A value written by an older build, or by a slider whose range later narrows, would otherwise
     * apply an offset the UI has no way to show and no way to undo.
     */
    override val offsetDb: Flow<Double> = context.soundMeterPreferences.data.map { preferences ->
        (preferences[OFFSET_KEY] ?: 0.0).coerceIn(
            -SoundReference.MAX_USER_OFFSET_DB,
            SoundReference.MAX_USER_OFFSET_DB,
        )
    }

    /**
     * Stored by `name`, never by ordinal.
     *
     * An ordinal would silently rebind to a different weighting the moment an entry was added or
     * reordered — and A read as C is a 20 dB error at low frequency with nothing on screen to
     * suggest anything changed. An unrecognised name falls back to the default rather than throwing.
     */
    override val weighting: Flow<Weighting> = context.soundMeterPreferences.data.map { preferences ->
        preferences[WEIGHTING_KEY]
            ?.let { name -> Weighting.entries.firstOrNull { it.name == name } }
            ?: Weighting.A
    }

    override val timeWeighting: Flow<TimeWeighting> =
        context.soundMeterPreferences.data.map { preferences ->
            preferences[TIME_WEIGHTING_KEY]
                ?.let { name -> TimeWeighting.entries.firstOrNull { it.name == name } }
                ?: TimeWeighting.Fast
        }

    override suspend fun setOffsetDb(offsetDb: Double) {
        context.soundMeterPreferences.edit { preferences ->
            preferences[OFFSET_KEY] = offsetDb.coerceIn(
                -SoundReference.MAX_USER_OFFSET_DB,
                SoundReference.MAX_USER_OFFSET_DB,
            )
        }
    }

    override suspend fun setWeighting(weighting: Weighting) {
        context.soundMeterPreferences.edit { it[WEIGHTING_KEY] = weighting.name }
    }

    override suspend fun setTimeWeighting(timeWeighting: TimeWeighting) {
        context.soundMeterPreferences.edit { it[TIME_WEIGHTING_KEY] = timeWeighting.name }
    }

    private companion object {
        val OFFSET_KEY = doublePreferencesKey("sound_offset_db")
        val WEIGHTING_KEY = stringPreferencesKey("sound_weighting")
        val TIME_WEIGHTING_KEY = stringPreferencesKey("sound_time_weighting")
    }
}
