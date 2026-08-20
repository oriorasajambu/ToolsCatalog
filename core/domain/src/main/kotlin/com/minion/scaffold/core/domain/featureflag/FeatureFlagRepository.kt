package com.minion.scaffold.core.domain.featureflag

import kotlinx.coroutines.flow.Flow

/**
 * One reading of the remote switches — which parts of the app may currently be offered.
 *
 * A snapshot, not a live view: the values inside never change once handed out, so a screen that
 * renders from one cannot have half its list decided by yesterday's configuration and half by
 * today's. A new configuration arrives as a new instance from [FeatureFlagRepository.flags].
 *
 * A `fun interface` so a test substitutes a lambda — `FeatureFlags { it != "weather" }` — instead
 * of a mock.
 */
fun interface FeatureFlags {

    /**
     * Whether the feature identified by [featureId] should be offered to the user.
     *
     * **Fail-open.** An id the configuration says nothing about is enabled. A remote kill switch
     * that fails closed takes the app down whenever the network, the project or a typo in a key
     * takes the configuration away, which is a far worse outcome than a feature that stays visible
     * one launch longer than intended.
     *
     * @param featureId The stable id of the feature being offered.
     * @return `true` unless the configuration explicitly withholds it.
     */
    fun isEnabled(featureId: String): Boolean
}

/**
 * Remote on/off switches for features that ship inside the binary.
 *
 * The point is to withhold something already installed — a tool whose backend is having a bad day,
 * a screen that turned out to be broken on a device the release was not tested on — without
 * waiting for a store review. It is not a permission system and not a paywall: nothing here is a
 * security boundary, because the code being switched off is on the device either way.
 *
 * Declared in the domain, implemented in `:app`, because the switches come from Firebase Remote
 * Config and Firebase is configured by `app/google-services.json` — which application this is,
 * rather than a capability a library module could own. This interface is what keeps that fact out
 * of every feature that reads a flag.
 */
interface FeatureFlagRepository {

    /**
     * The switches, as a stream.
     *
     * Emits immediately with what is already known — the in-app defaults on a first run, the last
     * activated configuration afterwards — so a screen collecting this never has to render an
     * empty or loading state. A second value follows if a fresh configuration arrives from the
     * network, at most once per collection.
     *
     * Returns no [com.minion.scaffold.core.common.result.AppResult] and never fails, which is the
     * deliberate counterpart to [FeatureFlags.isEnabled] failing open: a fetch that errors is not
     * something a screen can act on or a user can be told about, so it resolves to "keep showing
     * what we already know" here rather than becoming an error state everywhere upstream.
     *
     * @return A cold [Flow] that re-runs its fetch per collector.
     */
    fun flags(): Flow<FeatureFlags>
}
