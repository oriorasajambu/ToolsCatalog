/**
 * Text-recognition shaping — pure Kotlin, zero Android.
 *
 * Owns the reading-order reconstruction that turns ML Kit's detection-ordered blocks into
 * something a human would read, plus the assembly of selected blocks into final text. The ML Kit
 * types themselves stay in `:feature:ocr`, which maps them into the models here — the same split
 * `:core:weather` uses for Open-Meteo's DTOs, and what keeps the ordering algorithm testable on
 * the JVM.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    implementation(libs.javax.inject)
}
