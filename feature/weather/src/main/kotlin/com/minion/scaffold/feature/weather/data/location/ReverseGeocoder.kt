package com.minion.scaffold.feature.weather.data.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Resolves a GPS fix to a display name for the pinned current-location card, using the on-device
 * [Geocoder].
 *
 * Deliberately *not* Open-Meteo's Geocoding API, even though the original spec asked for it: that
 * API is search-by-name only (`/v1/search`, `/v1/get`) and has no reverse lat/lon -> name endpoint
 * to call. Upstream has acknowledged the gap and put it on their task list with no ETA
 * (github.com/open-meteo/open-meteo discussion #698), so this is an upstream limitation rather
 * than an oversight here — worth re-checking if that ever ships.
 *
 * Best-effort by design: [Geocoder.isPresent] is false on some devices (no Play Services / no
 * geocoder backend installed), and even where it's present a fix in open water or unmapped terrain
 * can return nothing. Either case returns `null`; the caller falls back to a lat/lon-formatted
 * string rather than blocking the pinned card on a service this feature cannot make guarantees
 * about.
 */
internal class ReverseGeocoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun displayNameFor(latitude: Double, longitude: Double): String? {
        val geocoder = Geocoder(context, Locale.getDefault())
        if (!Geocoder.isPresent()) return null

        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationAsync(latitude, longitude)
        } else {
            geocoder.getFromLocationBlocking(latitude, longitude)
        }

        return address?.let { listOfNotNull(it.locality, it.adminArea).joinToString(", ").ifBlank { null } }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun Geocoder.getFromLocationAsync(latitude: Double, longitude: Double) =
        suspendCancellableCoroutine { continuation ->
            try {
                getFromLocation(latitude, longitude, 1) { addresses ->
                    if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                }
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun Geocoder.getFromLocationBlocking(latitude: Double, longitude: Double) =
        withContext(Dispatchers.IO) {
            try {
                getFromLocation(latitude, longitude, 1)?.firstOrNull()
            } catch (_: Exception) {
                null
            }
        }
}
