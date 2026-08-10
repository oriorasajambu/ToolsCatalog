package com.minion.scaffold.feature.level.presentation.component

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.minion.scaffold.core.level.usecase.BeepPlan
import com.minion.scaffold.core.level.usecase.PlanBeepUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The parking-sensor beeper: pulses that quicken as the phone approaches level, resolving into a
 * steady tone once it is there.
 *
 * ## Why this lives in the composable rather than the ViewModel
 *
 * Partly convention — `:feature:qrscan` puts its haptics in the composable too. Mostly because it
 * gives the right behaviour for free: audio scoped to the composition stops when the screen goes
 * away, which is exactly what a level's beeper should do. Putting it in the ViewModel would mean
 * writing lifecycle code to reproduce that.
 *
 * The *rhythm* is not decided here. [PlanBeepUseCase] in `:core:level` maps deviation to an
 * interval, unit-tested without an audio device; this only plays what it is told.
 *
 * ## ToneGenerator is sharp on every edge
 *
 * It holds a scarce native `AudioTrack`. Leaking one eventually makes **every subsequent**
 * construction throw, app-wide, until the process dies — so release is handled twice over: on
 * dispose, and on `ON_STOP`, because a backgrounded screen is not disposed. Construction itself
 * throws on some devices when audio resources are unavailable, and blocks for long enough to be
 * visible, so it happens off the main thread inside a `runCatching` that degrades to silence rather
 * than taking the screen down with it.
 */
@Composable
internal fun LevelTone(
    enabled: State<Boolean>,
    deviationDegrees: State<Double>,
) {
    val planBeep = remember { PlanBeepUseCase() }
    val holder = remember { ToneHolder() }

    DisposableEffect(holder) {
        onDispose { holder.release() }
    }

    // A backgrounded screen keeps its composition, so onDispose alone would leave the generator
    // held — and, if it was mid-steady-tone, droning.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        holder.release()
    }

    LaunchedEffect(holder) {
        var wasSteady = false

        while (true) {
            if (!enabled.value) {
                holder.stopSteady()
                wasSteady = false
                delay(IDLE_POLL_MILLIS)
                continue
            }

            val generator = holder.acquire()
            if (generator == null) {
                delay(IDLE_POLL_MILLIS)
                continue
            }

            when (val plan = planBeep(deviationDegrees.value, enabled = true, wasSteady = wasSteady)) {
                BeepPlan.Silent -> {
                    holder.stopSteady()
                    wasSteady = false
                    delay(IDLE_POLL_MILLIS)
                }

                BeepPlan.Steady -> {
                    holder.startSteady()
                    wasSteady = true
                    delay(STEADY_POLL_MILLIS)
                }

                is BeepPlan.Pulse -> {
                    holder.stopSteady()
                    wasSteady = false
                    holder.pulse()
                    // Recomputed from the live deviation on every cycle rather than from a
                    // snapshot, so the rhythm tracks the phone as it moves.
                    delay(plan.intervalMillis)
                }
            }
        }
    }
}

/** Owns the native generator, and every way it can go wrong. */
private class ToneHolder {

    private var generator: ToneGenerator? = null
    private var steady = false
    private var failed = false

    suspend fun acquire(): ToneGenerator? {
        generator?.let { return it }
        if (failed) return null

        // Off the main thread: construction can block for tens of milliseconds, which is a visible
        // hitch at the moment the user taps the toggle.
        return withContext(Dispatchers.IO) {
            runCatching {
                // STREAM_MUSIC, so the volume rocker controls it and it plays over a Bluetooth
                // speaker or headphones — someone up a ladder is the point of this feature.
                // STREAM_SYSTEM would be silenced by Do Not Disturb, and the beeper would look
                // broken to anyone who keeps their phone quiet, which is most people.
                ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
            }.onFailure {
                // Audio being unavailable must not take the level down with it.
                Log.w(TAG, "ToneGenerator unavailable; level audio disabled", it)
                failed = true
            }.getOrNull()
        }?.also { generator = it }
    }

    fun pulse() {
        generator?.startTone(ToneGenerator.TONE_PROP_BEEP, PlanBeepUseCase.TONE_DURATION_MILLIS)
    }

    fun startSteady() {
        if (steady) return
        // No duration: a continuous tone has to be stopped explicitly, which is what stopSteady is
        // for. Forgetting it leaves the phone droning after the user navigates away.
        generator?.startTone(ToneGenerator.TONE_PROP_BEEP)
        steady = true
    }

    fun stopSteady() {
        if (!steady) return
        generator?.stopTone()
        steady = false
    }

    fun release() {
        stopSteady()
        generator?.release()
        generator = null
        // Reset, so re-entering the screen after a failure gets another try — the resource may
        // have been busy rather than absent.
        failed = false
    }
}

private const val TAG = "LevelTone"

/** Out of 100. Loud enough to hear over a worksite, quiet enough not to startle. */
private const val VOLUME = 70

/** How often to re-check while not pulsing. Cheap, and keeps the toggle feeling immediate. */
private const val IDLE_POLL_MILLIS = 150L

/** While holding a steady tone, only the transition out of it matters. */
private const val STEADY_POLL_MILLIS = 100L
