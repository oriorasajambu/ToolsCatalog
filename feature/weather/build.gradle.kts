/**
 * Weather Report: current conditions plus a hourly/daily forecast for the device's GPS location,
 * fetched from Open-Meteo (the app's first real network call — see SPEC.md §1 for why) and cached
 * in Room so the screen stays useful offline after the first successful fetch.
 *
 * Room is wired here rather than via `:core:data`, because this is the first and only consumer —
 * promoting the cache shape to a shared module is a decision for whenever a second feature needs
 * one, per the repo's "two features" rule.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.weather"
}

dependencies {
    implementation(project(":core:weather"))

    // LocationManagerCompat.getCurrentLocation — the platform LocationManager, not Play Services
    // Fused Location (this repo has no Play Services dependency; see LocationFixProvider).
    implementation(libs.androidx.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.room.testing)
}
