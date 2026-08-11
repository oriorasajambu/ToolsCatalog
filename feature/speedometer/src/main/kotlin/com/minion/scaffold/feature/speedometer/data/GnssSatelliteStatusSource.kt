package com.minion.scaffold.feature.speedometer.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.minion.scaffold.feature.speedometer.domain.Constellation
import com.minion.scaffold.feature.speedometer.domain.SatelliteStatus
import com.minion.scaffold.feature.speedometer.domain.SatelliteStatusSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the receiver can see.
 *
 * Registered alongside the fix stream and shown while there is no fix yet. Without a network there is
 * no almanac to download, so a cold start can take minutes and indoors may never succeed — and a
 * blank waiting screen gives no way to tell which situation you are in. Satellites visible but none
 * used means the receiver is working and needs longer; none visible means go outside.
 *
 * Deliberately **not** used to grade fix quality once there is a fix. Satellite count is a proxy —
 * eight satellites arriving by reflection in a street canyon are worse than five in the open — and
 * the receiver already publishes the answer as an accuracy estimate. See `ClassifyFixQualityUseCase`.
 */
@Singleton
internal class GnssSatelliteStatusSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SatelliteStatusSource {

    private val locationManager: LocationManager?
        get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @SuppressLint("MissingPermission")
    override fun status(): Flow<SatelliteStatus> = callbackFlow {
        val manager = locationManager
        if (manager == null || !hasFineLocation()) {
            trySend(SatelliteStatus.NONE)
            close()
            return@callbackFlow
        }

        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                trySend(status.toSatelliteStatus())
            }

            override fun onStopped() {
                trySend(SatelliteStatus.NONE)
            }
        }

        val registered = runCatching {
            manager.registerGnssStatusCallback(context.mainExecutor, callback)
        }.getOrDefault(false)

        if (!registered) {
            trySend(SatelliteStatus.NONE)
            close()
            return@callbackFlow
        }

        awaitClose { manager.unregisterGnssStatusCallback(callback) }
    }
        .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun GnssStatus.toSatelliteStatus(): SatelliteStatus {
    val strengths = mutableListOf<Float>()
    val constellations = mutableSetOf<Constellation>()
    var used = 0

    for (index in 0 until satelliteCount) {
        strengths += getCn0DbHz(index)
        constellations += constellationOf(getConstellationType(index))
        if (usedInFix(index)) used++
    }

    return SatelliteStatus(
        visible = satelliteCount,
        usedInFix = used,
        signalStrengths = strengths,
        constellations = constellations,
    )
}

private fun constellationOf(type: Int): Constellation = when (type) {
    GnssStatus.CONSTELLATION_GPS -> Constellation.Gps
    GnssStatus.CONSTELLATION_GLONASS -> Constellation.Glonass
    GnssStatus.CONSTELLATION_GALILEO -> Constellation.Galileo
    GnssStatus.CONSTELLATION_BEIDOU -> Constellation.BeiDou
    GnssStatus.CONSTELLATION_QZSS -> Constellation.Qzss
    GnssStatus.CONSTELLATION_IRNSS -> Constellation.Irnss
    GnssStatus.CONSTELLATION_SBAS -> Constellation.Sbas
    else -> Constellation.Unknown
}
