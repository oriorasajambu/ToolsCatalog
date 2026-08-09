package com.minion.scaffold.feature.level.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minion.scaffold.core.level.model.Calibration
import com.minion.scaffold.feature.level.domain.LevelPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * DataStore lives in this feature, not `:core:data` — only this feature reads it, and the repo
 * promotes to a core module on the *second* consumer rather than in anticipation of one. Same
 * placement and same reasoning as `OcrPreferencesDataStore`.
 */
private val Context.levelPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "level_preferences",
)

internal class LevelPreferencesDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LevelPreferencesRepository {

    /**
     * The calibration, stored component-wise.
     *
     * [VERSION_KEY] is written alongside so a future change to what a calibration means can be
     * migrated rather than guessed at — a stored bias silently reinterpreted under new semantics
     * would corrupt every reading with no visible symptom. An unrecognised version reads as
     * uncalibrated, which is honest: better to ask for twenty seconds of recalibration than to
     * apply a correction whose meaning is unknown.
     *
     * [MASK_KEY] records which components the flip actually observed. A flat calibration measures
     * two of the three, so this is what lets the UI say "calibrated for flat use" rather than
     * implying more than was measured.
     */
    override val calibration: Flow<Calibration> = context.levelPreferences.data.map { preferences ->
        val version = preferences[VERSION_KEY] ?: return@map Calibration.NONE
        if (version != Calibration.CURRENT_VERSION) return@map Calibration.NONE

        Calibration(
            x = preferences[BIAS_X_KEY] ?: 0.0,
            y = preferences[BIAS_Y_KEY] ?: 0.0,
            z = preferences[BIAS_Z_KEY] ?: 0.0,
            measuredMask = preferences[MASK_KEY] ?: 0,
            takenAtMillis = preferences[TAKEN_AT_KEY] ?: 0L,
            surfaceTiltDegrees = preferences[SURFACE_TILT_KEY] ?: 0.0,
            version = version,
        )
    }

    override val soundEnabled: Flow<Boolean> = context.levelPreferences.data.map { preferences ->
        preferences[SOUND_ENABLED_KEY] ?: false
    }

    override val calibrationPromptSeen: Flow<Boolean> =
        context.levelPreferences.data.map { preferences ->
            preferences[PROMPT_SEEN_KEY] ?: false
        }

    override suspend fun setCalibration(calibration: Calibration) {
        context.levelPreferences.edit { preferences ->
            preferences[VERSION_KEY] = calibration.version
            preferences[BIAS_X_KEY] = calibration.x
            preferences[BIAS_Y_KEY] = calibration.y
            preferences[BIAS_Z_KEY] = calibration.z
            preferences[MASK_KEY] = calibration.measuredMask
            preferences[TAKEN_AT_KEY] = calibration.takenAtMillis
            preferences[SURFACE_TILT_KEY] = calibration.surfaceTiltDegrees
        }
    }

    override suspend fun clearCalibration() {
        context.levelPreferences.edit { preferences ->
            preferences.remove(VERSION_KEY)
            preferences.remove(BIAS_X_KEY)
            preferences.remove(BIAS_Y_KEY)
            preferences.remove(BIAS_Z_KEY)
            preferences.remove(MASK_KEY)
            preferences.remove(TAKEN_AT_KEY)
            preferences.remove(SURFACE_TILT_KEY)
        }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        context.levelPreferences.edit { it[SOUND_ENABLED_KEY] = enabled }
    }

    override suspend fun setCalibrationPromptSeen() {
        context.levelPreferences.edit { it[PROMPT_SEEN_KEY] = true }
    }

    private companion object {

        val VERSION_KEY = intPreferencesKey("level_calibration_version")
        val BIAS_X_KEY = doublePreferencesKey("level_calibration_x")
        val BIAS_Y_KEY = doublePreferencesKey("level_calibration_y")
        val BIAS_Z_KEY = doublePreferencesKey("level_calibration_z")
        val MASK_KEY = intPreferencesKey("level_calibration_mask")
        val TAKEN_AT_KEY = longPreferencesKey("level_calibration_taken_at")
        val SURFACE_TILT_KEY = doublePreferencesKey("level_calibration_surface_tilt")

        val SOUND_ENABLED_KEY = booleanPreferencesKey("level_sound_enabled")
        val PROMPT_SEEN_KEY = booleanPreferencesKey("level_calibration_prompt_seen")
    }
}
