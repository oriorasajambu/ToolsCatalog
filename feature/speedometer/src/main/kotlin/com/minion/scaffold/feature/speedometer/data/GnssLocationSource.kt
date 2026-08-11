package com.minion.scaffold.feature.speedometer.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.minion.scaffold.core.gnss.model.GnssFix
import com.minion.scaffold.feature.speedometer.domain.LocationEvent
import com.minion.scaffold.feature.speedometer.domain.LocationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `LocationManager` bridge.
 *
 * ## The platform provider, not Play Services
 *
 * Following the precedent `:feature:weather`'s `LocationFixProvider` already sets — no Play
 * dependency, and it works on a device without it. For this feature there is a second and stronger
 * reason: **a fused provider blends in network-derived positions.** Wi-Fi and cell positioning are
 * exactly what an offline GNSS tool must not silently fall back to, and a speed derived from a
 * Wi-Fi position would be nonsense presented identically to a real one.
 *
 * `GPS_PROVIDER` therefore, explicitly.
 *
 * ## One hertz
 *
 * The standard GNSS output rate, and what a speedometer needs. It is also expensive — the receiver
 * stays at full power — which is why collection is tied to a visible screen upstream.
 */
@Singleton
internal class GnssLocationSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationSource {

    private val locationManager: LocationManager?
        get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /**
     * Suppressed once, with the check immediately below.
     *
     * Lint cannot follow the guard across the `callbackFlow` boundary. The same shape, and the same
     * reasoning, as `:feature:soundmeter`'s microphone open.
     */
    @SuppressLint("MissingPermission")
    override fun fixes(): Flow<LocationEvent> = callbackFlow {
        val manager = locationManager
        if (manager == null || !hasFineLocation()) {
            close()
            return@callbackFlow
        }

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            trySend(LocationEvent.ProviderDisabled)
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(LocationEvent.Fix(location.toGnssFix()))
            }

            override fun onProviderEnabled(provider: String) {
                trySend(LocationEvent.ProviderEnabled)
            }

            override fun onProviderDisabled(provider: String) {
                trySend(LocationEvent.ProviderDisabled)
            }

            // Deprecated and abstract on older platforms; overriding it is what keeps this class
            // instantiable on API 29 without a crash at registration.
            @Deprecated("Deprecated in Java", ReplaceWith(""))
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        }

        val registered = runCatching {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MILLIS,
                // Zero metres. A distance filter would suppress updates while stationary, which is
                // precisely when the screen still needs to be told the speed is zero.
                0f,
                listener,
                context.mainLooper,
            )
            true
        }.getOrDefault(false)

        if (!registered) {
            close()
            return@callbackFlow
        }

        awaitClose { manager.removeUpdates(listener) }
    }
        // Conflated. A stale fix is not a measurement of now, and a slow collector must never
        // back-pressure the location callback.
        .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        /** 1 Hz — the standard GNSS rate, and what a live speed readout needs. */
        const val UPDATE_INTERVAL_MILLIS = 1000L
    }
}

/**
 * Converts a platform [Location] into the pure module's shape.
 *
 * Every `has*` check matters. `Location` returns 0.0 rather than throwing for a value it does not
 * have, so reading `speed` without checking `hasSpeed()` turns "this receiver reports no velocity"
 * into "this receiver says you are stationary" — the same value, an entirely different claim, and one
 * that would send the whole pipeline down the derived-speed path without anyone noticing.
 */
internal fun Location.toGnssFix(): GnssFix = GnssFix(
    latitude = latitude,
    longitude = longitude,
    ellipsoidalAltitudeMeters = if (hasAltitude()) altitude else null,
    speedMetersPerSecond = if (hasSpeed()) speed.toDouble() else null,
    speedAccuracyMetersPerSecond = if (hasSpeedAccuracy()) {
        speedAccuracyMetersPerSecond.toDouble()
    } else {
        null
    },
    horizontalAccuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
    verticalAccuracyMeters = if (hasVerticalAccuracy()) {
        verticalAccuracyMeters.toDouble()
    } else {
        null
    },
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    // isMock is API 31; isFromMockProvider is the deprecated equivalent below it. Both answer the
    // same question, and the answer has to reach the screen either way.
    fromMockProvider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        isMock
    } else {
        @Suppress("DEPRECATION")
        isFromMockProvider
    },
)
