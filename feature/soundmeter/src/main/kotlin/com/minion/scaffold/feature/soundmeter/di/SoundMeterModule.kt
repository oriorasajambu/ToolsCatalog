package com.minion.scaffold.feature.soundmeter.di

import com.minion.scaffold.feature.soundmeter.data.AudioRecordSource
import com.minion.scaffold.feature.soundmeter.data.local.SoundMeterPreferencesDataStore
import com.minion.scaffold.feature.soundmeter.domain.AudioSource
import com.minion.scaffold.feature.soundmeter.domain.SoundMeterPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** This feature's bindings, `internal` and living beside the implementations they bind. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SoundMeterModule {

    /**
     * `@Singleton` so there is exactly one thing in the process that can open the microphone.
     *
     * The flow is cold, so scoping does not by itself keep a recorder alive — but a second instance
     * would make it possible for two collections to hold the input at once, and "how many things
     * have the microphone open" is a question this feature should never have more than one answer
     * to.
     */
    @Binds
    @Singleton
    abstract fun bindAudioSource(impl: AudioRecordSource): AudioSource

    /**
     * `@Singleton` matters beyond the usual reasons: DataStore throws if a second instance is
     * created for the same file within one process.
     */
    @Binds
    @Singleton
    abstract fun bindSoundMeterPreferencesRepository(
        impl: SoundMeterPreferencesDataStore,
    ): SoundMeterPreferencesRepository
}
