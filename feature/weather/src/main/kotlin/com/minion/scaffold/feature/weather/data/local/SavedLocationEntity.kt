package com.minion.scaffold.feature.weather.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.minion.scaffold.core.weather.model.Location

/**
 * A place the user added by searching (SPEC.md §6). Never holds the GPS card — that one is
 * transient, lives only in `forecast_cache` under the `"current"` key, and cannot be deleted or
 * reordered, so it has no row here.
 *
 * [sortOrder] is dense and rewritten wholesale on every reorder rather than being a sparse
 * "leave gaps and insert between" scheme: the list is small enough that rewriting it is cheap, and
 * a dense order can never drift into the state where two rows tie and the list order goes
 * non-deterministic.
 */
@Entity(tableName = "saved_locations")
internal data class SavedLocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int,
)

internal fun SavedLocationEntity.toDomain(): Location = Location(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    isCurrentLocation = false,
)

internal fun Location.toEntity(sortOrder: Int): SavedLocationEntity = SavedLocationEntity(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    sortOrder = sortOrder,
)
