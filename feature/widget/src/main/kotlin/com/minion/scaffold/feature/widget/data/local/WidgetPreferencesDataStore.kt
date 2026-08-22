package com.minion.scaffold.feature.widget.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minion.scaffold.core.data.widget.MAX_PINNED_TOOLS
import com.minion.scaffold.core.data.widget.PinnedToolsRepository
import com.minion.scaffold.core.domain.featureflag.FeatureFlagRepository
import com.minion.scaffold.core.toolcatalog.ToolCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The store name appears in three places: here, `app/src/main/res/xml/backup_rules.xml` and
 * `data_extraction_rules.xml`. Renaming it without updating both XML files silently stops the
 * backup — no build failure, no runtime error, just a user who restores a device and finds an
 * empty widget.
 */
private val Context.widgetPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "widget_preferences",
)

/**
 * The pinned list, on disk.
 *
 * Ids are joined by the ASCII unit separator rather than stored as a `stringSetPreferencesKey`: a
 * `Set` has no order, and order is half the point of the configuration screen. The separator is
 * chosen because it cannot occur in a kebab-case tool id.
 *
 * Ids rather than serialized routes, for the reason `ScanPurpose`'s `@Keep` documents: a
 * serialized route in persistent storage is a second thing R8 could rename out from under a stored
 * value. Ids are also what keeps `qr-scan` and `qr-edit` distinguishable when both resolve to the
 * same route.
 */
internal class WidgetPreferencesDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val featureFlagRepository: FeatureFlagRepository,
) : PinnedToolsRepository {

    override val pinnedIds: Flow<List<String>> =
        context.widgetPreferences.data.map { preferences -> preferences.readPinned() }

    /**
     * Reads once, seeding the default when nothing has ever been written.
     *
     * **Absent and present-but-empty must stay distinguishable.** Conflating them hands the
     * defaults back to a user who deliberately unpinned everything, on the next read, with no way
     * to make it stop. That is why this checks for a missing key rather than an empty list.
     */
    override suspend fun currentPinnedIds(): List<String> {
        val stored = context.widgetPreferences.data.first()[PINNED_TOOL_IDS]
        if (stored != null) return stored.decode()

        val seed = defaultPinnedIds()
        setPinned(seed)
        return seed
    }

    override suspend fun setPinned(ids: List<String>) {
        val capped = ids.distinct().take(MAX_PINNED_TOOLS)
        context.widgetPreferences.edit { preferences ->
            preferences[PINNED_TOOL_IDS] = capped.joinToString(SEPARATOR)
        }
    }

    /**
     * The first [MAX_PINNED_TOOLS] *enabled* tools, in catalog order.
     *
     * Enabled rather than merely present, so a first run never seeds a strip of greyed tiles for
     * something the console is currently withholding.
     */
    private suspend fun defaultPinnedIds(): List<String> {
        val flags = featureFlagRepository.flags().first()
        return ToolCatalog.entries
            .asSequence()
            .filter { flags.isEnabled(it.id) }
            .take(MAX_PINNED_TOOLS)
            .map { it.id }
            .toList()
    }

    private fun Preferences.readPinned(): List<String> = this[PINNED_TOOL_IDS]?.decode().orEmpty()

    /**
     * Capped on read as well as on write.
     *
     * A value written by a future build with a larger cap truncates visibly here instead of
     * overflowing the strip at render time.
     */
    private fun String.decode(): List<String> =
        split(SEPARATOR).filter { it.isNotEmpty() }.take(MAX_PINNED_TOOLS)

    private companion object {
        val PINNED_TOOL_IDS = stringPreferencesKey("pinned_tool_ids")

        /**
         * ASCII unit separator, written as an escape rather than a literal control
         * character: a raw 0x1F byte in source is invisible in review and does not
         * survive every editor intact.
         *
         * Chosen because it cannot occur in a kebab-case tool id.
         */
        const val SEPARATOR = "\u001F"
    }
}
