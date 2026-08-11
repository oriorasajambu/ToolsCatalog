package com.minion.scaffold.feature.soundmeter.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import com.minion.scaffold.feature.soundmeter.domain.AudioBlock
import com.minion.scaffold.feature.soundmeter.domain.AudioSource
import com.minion.scaffold.feature.soundmeter.domain.CaptureEvent
import com.minion.scaffold.feature.soundmeter.domain.CaptureFailure
import com.minion.scaffold.feature.soundmeter.domain.CaptureQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `AudioRecord` bridge.
 *
 * ## The source chain is the whole point
 *
 * Android may apply automatic gain control, noise suppression and echo cancellation to a microphone
 * stream depending on which `AudioSource` was asked for. Every one of those is correct for a phone
 * call and fatal for a measurement: with AGC in the path a sustained loud sound *drops* over the
 * first second, and the meter is reporting the compressor rather than the room. Nothing on screen
 * would look wrong.
 *
 * So the input is opened by preference — `UNPROCESSED`, then `VOICE_RECOGNITION`, then `MIC` — the
 * effects are explicitly disabled on whatever session results, and which rung was reached is
 * reported to the UI. `UNPROCESSED` is gated on the device *advertising* support, because the
 * constant is accepted on devices that quietly ignore it.
 *
 * ## Pinned to the built-in microphone
 *
 * A connected headset would otherwise be chosen, and its sensitivity is nothing like the built-in
 * capsule's — so the calibration offset, which is the only thing standing between a raw amplitude
 * and a claim about the world, would silently stop applying to the device it was set for.
 *
 * ## Nothing is written anywhere
 *
 * Each block is handed straight to the level computation and dropped. No file, no accumulating
 * buffer, nothing leaving the process. That is the commitment the permission rationale makes to the
 * user, and it is one line of policy that has to survive every future edit to this class.
 */
@Singleton
internal class AudioRecordSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AudioSource {

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Suppressed at exactly one place, and the check it is standing in for is the first statement
     * below.
     *
     * Lint cannot follow the permission guard across the `callbackFlow` lambda boundary and into the
     * private helpers, so the alternative would be an inline check it *can* follow — duplicating the
     * guard into the one function that constructs the recorder, where it would be a second thing to
     * keep in step with this one. One check, in the place a reader looks for it, beats two.
     */
    @SuppressLint("MissingPermission")
    override fun capture(): Flow<CaptureEvent> = callbackFlow {
        // The permission can be revoked while the app is running, so this is a live check on every
        // collection rather than something resolved once at construction.
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            send(CaptureEvent.Failed(CaptureFailure.Unavailable))
            close()
            return@callbackFlow
        }

        val session = openSession()
        if (session == null) {
            send(CaptureEvent.Failed(CaptureFailure.Unavailable))
            close()
            return@callbackFlow
        }

        send(CaptureEvent.Started(session.quality, session.sampleRate))

        // Registered before the first read, so a microphone that is *already* being held by another
        // app is reported rather than being mistaken for a very quiet room.
        val recordingCallback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                val own = configs.firstOrNull {
                    it.clientAudioSessionId == session.recorder.audioSessionId
                } ?: return
                trySend(CaptureEvent.Silenced(own.isClientSilenced))
            }
        }
        audioManager?.registerAudioRecordingCallback(recordingCallback, null)

        val reader = launch(ioDispatcher) {
            // The read loop competes with everything else on the device for a deadline it cannot
            // miss without dropping samples, which would shorten the time Leq integrates over.
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            val buffer = ShortArray(BLOCK_SAMPLES)

            while (isActive) {
                val read = session.recorder.read(buffer, 0, BLOCK_SAMPLES)

                if (read <= 0) {
                    // ERROR_INVALID_OPERATION and ERROR_DEAD_OBJECT both mean the recorder is gone
                    // and will not recover on its own. Reporting beats looping on a dead handle.
                    trySend(CaptureEvent.Failed(CaptureFailure.Interrupted))
                    break
                }

                trySend(
                    CaptureEvent.Captured(
                        AudioBlock(
                            samples = buffer.copyOf(read),
                            count = read,
                            sampleRate = session.sampleRate,
                        ),
                    ),
                )
            }
        }

        awaitClose {
            reader.cancel()
            audioManager?.unregisterAudioRecordingCallback(recordingCallback)
            session.release()
        }
    }

    /**
     * Opens the best input this device will give, or null if it will give none.
     *
     * Walks the source preferences against the sample rates, taking the first combination that both
     * constructs and starts. Both checks matter: `AudioRecord` reports `STATE_INITIALIZED` for
     * configurations that then fail to start recording.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun openSession(): CaptureSession? {
        for ((source, quality) in preferredSources()) {
            for (sampleRate in SAMPLE_RATES) {
                val session = tryOpen(source, quality, sampleRate)
                if (session != null) return session
            }
        }
        return null
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun tryOpen(
        source: Int,
        quality: CaptureQuality,
        sampleRate: Int,
    ): CaptureSession? {
        val minimumBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimumBuffer <= 0) return null

        val recorder = runCatching {
            AudioRecord(
                source,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                // 16-bit rather than float, so that clipping is exact. A sample sitting on
                // Short.MAX_VALUE is unambiguously the converter's rail; a float threshold would
                // be a guess about where the rail was, on the one measurement that must not be
                // guessed at.
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimumBuffer, BLOCK_SAMPLES * BYTES_PER_SAMPLE * BUFFERED_BLOCKS),
            )
        }.getOrNull() ?: return null

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }

        preferBuiltInMicrophone(recorder)
        val effects = disableProcessing(recorder.audioSessionId)

        val started = runCatching {
            recorder.startRecording()
            recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
        }.getOrDefault(false)

        if (!started) {
            effects.forEach { it.release() }
            recorder.release()
            return null
        }

        return CaptureSession(recorder, effects, quality, sampleRate)
    }

    /**
     * The chain, best first.
     *
     * `UNPROCESSED` only when [AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED] says so. The
     * constant is accepted regardless, so opening it blind would report the best possible capture
     * quality on exactly the devices least able to deliver it.
     */
    private fun preferredSources(): List<Pair<Int, CaptureQuality>> = buildList {
        val unprocessedSupported = audioManager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            ?.toBoolean() == true

        if (unprocessedSupported) {
            add(MediaRecorder.AudioSource.UNPROCESSED to CaptureQuality.Unprocessed)
        }
        add(MediaRecorder.AudioSource.VOICE_RECOGNITION to CaptureQuality.VoiceRecognition)
        add(MediaRecorder.AudioSource.MIC to CaptureQuality.Processed)
    }

    private fun preferBuiltInMicrophone(recorder: AudioRecord) {
        val builtIn = audioManager
            ?.getDevices(AudioManager.GET_DEVICES_INPUTS)
            ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?: return

        recorder.setPreferredDevice(builtIn)
    }

    /**
     * Switches off gain control, noise suppression and echo cancellation on this session.
     *
     * Belt and braces over the source choice. `VOICE_RECOGNITION` and `MIC` make no promise about
     * processing, and where the platform exposes these as effects it will honour being told to turn
     * them off. Where it does not expose them, `isAvailable` is false and there is nothing to do —
     * which is precisely why the source chain exists as well, rather than instead.
     *
     * The handles are kept and released with the recorder: an effect outliving its session leaks a
     * native object and can leave the effect attached for whoever gets the session id next.
     */
    private fun disableProcessing(sessionId: Int): List<AudioEffect> = buildList {
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(sessionId)?.let {
                it.enabled = false
                add(it)
            }
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(sessionId)?.let {
                it.enabled = false
                add(it)
            }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(sessionId)?.let {
                it.enabled = false
                add(it)
            }
        }
    }

    private class CaptureSession(
        val recorder: AudioRecord,
        private val effects: List<AudioEffect>,
        val quality: CaptureQuality,
        val sampleRate: Int,
    ) {
        fun release() {
            runCatching { recorder.stop() }
            effects.forEach { runCatching { it.release() } }
            recorder.release()
        }
    }

    private companion object {

        /**
         * Preferred first. 48 kHz is what most modern hardware runs natively; 44.1 is the fallback.
         *
         * Which one is granted is decided at runtime, which is why `:core:sound` computes its filter
         * coefficients from the rate rather than carrying a table for one of them.
         */
        val SAMPLE_RATES = intArrayOf(48_000, 44_100)

        /**
         * Samples per block — about 21 ms at 48 kHz.
         *
         * Comfortably shorter than the 125 ms Fast time constant, so the exponential average has
         * roughly six blocks to work with per constant and Fast behaves like Fast. A block long
         * enough to rival the constant would turn the time weighting into a staircase.
         */
        const val BLOCK_SAMPLES = 1024

        const val BYTES_PER_SAMPLE = 2

        /** Headroom in the hardware buffer, so a scheduling hiccup does not drop samples. */
        const val BUFFERED_BLOCKS = 4
    }
}
