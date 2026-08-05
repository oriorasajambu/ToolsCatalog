package com.minion.scaffold.feature.weather.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface SavedLocationDao {

    /**
     * A [Flow], unlike `ForecastCacheDao`'s one-shot reads: this list is edited from the search
     * screen and re-rendered on the home screen, so the home screen has to see an add land without
     * being told to go and re-read.
     */
    @Query("SELECT * FROM saved_locations ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<SavedLocationEntity>>

    @Query("SELECT * FROM saved_locations ORDER BY sortOrder ASC")
    suspend fun getAll(): List<SavedLocationEntity>

    @Upsert
    suspend fun upsert(entity: SavedLocationEntity)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM saved_locations")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE saved_locations SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    /**
     * Rewrites the whole order in one transaction, so a reorder can never be observed half-applied
     * — without the transaction the [observeAll] flow would emit an intermediate list where two
     * rows briefly share a `sortOrder`, and the UI would flicker through a scrambled order.
     */
    @Transaction
    suspend fun replaceOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updateSortOrder(id, index) }
    }
}
