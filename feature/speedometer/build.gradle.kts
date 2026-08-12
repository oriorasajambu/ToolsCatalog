/**
 * GPS speedometer and altimeter, offline.
 *
 * Speed, altitude above sea level and position from the device's own GNSS receiver, with no network
 * of any kind.
 *
 * The measurement shaping lives in `:core:gnss` — the geoid conversion, the rules that decide when a
 * speed is really zero, and the trip accumulators. What is here is only the parts that cannot be
 * pure: the `LocationManager` bridge, the satellite-status callback, the pressure sensor, and the UI.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.speedometer"
}

dependencies {
    implementation(project(":core:gnss"))

    // LocationManagerCompat.registerGnssStatusCallback — the Executor overload is API 30 and this
    // module is 29. Added explicitly for the same reason :feature:level and :feature:weather do: the
    // feature convention does not put androidx-core on every classpath for the sake of one consumer.
    implementation(libs.androidx.core)

    // Stores the unit choices and the coordinate format. Feature-local rather than :core:data — only
    // this feature reads it, and the repo promotes on the second consumer.
    implementation(libs.data.store)
}
