/**
 * Weather domain shaping — pure Kotlin, zero Android.
 *
 * Owns the shapes both Open-Meteo endpoints get mapped into, the WMO weather-code lookup, the
 * app-computed "notable conditions" thresholds, and metric/imperial conversion. `:feature:weather`
 * owns the DTOs-as-network-models, the Retrofit interfaces and the Room cache — this module only
 * shapes data, matching `:core:wifi`/`:core:emv`.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    implementation(libs.javax.inject)
}
