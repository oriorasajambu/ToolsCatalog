/**
 * Image container surgery — pure Kotlin, zero Android.
 *
 * Owns the part of the metadata stripper that has a right answer: which bytes of a JPEG, PNG or
 * WebP carry metadata, which bytes carry the image, and exactly what a cleaned file should contain.
 *
 * ## Nothing here touches a file
 *
 * The module takes the bytes of a container and returns a **plan** — a list of byte ranges to copy
 * and short blocks to insert — which the feature executes against real streams. Two things fall out
 * of that, and both are the reason this module exists:
 *
 *  - The whole decision is testable from byte arrays. No fixtures on disk, no emulator, no `Bitmap`.
 *    `:app` filters to arm64-v8a, so an x86 emulator cannot install the app at all and JVM tests are
 *    the only fast feedback loop there is.
 *  - **The pixel-identity guarantee becomes structural rather than a discipline.** The compressed
 *    scan data is only ever named by a `Copy` range, so there is no code path in the app that could
 *    re-encode it. That is the difference between a promise kept by careful review and one kept by
 *    the shape of the types.
 *
 * The same two rules as `:core:sound` and `:core:level` hold: no clock reads, no coroutines.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    implementation(libs.javax.inject)
}
