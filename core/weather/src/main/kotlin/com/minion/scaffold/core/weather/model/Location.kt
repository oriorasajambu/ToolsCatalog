package com.minion.scaffold.core.weather.model

/**
 * A place a forecast can be requested for.
 *
 * [id] is `"current"` for the pinned GPS card — a stable key so the forecast cache has exactly one
 * row for wherever the device currently is — or a geocoding result id for anything the user added.
 */
data class Location(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean,
)
