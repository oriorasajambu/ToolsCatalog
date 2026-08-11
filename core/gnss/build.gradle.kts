/**
 * GNSS measurement shaping — pure Kotlin, zero Android.
 *
 * Owns the parts of the speedometer that have a right answer: the geoid conversion that turns a
 * satellite's ellipsoidal height into a height above sea level, the rules that decide when a speed is
 * really zero, the accumulators behind a trip, and every unit conversion.
 *
 * ## Why this is a module rather than a package
 *
 * The same reason as `:core:level` and `:core:sound`, and one more that is specific to this feature.
 * A speedometer has no visible ground truth — a wrong speed looks exactly like a right one — and
 * `:app` filters to arm64-v8a, so an x86 emulator cannot install the app and JVM tests are the only
 * fast feedback loop.
 *
 * The extra reason is the geoid. It is a 508 kB table of published values, and the correctness of
 * every altitude the app shows rests on indexing it correctly. That is exactly the kind of thing that
 * can be checked against the source data at a hundred thousand points in a JVM test and cannot
 * usefully be checked by looking at a phone.
 *
 * ## Rules
 *
 *  - **Nothing here reads a clock.** Elapsed time arrives as a parameter and accumulated state is
 *    returned, never held — which is what lets the trip accumulators be tested by folding a list of
 *    synthetic fixes.
 *  - **No `kotlinx.coroutines`.** Plain functions over values; the ViewModel does the folding.
 *
 * The one thing this module does read is its own JAR resource, `egm96_geoid.bin`, loaded once and
 * kept. See `scripts/generate_geoid.py` for where that file comes from and why it is the size it is.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    implementation(libs.javax.inject)
}
