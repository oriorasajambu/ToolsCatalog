package com.minion.scaffold.feature.weather.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume

internal data class LatLng(val latitude: Double, val longitude: Double)

/**
 * Resolves the device's current location via the platform [LocationManager] — not Play Services
 * Fused Location, which this repo has no dependency on and which a device with no Play Services
 * (or with it disabled) cannot provide.
 *
 * [LocationManagerCompat.getCurrentLocation] is used rather than hand-rolling the API 29 vs 30+
 * split (`requestSingleUpdate` vs `getCurrentLocation`) — it already does that internally, back to
 * API 19.
 *
 * Callers must have already confirmed the permission is granted (SPEC.md §5's gate); every call
 * here is `@SuppressLint`-guarded rather than permission-checked again, and a stray
 * [SecurityException] still degrades to `null` instead of crashing the screen.
 */
internal class LocationFixProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * The best fix available: a fresh last-known location if there is one, otherwise a one-shot
     * request from the most accurate enabled provider. `null` when nothing works — no provider
     * enabled, permission revoked mid-call, or the one-shot request times out with nothing cached.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentFix(): LatLng? {
        val provider = bestEnabledProvider() ?: return null

        lastKnownLocation(provider)?.let { if (it.isFresh()) return it.toLatLng() }

        return requestCurrentLocation(provider)?.toLatLng() ?: lastKnownLocation(provider)?.toLatLng()
    }

    private fun bestEnabledProvider(): String? {
        val manager = locationManager
        return when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(provider: String): Location? = try {
        locationManager.getLastKnownLocation(provider)
    } catch (_: SecurityException) {
        null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }

            try {
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    provider,
                    cancellationSignal,
                    Executor { it.run() },
                ) { location -> if (continuation.isActive) continuation.resume(location) }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun Location.isFresh(): Boolean =
        System.currentTimeMillis() - time < LAST_KNOWN_FRESHNESS_MS

    private fun Location.toLatLng() = LatLng(latitude, longitude)

    private companion object {
        const val LAST_KNOWN_FRESHNESS_MS = 5 * 60 * 1000L
    }
}
