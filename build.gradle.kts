// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serializable) apply false
    alias(libs.plugins.android.library) apply false
    // Applied by the `minion.android.application` convention, not here — declaring them at the
    // root with `apply false` is what puts them on the build script classpath so :app can
    // configure the Crashlytics extension by type.
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}