package com.minion.scaffold.feature.exifstrip.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.minion.scaffold.feature.exifstrip.domain.ExifStripPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * DataStore lives in this feature, not `:core:data` — only this feature reads it, and the repo
 * promotes on the *second* consumer rather than in anticipation of one. Same placement and reasoning
 * as `LevelPreferencesDataStore` and `SoundMeterPreferencesDataStore`.
 */
private val Context.exifStripPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "exif_strip_preferences",
)

internal class ExifStripPreferencesDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExifStripPreferencesRepository {

    override val keepColourProfile: Flow<Boolean> =
        context.exifStripPreferences.data.map { it[KEEP_PROFILE_KEY] ?: DEFAULT_KEEP }

    override suspend fun setKeepColourProfile(keep: Boolean) {
        context.exifStripPreferences.edit { it[KEEP_PROFILE_KEY] = keep }
    }

    private companion object {
        val KEEP_PROFILE_KEY = booleanPreferencesKey("exif_keep_colour_profile")

        /** See the repository interface for why keeping it is the safer default. */
        const val DEFAULT_KEEP = true
    }
}
