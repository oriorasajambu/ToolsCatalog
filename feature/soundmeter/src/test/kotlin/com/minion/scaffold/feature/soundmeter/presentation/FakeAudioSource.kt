package com.minion.scaffold.feature.soundmeter.presentation

import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.feature.soundmeter.domain.AudioBlock
import com.minion.scaffold.feature.soundmeter.domain.AudioSource
import com.minion.scaffold.feature.soundmeter.domain.CaptureEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A microphone that emits exactly what a test tells it to.
 *
 * The seam that makes the whole pipeline testable on the JVM: `AudioRecord` does not exist off a
 * device, and `:app` filters to arm64-v8a so there is no emulator to fall back to. [collections]
 * counts how many times the flow has been subscribed, which is how the visibility-gating tests check
 * that the microphone is actually released rather than merely ignored.
 */
internal class FakeAudioSource : AudioSource {

    private val events = MutableSharedFlow<CaptureEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    var collections = 0
        private set

    var active = 0
        private set

    override fun capture(): Flow<CaptureEvent> = flowWithTracking()

    private fun flowWithTracking(): Flow<CaptureEvent> = kotlinx.coroutines.flow.flow {
        collections++
        active++
        try {
            events.collect { emit(it) }
        } finally {
            active--
        }
    }

    suspend fun emit(event: CaptureEvent) = events.emit(event)

    /** A block of a sine at [amplitude] relative to full scale, saturating at the rails. */
    suspend fun emitTone(amplitude: Double, frequencyHz: Double = 1000.0) {
        val samples = ShortArray(BLOCK) { index ->
            val value = amplitude * SoundReference.FULL_SCALE_PCM16 *
                sin(2.0 * PI * frequencyHz * index / SAMPLE_RATE)
            value.roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        emit(CaptureEvent.Captured(AudioBlock(samples, BLOCK, SAMPLE_RATE)))
    }

    suspend fun emitSilence() {
        emit(CaptureEvent.Captured(AudioBlock(ShortArray(BLOCK), BLOCK, SAMPLE_RATE)))
    }

    companion object {
        const val SAMPLE_RATE = 48_000

        /** The same block size the real source reads — about 21 ms. */
        const val BLOCK = 1024
    }
}
