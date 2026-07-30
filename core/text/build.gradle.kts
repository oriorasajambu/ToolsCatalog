/**
 * Text and developer transforms — encoding, hashing, case conversion, generation. Pure Kotlin.
 *
 * The substance is the edge cases: Base64 that reads URL-safe and unpadded input, URL encoding
 * where a space is `%20` and not `+`, JSON that keeps its key order, and a password generator that
 * must draw from a cryptographic source. Each of those, done the obvious way, is subtly wrong.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    // For JSON prettify/minify via JsonElement — no @Serializable types, just the runtime.
    implementation(libs.kotlin.serializable)
    implementation(libs.javax.inject)
}
