/**
 * Text and developer utilities: a transform screen and a generator screen.
 *
 * The first feature that is not a QR tool — it touches neither the scanner nor the create spine.
 * All the substance is in `:core:text`; this module is the thin Compose shell over it.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.texttools"
}

dependencies {
    implementation(project(":core:text"))
}
