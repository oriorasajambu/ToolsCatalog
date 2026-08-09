/**
 * Bubble level and clinometer: is this surface level, what is its slope, and what is the angle
 * between two surfaces.
 *
 * The maths lives in `:core:level` — a level has no visible ground truth, so it has to be provable
 * against synthesised vectors rather than eyeballed on a phone. What is here is only the parts that
 * cannot be pure: the `SensorManager` bridge, the calibration store, the beeper, and the UI.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.level"
}

dependencies {
    implementation(project(":core:level"))

    // ContextCompat.getDisplayOrDefault — Context.getDisplay() is API 30 and this module is 29.
    // Added explicitly for the same reason :feature:weather adds it: the feature convention does
    // not put androidx-core on every feature's classpath for the sake of one consumer.
    implementation(libs.androidx.core)

    // Stores the device's calibration and the beeper toggle. Feature-local rather than :core:data —
    // only this feature reads it, and the repo promotes on the second consumer, not the first.
    implementation(libs.data.store)
}
