package com.minion.scaffold.feature.soundmeter.presentation

import com.minion.scaffold.core.sound.model.TimeWeighting
import com.minion.scaffold.core.sound.model.Weighting
import com.minion.scaffold.core.sound.usecase.AccumulateSessionUseCase
import com.minion.scaffold.core.sound.usecase.ApplyTimeWeightingUseCase
import com.minion.scaffold.core.sound.usecase.ComputeBlockLevelUseCase
import com.minion.scaffold.core.sound.usecase.WeightingFilterFactory
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.feature.soundmeter.domain.CaptureEvent
import com.minion.scaffold.feature.soundmeter.domain.CaptureFailure
import com.minion.scaffold.feature.soundmeter.domain.CaptureQuality
import com.minion.scaffold.feature.soundmeter.domain.ObserveSoundPreferencesUseCase
import com.minion.scaffold.feature.soundmeter.domain.SetTimeWeightingUseCase
import com.minion.scaffold.feature.soundmeter.domain.SetWeightingUseCase
import com.minion.scaffold.feature.soundmeter.domain.SoundMeterPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class SoundMeterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val audioSource = FakeAudioSource()
    private val preferences = FakePreferences()

    private fun viewModel() = SoundMeterViewModel(
        audioSource = audioSource,
        filterFactory = WeightingFilterFactory(),
        computeBlockLevel = ComputeBlockLevelUseCase(),
        applyTimeWeighting = ApplyTimeWeightingUseCase(),
        accumulateSession = AccumulateSessionUseCase(),
        observeSoundPreferences = ObserveSoundPreferencesUseCase(preferences),
        setWeighting = SetWeightingUseCase(preferences),
        setTimeWeighting = SetTimeWeightingUseCase(preferences),
    )

    /**
     * The microphone is not opened until the screen is on show.
     *
     * The counterpart to the bug that bit `:feature:level`, where the sensor stayed registered after
     * backgrounding because the ViewModel collected in `init`. There it cost battery; here it would
     * mean the app holding an open microphone while the phone sat in a pocket.
     */
    @Test
    fun `no capture until the screen resumes`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(
            SoundMeterIntent.PermissionResult(granted = true, shouldShowRationale = false),
        )
        advanceUntilIdle()

        assertEquals(0, audioSource.collections)

        viewModel.onIntent(SoundMeterIntent.ScreenResumed)
        advanceUntilIdle()

        assertEquals(1, audioSource.collections)
        assertEquals(1, audioSource.active)
    }

    /** And it is released again on pause, not merely ignored. */
    @Test
    fun `pausing releases the capture`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(
            SoundMeterIntent.PermissionResult(granted = true, shouldShowRationale = false),
        )
        viewModel.onIntent(SoundMeterIntent.ScreenResumed)
        advanceUntilIdle()

        viewModel.onIntent(SoundMeterIntent.ScreenPaused)
        advanceUntilIdle()

        assertEquals(0, audioSource.active)
    }

    @Test
    fun `a tone produces a level`() = runTest {
        val viewModel = resumed()
        audioSource.emitTone(amplitude = 0.05)
        advanceUntilIdle()

        val reading = viewModel.state.value.reading
        assertTrue("expected a level, was $reading", reading is SoundMeterState.Reading.Level)
    }

    /**
     * Statistics accumulate only between Start and Stop.
     *
     * The live gauge runs the whole time the screen is open, so without this the session figures
     * would silently include however long the app happened to be sitting on that screen before
     * anyone decided to measure anything.
     */
    @Test
    fun `statistics do not accumulate before Start`() = runTest {
        val viewModel = resumed()
        repeat(20) { audioSource.emitTone(amplitude = 0.05) }
        advanceUntilIdle()

        assertNull(viewModel.state.value.stats.leqDbSpl)
        assertEquals(0.0, viewModel.state.value.stats.durationSeconds, 0.0)
    }

    @Test
    fun `statistics accumulate after Start and freeze after Stop`() = runTest {
        val viewModel = resumed()
        viewModel.onIntent(SoundMeterIntent.StartPressed)

        repeat(20) { audioSource.emitTone(amplitude = 0.05) }
        advanceUntilIdle()

        val whileRunning = viewModel.state.value.stats
        assertTrue("expected a measurement", whileRunning.hasMeasurement)

        viewModel.onIntent(SoundMeterIntent.StopPressed)
        repeat(20) { audioSource.emitTone(amplitude = 0.05) }
        advanceUntilIdle()

        assertEquals(
            whileRunning.durationSeconds,
            viewModel.state.value.stats.durationSeconds,
            0.0,
        )
    }

    @Test
    fun `Reset clears the statistics`() = runTest {
        val viewModel = resumed()
        viewModel.onIntent(SoundMeterIntent.StartPressed)
        repeat(20) { audioSource.emitTone(amplitude = 0.05) }
        advanceUntilIdle()

        viewModel.onIntent(SoundMeterIntent.ResetPressed)
        advanceUntilIdle()

        assertNull(viewModel.state.value.stats.leqDbSpl)
        assertTrue(viewModel.state.value.history.isEmpty())
    }

    /**
     * **The most important test in this file.**
     *
     * While another app holds the microphone the system hands over valid-looking silence. Folding
     * that in would drag Leq down for the whole session, and — worse — the meter would keep showing
     * a confident number for a room it cannot hear. So blocks arriving while silenced move nothing
     * at all.
     */
    @Test
    fun `blocks arriving while silenced are discarded`() = runTest {
        val viewModel = resumed()
        viewModel.onIntent(SoundMeterIntent.StartPressed)
        repeat(20) { audioSource.emitTone(amplitude = 0.05) }
        advanceUntilIdle()

        val before = viewModel.state.value.stats

        audioSource.emit(CaptureEvent.Silenced(silenced = true))
        repeat(40) { audioSource.emitSilence() }
        advanceUntilIdle()

        val after = viewModel.state.value.stats
        assertTrue(viewModel.state.value.silenced)
        assertEquals(before.durationSeconds, after.durationSeconds, 0.0)
        assertEquals(before.leqDbSpl!!, after.leqDbSpl!!, 1e-9)
    }

    @Test
    fun `the reading recovers when the microphone comes back`() = runTest {
        val viewModel = resumed()
        audioSource.emit(CaptureEvent.Silenced(silenced = true))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.silenced)

        audioSource.emit(CaptureEvent.Silenced(silenced = false))
        repeat(5) { audioSource.emitTone(amplitude = 0.05) }
        advanceUntilIdle()

        assertFalse(viewModel.state.value.silenced)
        assertTrue(viewModel.state.value.reading is SoundMeterState.Reading.Level)
    }

    /**
     * One stray clipped block does not flash the readout.
     *
     * Blocks arrive every 21 ms, so without a dwell a single sample touching the rail would replace
     * the whole reading for a frame. Asymmetric by design — see the ViewModel.
     */
    @Test
    fun `a single clipped block does not latch too loud`() = runTest {
        val viewModel = resumed()
        repeat(20) { audioSource.emitTone(amplitude = 0.05) }
        advanceUntilIdle()

        audioSource.emitTone(amplitude = 1.5)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.reading is SoundMeterState.Reading.Level)
    }

    @Test
    fun `sustained clipping latches too loud`() = runTest {
        val viewModel = resumed()
        repeat(20) { audioSource.emitTone(amplitude = 0.05) }
        repeat(5) { audioSource.emitTone(amplitude = 1.5) }
        advanceUntilIdle()

        assertEquals(SoundMeterState.Reading.TooLoud, viewModel.state.value.reading)
    }

    /**
     * A clipped stretch does not become the session maximum.
     *
     * The inversion this whole feature guards against: a clipped block's level is wrong *downwards*,
     * so taking it at face value would record the loudest moment of a session as something
     * unremarkable.
     */
    @Test
    fun `clipping does not set the maximum`() = runTest {
        val viewModel = resumed()
        viewModel.onIntent(SoundMeterIntent.StartPressed)
        repeat(20) { audioSource.emitTone(amplitude = 0.05) }
        advanceUntilIdle()

        val quietMax = viewModel.state.value.stats.maxDbSpl!!

        repeat(20) { audioSource.emitTone(amplitude = 1.5) }
        advanceUntilIdle()

        val stats = viewModel.state.value.stats
        assertEquals(quietMax, stats.maxDbSpl!!, 1e-9)
        assertTrue("clipped time should be recorded", stats.unmeasurableSeconds > 0.0)
    }

    @Test
    fun `a failed capture is surfaced rather than left looking live`() = runTest {
        val viewModel = resumed()
        audioSource.emit(CaptureEvent.Failed(CaptureFailure.Interrupted))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(CaptureFailure.Interrupted, state.failure)
        assertFalse(state.capturing)
        assertFalse(state.canMeasure)
        assertEquals(SoundMeterState.Reading.Idle, state.reading)
    }

    /**
     * Without permission the microphone is never opened at all.
     *
     * Found on a device: the capture was attempted anyway, failed for want of permission, and the
     * status line rendered that as "the microphone stopped responding" — directly beneath the card
     * explaining that access had been denied. Two contradictory accounts of one situation, and the
     * more prominent one was wrong. Not opening it is also the honest behaviour: there is nothing to
     * try.
     */
    @Test
    fun `a denied permission never opens the microphone`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(
            SoundMeterIntent.PermissionResult(granted = false, shouldShowRationale = true),
        )
        viewModel.onIntent(SoundMeterIntent.ScreenResumed)
        advanceUntilIdle()

        assertEquals(0, audioSource.collections)
        assertNull(viewModel.state.value.failure)
    }

    /**
     * And the gauge does not claim to be listening while it is not.
     *
     * "Listening…" under a refused-permission card is a small lie, and the sort that costs the tool
     * credibility on every other number it shows.
     */
    @Test
    fun `a denied permission leaves the readout idle, not listening`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(SoundMeterIntent.ScreenResumed)
        viewModel.onIntent(
            SoundMeterIntent.PermissionResult(granted = false, shouldShowRationale = true),
        )
        advanceUntilIdle()

        assertEquals(SoundMeterState.Reading.Idle, viewModel.state.value.reading)
    }

    /** Granting after a refusal opens the microphone without needing the screen to be revisited. */
    @Test
    fun `granting after a refusal opens the microphone`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(
            SoundMeterIntent.PermissionResult(granted = false, shouldShowRationale = true),
        )
        viewModel.onIntent(SoundMeterIntent.ScreenResumed)
        advanceUntilIdle()
        assertEquals(0, audioSource.collections)

        viewModel.onIntent(
            SoundMeterIntent.PermissionResult(granted = true, shouldShowRationale = false),
        )
        advanceUntilIdle()

        assertEquals(1, audioSource.collections)
    }

    @Test
    fun `changing the weighting rebuilds the filter and keeps measuring`() = runTest {
        val viewModel = resumed()
        repeat(10) { audioSource.emitTone(amplitude = 0.05, frequencyHz = 63.0) }
        advanceUntilIdle()
        val aWeighted = (viewModel.state.value.reading as SoundMeterState.Reading.Level).dbSpl

        viewModel.onIntent(SoundMeterIntent.WeightingChanged(Weighting.C))
        advanceUntilIdle()
        repeat(60) { audioSource.emitTone(amplitude = 0.05, frequencyHz = 63.0) }
        advanceUntilIdle()
        val cWeighted = (viewModel.state.value.reading as SoundMeterState.Reading.Level).dbSpl

        // C is flat where A is 26 dB down, so at 63 Hz the switch has to move the number a long way.
        // If the filter were not rebuilt, these would be identical.
        assertTrue("C ($cWeighted) should far exceed A ($aWeighted)", cWeighted - aWeighted > 15.0)
    }

    @Test
    fun `the permission result resolves to a state`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(
            SoundMeterIntent.PermissionResult(granted = false, shouldShowRationale = false),
        )
        advanceUntilIdle()

        assertEquals(PermissionState.PermanentlyDenied, viewModel.state.value.permission)
    }

    /**
     * A ViewModel with permission, on screen, and with the capture already open.
     *
     * The `advanceUntilIdle` before the first emission is load-bearing rather than defensive: the
     * fake publishes through a `MutableSharedFlow` with no replay, so anything emitted before the
     * ViewModel's collector has actually subscribed is dropped on the floor — and a test that
     * asserts "nothing happened" would then pass for entirely the wrong reason.
     */
    private suspend fun TestScope.resumed(): SoundMeterViewModel {
        val viewModel = viewModel()
        viewModel.onIntent(
            SoundMeterIntent.PermissionResult(granted = true, shouldShowRationale = false),
        )
        viewModel.onIntent(SoundMeterIntent.ScreenResumed)
        advanceUntilIdle()

        audioSource.emit(
            CaptureEvent.Started(CaptureQuality.Unprocessed, FakeAudioSource.SAMPLE_RATE),
        )
        advanceUntilIdle()
        return viewModel
    }

    /** In-memory preferences — the real one needs DataStore, which needs a `Context`. */
    private class FakePreferences : SoundMeterPreferencesRepository {
        private val offset = MutableStateFlow(0.0)
        private val weight = MutableStateFlow(Weighting.A)
        private val timeWeight = MutableStateFlow(TimeWeighting.Fast)

        override val offsetDb: Flow<Double> = offset
        override val weighting: Flow<Weighting> = weight
        override val timeWeighting: Flow<TimeWeighting> = timeWeight

        override suspend fun setOffsetDb(offsetDb: Double) { offset.value = offsetDb }
        override suspend fun setWeighting(weighting: Weighting) { weight.value = weighting }
        override suspend fun setTimeWeighting(timeWeighting: TimeWeighting) {
            timeWeight.value = timeWeighting
        }
    }
}
