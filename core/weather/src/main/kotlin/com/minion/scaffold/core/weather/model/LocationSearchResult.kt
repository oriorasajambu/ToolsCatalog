package com.minion.scaffold.core.weather.model

/**
 * One hit from a place-name search, before the user has decided to keep it.
 *
 * Distinct from [Location] because a search result carries the fields that exist purely to tell
 * near-identical names apart — there are dozens of Springfields, and a list of them showing only
 * "Springfield" nine times is unusable. Once the user picks one it becomes a plain [Location]; the
 * disambiguation fields have done their job by then and are not persisted.
 */
data class LocationSearchResult(
    /** The provider's result id. */
    val id: String,
    /** The place name. */
    val name: String,

    /** e.g. "United States". Null when the provider has none for this hit. */
    val country: String?,

    /** First-level administrative area — state, province, region. Null when the provider has none. */
    val admin1: String?,

    /** Latitude in decimal degrees. */
    val latitude: Double,
    /** Longitude in decimal degrees. */
    val longitude: Double,
) {

    /**
     * The saved-location form of this hit, once the user adds it.
     *
     * @return A [Location] with the same coordinates, flagged as not the current location.
     */
    fun toLocation(): Location = Location(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        isCurrentLocation = false,
    )
}
