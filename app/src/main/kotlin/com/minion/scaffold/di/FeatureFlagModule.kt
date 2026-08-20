package com.minion.scaffold.di

import com.minion.scaffold.core.domain.featureflag.FeatureFlagRepository
import com.minion.scaffold.data.RemoteConfigFeatureFlagRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the one repository implementation `:app` owns.
 *
 * This is the exception the module rules allow, not a place to collect feature bindings. A
 * feature's repository is bound by an `internal` module inside that feature; this one is here
 * because its implementation talks to Firebase, and Firebase is application identity — see
 * [RemoteConfigFeatureFlagRepository]. `:feature:tools` sees only the domain interface.
 *
 * `@Binds` on an abstract class rather than a `@Provides` in [FirebaseModule]: the implementation
 * is constructor-injected, so there is nothing to write except the type mapping, and a `@Provides`
 * that just calls a constructor is a copy of the parameter list waiting to go stale.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FeatureFlagModule {

    /**
     * @param impl The Remote Config implementation.
     * @return It, as the domain interface every consumer depends on.
     */
    @Binds
    @Singleton
    abstract fun bindFeatureFlagRepository(
        impl: RemoteConfigFeatureFlagRepository,
    ): FeatureFlagRepository
}
