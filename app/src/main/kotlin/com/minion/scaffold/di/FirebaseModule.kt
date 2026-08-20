package com.minion.scaffold.di

import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.minion.scaffold.BuildConfig
import com.minion.scaffold.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The Firebase singletons, bound so nothing else has to reach for `Firebase.analytics` directly.
 *
 * Every one of these is a global the SDK already caches, so binding them buys no lifecycle
 * management — it buys a seam. A class that takes [FirebaseAnalytics] as a constructor parameter
 * can be unit-tested against a MockK relaxed mock; one that calls `Firebase.analytics` inside a
 * method needs an initialised `FirebaseApp` and therefore an emulator.
 *
 * This lives in `:app` rather than a core module because Firebase is configured by
 * `app/google-services.json` — which app this is, not a capability a library can own.
 *
 * Note that none of this initialises Firebase. `FirebaseApp` is started before
 * [com.minion.scaffold.AppApplication.onCreate] by the SDK's own `androidx.startup` provider,
 * reading the resources the `google-services` Gradle plugin generated from
 * `app/google-services.json`.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Analytics, for screen views and custom events.
     *
     * Collection is switched off in debug builds by a `<meta-data>` in the debug manifest, so this
     * instance is a no-op there rather than a second source of production numbers.
     *
     * @return The process-wide [FirebaseAnalytics].
     */
    @Provides
    @Singleton
    fun provideFirebaseAnalytics(): FirebaseAnalytics = Firebase.analytics

    /**
     * Crashlytics, for recording non-fatal failures alongside the crashes it catches on its own.
     *
     * Uncaught exceptions need nothing from this binding — the SDK installs its own
     * `Thread.UncaughtExceptionHandler` at startup. Inject it to call `recordException` on an
     * error the app handles but should still know the rate of.
     *
     * @return The process-wide [FirebaseCrashlytics].
     */
    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics = Firebase.crashlytics

    /**
     * Remote Config, with its in-app defaults already applied.
     *
     * `setDefaultsAsync` is called here rather than at each call site so no code path can read a
     * key before the defaults land; `R.xml.remote_config_defaults` is the single list of what the
     * app expects. Nothing is fetched — a fetch is a network call and belongs to whatever screen
     * or work actually needs a value, not to graph construction.
     *
     * @return The process-wide [FirebaseRemoteConfig], seeded with the XML defaults.
     */
    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig = Firebase.remoteConfig.apply {
        setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds =
                    if (BuildConfig.DEBUG) DEBUG_FETCH_INTERVAL.inWholeSeconds
                    else PRODUCTION_FETCH_INTERVAL.inWholeSeconds
            },
        )
        setDefaultsAsync(R.xml.remote_config_defaults)
    }
}

/**
 * How stale a cached Remote Config fetch may be before `fetchAndActivate()` goes to the network.
 * Firebase throttles a client that fetches more often than this; 12 hours is the SDK's own default.
 */
private val PRODUCTION_FETCH_INTERVAL = 12.hours

/**
 * The debug counterpart: short enough that a value changed in the console shows up on the next
 * launch instead of tomorrow. Not zero — that trips the SDK's throttle after a handful of fetches.
 */
private val DEBUG_FETCH_INTERVAL = 1.minutes
