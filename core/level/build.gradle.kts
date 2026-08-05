/**
 * Tilt geometry — pure Kotlin, zero Android.
 *
 * Owns everything the bubble level computes: the angles a gravity vector implies, the pose machine
 * that decides whether the phone is lying flat or standing on an edge, the filter that makes the
 * reading stable, and the flip-calibration algebra that cancels the device's own bias.
 *
 * All of it lives here rather than in `:feature:level` for a reason specific to this feature: a
 * level has no visible ground truth. A wrong angle looks exactly like a right one, so the only way
 * to know the maths is correct is to prove it against synthesised vectors — which needs a JVM test,
 * which needs a module with no `android.*` in it. `:app` also filters to arm64-v8a, so an x86
 * emulator cannot install the app at all and these tests are the only fast feedback loop there is.
 *
 * Two rules keep that property:
 *  - **Nothing here reads a clock.** Time arrives as a `timestampNanos` parameter and accumulated
 *    state is returned, never held. That is what lets the filter and the pose machine be tested by
 *    folding a list of samples, with no dispatcher and no coroutines.
 *  - **No `kotlinx.coroutines`.** Same as `:core:ocr`: plain functions over immutable values, and
 *    the ViewModel does the folding.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    implementation(libs.javax.inject)
}
