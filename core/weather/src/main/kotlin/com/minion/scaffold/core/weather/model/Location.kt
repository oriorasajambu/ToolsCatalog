package com.minion.scaffold.core.weather.model

/**
 * A place a forecast can be requested for.
 *
 * [id] is `"current"` for the pinned GPS card — a stable key so the forecast cache has exactly one
 * row for wherever the device currently is — or a geocoding result id for anything the user added.
 *
 * @property id                The stable cache key: `"current"` or a geocoding result id.
 * @property name              The display name.
 * @property latitude          Latitude in decimal degrees.
 * @property longitude         Longitude in decimal degrees.
 * @property isCurrentLocation Whether this is the pinned GPS location.
 */
data class Location(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean,
)
