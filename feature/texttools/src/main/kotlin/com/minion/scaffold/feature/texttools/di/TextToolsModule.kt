package com.minion.scaffold.feature.texttools.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import java.util.Random

/**
 * Binds the random source the generator draws from.
 *
 * A `SecureRandom`, never a plain `Random`. `GenerateTextUseCase` depends on the `Random` supertype
 * so a test can substitute a seeded instance and assert on deterministic output — but the only
 * binding production ever sees is the cryptographic one. A password from a predictable generator is
 * not a password.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TextToolsModule {

    @Provides
    fun provideRandom(): Random = SecureRandom()
}
