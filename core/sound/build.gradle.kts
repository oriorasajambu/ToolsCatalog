/**
 * Sound level metering — pure Kotlin, zero Android.
 *
 * Owns everything the meter computes: the frequency-weighting filters, the exponential time
 * weighting, the conversion from a block of PCM to a sound pressure level, and the session
 * accumulator behind Leq, min and max.
 *
 * All of it lives here for a sharper version of the reason `:core:level` exists. A level has no
 * visible ground truth; a sound meter has less than none. Gravity is the same everywhere and a flip
 * calibration cancels the device's bias exactly, so a level can at least be made *correct*. A phone
 * reports a digital amplitude and nothing else — the microphone's sensitivity is exposed through no
 * Android API and varies by 10–20 dB between models, and no on-device experiment recovers it.
 *
 * What that leaves is a filter chain that must be provably right even though the number it produces
 * carries an unknown offset. Unlike the rest of this domain, the filters *do* have external ground
 * truth: IEC 61672-1 tabulates the A and C response at every octave centre to a stated tolerance.
 * That table is what the tests assert against, and it is the reason this module is worth separating.
 * `:app` also filters to arm64-v8a, so an x86 emulator cannot install the app and JVM tests are the
 * only fast feedback loop there is.
 *
 * Two rules keep that property, the same two as `:core:level`:
 *  - **Nothing here reads a clock.** Elapsed time arrives as a parameter and accumulated state is
 *    returned, never held. That is what lets the time weighting and the session accumulator be
 *    tested by folding a list of blocks, with no dispatcher and no coroutines.
 *  - **No `kotlinx.coroutines`.** Plain functions over values; the ViewModel does the folding.
 *
 * The one deliberate exception is [com.minion.scaffold.core.sound.usecase.WeightingFilter], which
 * holds its own delay line. Threading four doubles per biquad through a call per *sample* would be
 * unreadable and slow, and a filter's delay line is not the kind of state those rules are about —
 * it is created per capture session, reset explicitly, and never injected.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    implementation(libs.javax.inject)
}
