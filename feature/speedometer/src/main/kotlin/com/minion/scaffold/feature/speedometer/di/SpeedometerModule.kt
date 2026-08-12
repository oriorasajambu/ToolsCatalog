package com.minion.scaffold.feature.speedometer.di

import com.minion.scaffold.feature.speedometer.data.BarometricRateOfClimbSource
import com.minion.scaffold.feature.speedometer.data.GnssLocationSource
import com.minion.scaffold.feature.speedometer.data.GnssSatelliteStatusSource
import com.minion.scaffold.feature.speedometer.data.local.SpeedometerPreferencesDataStore
import com.minion.scaffold.feature.speedometer.domain.LocationSource
import com.minion.scaffold.feature.speedometer.domain.RateOfClimbSource
import com.minion.scaffold.feature.speedometer.domain.SatelliteStatusSource
import com.minion.scaffold.feature.speedometer.domain.SpeedometerPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** This feature's bindings, `internal` and living beside the implementations they bind. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SpeedometerModule {

    /**
     * `@Singleton` so there is one thing in the process that can hold the receiver open.
     *
     * The flows are cold, so scoping does not by itself keep anything alive — but at 1 Hz, "how many
     * things have GNSS running" should never have more than one answer.
     */
    @Binds
    @Singleton
    abstract fun bindLocationSource(impl: GnssLocationSource): LocationSource

    @Binds
    @Singleton
    abstract fun bindSatelliteStatusSource(impl: GnssSatelliteStatusSource): SatelliteStatusSource

    @Binds
    @Singleton
    abstract fun bindRateOfClimbSource(impl: BarometricRateOfClimbSource): RateOfClimbSource

    /** `@Singleton` matters beyond the usual reasons: DataStore throws on a second instance. */
    @Binds
    @Singleton
    abstract fun bindSpeedometerPreferencesRepository(
        impl: SpeedometerPreferencesDataStore,
    ): SpeedometerPreferencesRepository
}
