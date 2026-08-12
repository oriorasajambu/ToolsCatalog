package com.minion.scaffold.feature.speedometer.presentation

import com.minion.scaffold.core.gnss.geoid.GeoidModel
import com.minion.scaffold.core.gnss.model.CoordinateFormat
import com.minion.scaffold.core.gnss.model.DistanceUnit
import com.minion.scaffold.core.gnss.model.GnssFix
import com.minion.scaffold.core.gnss.model.SpeedUnit
import com.minion.scaffold.core.gnss.usecase.AccumulateTripUseCase
import com.minion.scaffold.core.gnss.usecase.ClassifyFixQualityUseCase
import com.minion.scaffold.core.gnss.usecase.ResolveSpeedUseCase
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.feature.speedometer.domain.LocationEvent
import com.minion.scaffold.feature.speedometer.domain.LocationSource
import com.minion.scaffold.feature.speedometer.domain.ObserveSpeedometerPreferencesUseCase
import com.minion.scaffold.feature.speedometer.domain.RateOfClimbSource
import com.minion.scaffold.feature.speedometer.domain.SatelliteStatus
import com.minion.scaffold.feature.speedometer.domain.SatelliteStatusSource
import com.minion.scaffold.feature.speedometer.domain.SpeedometerPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class SpeedometerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val locationSource = FakeLocationSource()
    private val satelliteSource = FakeSatelliteSource()
    private val preferences = FakePreferences()

    private fun viewModel() = SpeedometerViewModel(
        locationSource = locationSource,
        satelliteStatusSource = satelliteSource,
        rateOfClimbSource = object : RateOfClimbSource {
            override fun ratePerMinute(): Flow<Double> = flow { }
        },
        geoid = GeoidModel(),
        resolveSpeed = ResolveSpeedUseCase(),
        accumulateTrip = AccumulateTripUseCase(),
        classifyFixQuality = ClassifyFixQualityUseCase(),
        observePreferences = ObserveSpeedometerPreferencesUseCase(preferences),
    )

    /**
     * **The subtle permission case.**
     *
     * Approximate location is a deliberate user choice in the system dialog, not a denial. Treating
     * it as one would tell somebody who granted something that they granted nothing — and the
     * recovery is different: a re-request for precise, not a trip to Settings.
     */
    @Test
    fun `a coarse-only grant is distinct from a denial`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(
            SpeedometerIntent.PermissionResult(
                fineGranted = false,
                coarseGranted = true,
                shouldShowRationale = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(
            SpeedometerState.LocationAccess.Approximate,
            viewModel.state.value.access,
        )
        assertFalse(viewModel.state.value.canMeasure)
    }

    /**
     * And the receiver is never opened on that grant.
     *
     * Approximate positions are re-coarsened per request, so they jump around a grid rather than
     * tracing a path. Feeding those into the pipeline would produce speeds that are not merely
     * imprecise but meaningless, so the source is never started at all.
     */
    @Test
    fun `an approximate grant never opens the receiver`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(
            SpeedometerIntent.PermissionResult(false, coarseGranted = true, shouldShowRationale = true),
        )
        viewModel.onIntent(SpeedometerIntent.ScreenResumed)
        advanceUntilIdle()

        assertEquals(0, locationSource.collections)
    }

    @Test
    fun `a denial and a permanent denial are distinguished`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(SpeedometerIntent.PermissionResult(false, false, shouldShowRationale = true))
        advanceUntilIdle()
        assertEquals(SpeedometerState.LocationAccess.Denied, viewModel.state.value.access)

        viewModel.onIntent(SpeedometerIntent.PermissionResult(false, false, shouldShowRationale = false))
        advanceUntilIdle()
        assertEquals(
            SpeedometerState.LocationAccess.PermanentlyDenied,
            viewModel.state.value.access,
        )
    }

    /**
     * The receiver follows the screen, not the ViewModel's lifetime.
     *
     * The bug that bit `:feature:level` and was verified fixed in `:feature:soundmeter`. At 1 Hz a
     * GNSS stream is expensive, and `viewModelScope` outlives the screen.
     */
    @Test
    fun `the receiver opens on resume and closes on pause`() = runTest {
        val viewModel = granted()
        advanceUntilIdle()
        assertEquals(0, locationSource.collections)

        viewModel.onIntent(SpeedometerIntent.ScreenResumed)
        advanceUntilIdle()
        assertEquals(1, locationSource.collections)
        assertEquals(1, locationSource.active)

        viewModel.onIntent(SpeedometerIntent.ScreenPaused)
        advanceUntilIdle()
        assertEquals(0, locationSource.active)
    }

    /**
     * Searching is not stationary.
     *
     * Before any fix the screen must not show a confident zero — those are entirely different
     * situations, and a speedometer reading 0 during a cold start is precisely the kind of small lie
     * this app's tools are built to avoid.
     */
    @Test
    fun `with no fix the reading is searching rather than zero`() = runTest {
        val viewModel = resumed()

        assertEquals(SpeedometerState.Reading.Searching, viewModel.state.value.reading)
    }

    @Test
    fun `a fix produces a live reading with a sea-level altitude`() = runTest {
        val viewModel = resumed()

        repeat(4) { index -> locationSource.emit(fix(speed = 20.0, elapsedNanos = index * 1_000_000_000L)) }
        advanceUntilIdle()

        val live = viewModel.state.value.reading as SpeedometerState.Reading.Live
        assertEquals(20.0, live.speedMetersPerSecond, 0.01)
        assertFalse(live.speedDerived)
        // Medan's geoid separation is about -16.4 m, so an ellipsoidal 25 m is roughly 41 m MSL.
        assertEquals(41.4, live.altitudeMeters!!, 1.0)
    }

    /** A mock fix is measured and marked, never silently trusted. */
    @Test
    fun `a mock provider raises the warning without stopping the readout`() = runTest {
        val viewModel = resumed()

        repeat(4) { index ->
            locationSource.emit(
                fix(speed = 20.0, elapsedNanos = index * 1_000_000_000L, mock = true),
            )
        }
        advanceUntilIdle()

        assertTrue(viewModel.state.value.mocked)
        assertTrue(viewModel.state.value.reading is SpeedometerState.Reading.Live)
    }

    @Test
    fun `location switched off is reported and recovers`() = runTest {
        val viewModel = resumed()

        locationSource.emit(LocationEvent.ProviderDisabled)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.providerEnabled)
        assertFalse(viewModel.state.value.canMeasure)

        locationSource.emit(LocationEvent.ProviderEnabled)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.providerEnabled)
    }

    @Test
    fun `the trip accumulates only between start and stop`() = runTest {
        val viewModel = resumed()

        repeat(20) { index -> locationSource.emit(fix(20.0, index * 1_000_000_000L)) }
        advanceUntilIdle()
        assertFalse(viewModel.state.value.trip.hasMeasurement)

        viewModel.onIntent(SpeedometerIntent.StartPressed)
        repeat(20) { index -> locationSource.emit(fix(20.0, (20 + index) * 1_000_000_000L)) }
        advanceUntilIdle()
        val running = viewModel.state.value.trip
        assertTrue(running.hasMeasurement)

        viewModel.onIntent(SpeedometerIntent.StopPressed)
        repeat(20) { index -> locationSource.emit(fix(20.0, (40 + index) * 1_000_000_000L)) }
        advanceUntilIdle()

        assertEquals(
            running.distanceMeters,
            viewModel.state.value.trip.distanceMeters,
            0.0,
        )
    }

    @Test
    fun `reset clears the trip`() = runTest {
        val viewModel = resumed()
        viewModel.onIntent(SpeedometerIntent.StartPressed)
        repeat(20) { index -> locationSource.emit(fix(20.0, index * 1_000_000_000L)) }
        advanceUntilIdle()

        viewModel.onIntent(SpeedometerIntent.ResetPressed)
        advanceUntilIdle()

        assertEquals(0.0, viewModel.state.value.trip.distanceMeters, 0.0)
    }

    // region Helpers

    private suspend fun TestScope.granted(): SpeedometerViewModel {
        val viewModel = viewModel()
        viewModel.onIntent(
            SpeedometerIntent.PermissionResult(true, coarseGranted = true, shouldShowRationale = false),
        )
        advanceUntilIdle()
        return viewModel
    }

    private suspend fun TestScope.resumed(): SpeedometerViewModel {
        val viewModel = granted()
        viewModel.onIntent(SpeedometerIntent.ScreenResumed)
        advanceUntilIdle()
        return viewModel
    }

    private fun fix(speed: Double, elapsedNanos: Long, mock: Boolean = false) = LocationEvent.Fix(
        GnssFix(
            latitude = 3.5952,
            longitude = 98.6722,
            ellipsoidalAltitudeMeters = 25.0,
            speedMetersPerSecond = speed,
            speedAccuracyMetersPerSecond = 0.3,
            horizontalAccuracyMeters = 6.0,
            verticalAccuracyMeters = 12.0,
            elapsedRealtimeNanos = elapsedNanos,
            fromMockProvider = mock,
        ),
    )

    /** Counts subscriptions, which is how the visibility-gating tests prove the receiver closed. */
    private class FakeLocationSource : LocationSource {
        private val events = MutableSharedFlow<LocationEvent>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )

        var collections = 0
            private set
        var active = 0
            private set

        override fun fixes(): Flow<LocationEvent> = flow {
            collections++
            active++
            try {
                events.collect { emit(it) }
            } finally {
                active--
            }
        }

        suspend fun emit(event: LocationEvent) = events.emit(event)
    }

    private class FakeSatelliteSource : SatelliteStatusSource {
        override fun status(): Flow<SatelliteStatus> = flow { }
    }

    private class FakePreferences : SpeedometerPreferencesRepository {
        private val speed = MutableStateFlow(SpeedUnit.KilometersPerHour)
        private val distance = MutableStateFlow(DistanceUnit.Metric)
        private val coordinates = MutableStateFlow(CoordinateFormat.Decimal)

        override val speedUnit: Flow<SpeedUnit> = speed
        override val distanceUnit: Flow<DistanceUnit> = distance
        override val coordinateFormat: Flow<CoordinateFormat> = coordinates

        override suspend fun setSpeedUnit(unit: SpeedUnit) { speed.value = unit }
        override suspend fun setDistanceUnit(unit: DistanceUnit) { distance.value = unit }
        override suspend fun setCoordinateFormat(format: CoordinateFormat) {
            coordinates.value = format
        }
    }

    // endregion
}
