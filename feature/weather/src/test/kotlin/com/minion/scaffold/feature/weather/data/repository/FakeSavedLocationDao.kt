package com.minion.scaffold.feature.weather.data.repository

import com.minion.scaffold.feature.weather.data.local.SavedLocationDao
import com.minion.scaffold.feature.weather.data.local.SavedLocationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [SavedLocationDao]. Room needs Robolectric to run in a JVM unit test — not a
 * dependency this repo carries — so the repository is tested against this instead.
 *
 * Backed by a [MutableStateFlow] so [observeAll] genuinely re-emits on every write, which is what
 * the tests covering "the home screen sees an add land" actually depend on.
 */
internal class FakeSavedLocationDao : SavedLocationDao {

    private val rows = MutableStateFlow<List<SavedLocationEntity>>(emptyList())

    override fun observeAll(): Flow<List<SavedLocationEntity>> =
        rows.map { list -> list.sortedBy { it.sortOrder } }

    override suspend fun getAll(): List<SavedLocationEntity> = rows.value.sortedBy { it.sortOrder }

    override suspend fun upsert(entity: SavedLocationEntity) {
        rows.value = rows.value.filterNot { it.id == entity.id } + entity
    }

    override suspend fun deleteById(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun maxSortOrder(): Int = rows.value.maxOfOrNull { it.sortOrder } ?: -1

    override suspend fun updateSortOrder(id: String, sortOrder: Int) {
        rows.value = rows.value.map { if (it.id == id) it.copy(sortOrder = sortOrder) else it }
    }
}
