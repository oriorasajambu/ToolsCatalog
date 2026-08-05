package com.minion.scaffold.feature.ocr.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minion.scaffold.core.ocr.model.OcrEngine
import com.minion.scaffold.feature.ocr.domain.OcrPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * DataStore lives in this feature, not `:core:data`, because only this feature reads it — the
 * repo's rule is that something moves into a core module once a *second* consumer appears, not in
 * anticipation of one. Same placement, and same reasoning, as `WeatherPreferencesDataStore`.
 */
private val Context.ocrPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "ocr_preferences",
)

internal class OcrPreferencesDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OcrPreferencesRepository {

    /** Stored and read by [OcrEngine.name] — see [OcrEngine.ofName] for why, and for the fallback. */
    override val engine: Flow<OcrEngine> = context.ocrPreferences.data.map { preferences ->
        OcrEngine.ofName(preferences[ENGINE_KEY])
    }

    override suspend fun setEngine(engine: OcrEngine) {
        context.ocrPreferences.edit { preferences -> preferences[ENGINE_KEY] = engine.name }
    }

    private companion object {
        val ENGINE_KEY = stringPreferencesKey("ocr_engine")
    }
}
