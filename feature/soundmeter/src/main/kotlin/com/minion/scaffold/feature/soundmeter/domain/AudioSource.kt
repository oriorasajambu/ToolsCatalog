package com.minion.scaffold.feature.soundmeter.domain

import kotlinx.coroutines.flow.Flow

/**
 * Where blocks of audio come from.
 *
 * An interface so the ViewModel is testable: the real implementation needs `AudioRecord`, which does
 * not exist in a JVM unit test. The same seam, and the same reason, as `:feature:level`'s
 * `GravitySource`.
 */
internal interface AudioSource {

    /**
     * Captures, for as long as this is collected.
     *
     * Cold. The recorder is opened when collection starts and released when it stops, so nothing
     * holds the microphone while the screen is away — which here is a privacy property rather than
     * a battery one, and is why the ViewModel gates collection on visibility rather than collecting
     * for its own lifetime.
     */
    fun capture(): Flow<CaptureEvent>
}

/**
 * What the capture has to say for itself.
 *
 * Events rather than a bare stream of blocks, because two of the three things the screen needs to
 * know are not levels. How the input was opened decides whether the reading can be trusted at all,
 * and losing the microphone mid-session has to be *visible* — a meter that keeps drawing a gauge
 * while another app holds the mic is reporting a library when it is standing in a nightclub.
 */
internal sealed interface CaptureEvent {

    /** The recorder opened. Carries what the negotiation actually settled on. */
    data class Started(val quality: CaptureQuality, val sampleRate: Int) : CaptureEvent

    data class Captured(val block: AudioBlock) : CaptureEvent

    /**
     * The system is feeding this app silence while another client holds the microphone.
     *
     * The dangerous case, and the one that read codes never see: the recorder is healthy, the reads
     * succeed, and every sample is zero. Without this the meter would confidently report the noise
     * floor. Reported as a toggle rather than a failure because it resolves on its own when the
     * other app finishes.
     */
    data class Silenced(val silenced: Boolean) : CaptureEvent

    /** The capture could not start, or could not continue. */
    data class Failed(val reason: CaptureFailure) : CaptureEvent
}

internal enum class CaptureFailure {

    /** No input device, or the recorder refused to initialise. */
    Unavailable,

    /** The read loop returned an error — usually the recorder being torn out from underneath. */
    Interrupted,
}

/**
 * One buffer of captured audio.
 *
 * [samples] is a fresh array per block rather than a reused one. Reuse would save roughly 100 KB a
 * second of allocation, which is not worth the contract it would impose: any buffering anywhere
 * downstream — a `buffer()`, a slow collector, a test that collects into a list — would silently
 * alias every block to the same array and produce readings that were plausible and wrong.
 */
internal class AudioBlock(
    val samples: ShortArray,
    val count: Int,
    val sampleRate: Int,
) {
    /** How much time this block covers — what the time weighting and Leq integrate over. */
    val seconds: Double get() = count.toDouble() / sampleRate
}

/**
 * How the input was opened, and therefore how much the reading can be trusted.
 *
 * Surfaced on screen for the same reason `:feature:level` says when it is running on a raw
 * accelerometer: the difference is invisible in the number, and a meter that silently measures the
 * automatic gain control instead of the room is the worst bug this feature can have. With AGC in the
 * path a loud sound gets *quieter* after a second, which looks exactly like a sound that got
 * quieter.
 */
internal enum class CaptureQuality {

    /**
     * `UNPROCESSED` — the device declared a signal path with no processing applied.
     *
     * The only mode where the reading is a measurement of the room rather than of the room plus
     * whatever the platform decided to do to it. Gated on the device advertising support, because
     * the constant is accepted on devices that do not honour it.
     */
    Unprocessed,

    /**
     * `VOICE_RECOGNITION` — conventionally free of gain control, but only conventionally.
     *
     * Chosen over plain `MIC` because recognisers need an uncompressed signal, so OEMs generally
     * leave this path alone. "Generally" is doing real work in that sentence, which is why it is a
     * fallback and why it is named on screen.
     */
    VoiceRecognition,

    /** Plain `MIC`. Most likely to have processing applied; the reading may be compressed. */
    Processed,

    /** No usable input. The tool explains itself rather than showing a gauge stuck at zero. */
    Unavailable,
}
