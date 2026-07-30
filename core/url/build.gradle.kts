/**
 * Web link QR codes. Pure Kotlin, zero Android.
 *
 * Small — a URL QR has no wrapper format, so the payload *is* the URL and the work is validation
 * rather than encoding. It is still its own module for the rule that keeps: one format, one module,
 * and no argument about where the next one belongs.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    implementation(libs.javax.inject)
}
