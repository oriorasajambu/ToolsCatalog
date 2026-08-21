package com.minion.scaffold.feature.qrscan.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import com.minion.scaffold.feature.qrscan.domain.export.PaymentSchema
import com.minion.scaffold.feature.qrscan.domain.export.PaymentSchemaRepository
import com.minion.scaffold.feature.qrscan.domain.export.PaymentSchemaSource
import com.minion.scaffold.feature.qrscan.domain.export.SCHEMA_FORMAT_VERSION
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore lives in this feature, not `:core:data`, because only this feature reads it — the
 * repo's rule is that something moves into a core module once a *second* consumer appears, not in
 * anticipation of one. Same placement, and same reasoning, as `OcrPreferencesDataStore`.
 */
private val Context.qrScanPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "qrscan_preferences",
)

/**
 * The active schema, and the built-in one behind it.
 *
 * `@Singleton` because the built-in template is read from assets and cached: every JSON export
 * would otherwise re-read the same file, and a second instance would keep a second copy.
 */
@Singleton
internal class PaymentSchemaDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PaymentSchemaRepository {

    private val builtInLock = Mutex()
    private var builtInCache: String? = null

    override val activeSchema: Flow<PaymentSchema> =
        context.qrScanPreferences.data.map { preferences ->
            val stored = preferences[TEMPLATE_KEY].orEmpty()

            if (stored.isBlank()) {
                PaymentSchema(text = builtIn(), source = PaymentSchemaSource.BuiltIn)
            } else {
                PaymentSchema(
                    text = stored,
                    source = PaymentSchemaSource.Custom,
                    label = preferences[LABEL_KEY].orEmpty(),
                    // A template written against an older syntax cannot be trusted to still mean
                    // what it did, and no name check would notice.
                    outdated = preferences[VERSION_KEY] != SCHEMA_FORMAT_VERSION,
                )
            }
        }

    override suspend fun store(text: String, label: String) {
        context.qrScanPreferences.edit { preferences ->
            preferences[TEMPLATE_KEY] = text
            preferences[LABEL_KEY] = label
            preferences[VERSION_KEY] = SCHEMA_FORMAT_VERSION
        }
    }

    override suspend fun reset() {
        context.qrScanPreferences.edit { preferences ->
            preferences.remove(TEMPLATE_KEY)
            preferences.remove(LABEL_KEY)
            preferences.remove(VERSION_KEY)
        }
    }

    /**
     * The template that ships with the app.
     *
     * Read once behind a mutex rather than a plain null check: `activeSchema` is a flow several
     * collectors can be inside at the same moment, and two of them racing would open the asset
     * twice for no reason.
     */
    override suspend fun builtIn(): String = builtInLock.withLock {
        builtInCache ?: withContext(ioDispatcher) {
            context.assets.open(DEFAULT_SCHEMA_ASSET).bufferedReader().use { it.readText() }
        }.also { builtInCache = it }
    }

    private companion object {
        const val DEFAULT_SCHEMA_ASSET = "default_payment_schema.json"

        val TEMPLATE_KEY = stringPreferencesKey("payment_schema_template")
        val LABEL_KEY = stringPreferencesKey("payment_schema_label")
        val VERSION_KEY = intPreferencesKey("payment_schema_format_version")
    }
}
