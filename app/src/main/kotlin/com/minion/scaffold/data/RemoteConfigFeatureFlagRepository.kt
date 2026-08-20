package com.minion.scaffold.data

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.minion.scaffold.core.domain.featureflag.FeatureFlagRepository
import com.minion.scaffold.core.domain.featureflag.FeatureFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FeatureFlagRepository] backed by Firebase Remote Config.
 *
 * Lives in `:app` rather than a core module because Remote Config is configured by
 * `app/google-services.json`, which is application identity. A feature that reads a flag depends
 * on the domain interface and never learns that Firebase is behind it.
 *
 * **The key naming rule is here and nowhere else.** A feature id from the tool catalog is
 * kebab-case (`sound-meter`); a Remote Config parameter key may only hold letters, digits and
 * underscores, so it becomes `feature_sound_meter_enabled`. Every key is also written out
 * literally in `res/xml/remote_config_defaults.xml`, which is what makes the set discoverable
 * without reading this function — keep the two in step when a tool is added.
 */
@Singleton
internal class RemoteConfigFeatureFlagRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) : FeatureFlagRepository {

    override fun flags(): Flow<FeatureFlags> = flow {
        // What is already known, before any network call: the XML defaults on a first run, the
        // configuration activated on a previous run afterwards. Emitted first so the home screen
        // draws a full catalog immediately rather than flashing an empty one.
        emit(snapshot())

        // `fetchAndActivate` is throttled by the minimumFetchInterval set in FirebaseModule, so
        // this is not a network call on every collection. It returns whether a *new* configuration
        // was activated; re-emitting when it did not would hand the UI an identical snapshot and
        // recompose for nothing.
        if (fetchAndActivate()) emit(snapshot())
    }

    /**
     * @return `true` if a newly fetched configuration was activated, `false` if none was — whether
     *         because the fetch was throttled, returned nothing new, or failed.
     */
    private suspend fun fetchAndActivate(): Boolean =
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: CancellationException) {
            // Rethrown before the catch below, the same rule `safeCall` follows in `:core:network`:
            // a screen that goes away mid-fetch has cancelled this coroutine, and swallowing that
            // turns a normal cancellation into a silent failure path.
            throw e
        } catch (_: Exception) {
            // Fail-open — see FeatureFlags.isEnabled. No network, no Firebase project reachable, a
            // throttled client: all of them mean "keep showing what we already know".
            false
        }

    /**
     * Freezes the current parameter values into a [FeatureFlags].
     *
     * `remoteConfig.all` is read once, here, rather than per lookup: a snapshot handed to the UI
     * must not change values under it while a list is being filtered.
     */
    private fun snapshot(): FeatureFlags {
        val values = remoteConfig.all
        return FeatureFlags { featureId ->
            val value = values[keyFor(featureId)] ?: return@FeatureFlags true
            // asBoolean() throws on a value that is not boolean-shaped — someone typing "yes" into
            // the console, or changing a parameter's type. That is a configuration mistake, and the
            // safe reading of a mistake is "leave the feature alone".
            runCatching { value.asBoolean() }.getOrDefault(true)
        }
    }

    private fun keyFor(featureId: String): String =
        "feature_${featureId.replace('-', '_')}_enabled"
}
