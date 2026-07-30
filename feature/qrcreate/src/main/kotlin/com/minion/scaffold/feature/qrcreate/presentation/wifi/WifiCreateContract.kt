package com.minion.scaffold.feature.qrcreate.presentation.wifi

import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.wifi.model.WifiCredentials
import com.minion.scaffold.core.wifi.model.WifiField
import com.minion.scaffold.core.wifi.model.WifiSecurity
import com.minion.scaffold.core.wifi.model.WifiViolation
import com.minion.scaffold.core.wifi.model.WifiViolationReason

/**
 * What the Wi-Fi authoring screen renders.
 *
 * Same shape as `QrCreateState` and for the same reasons: [payload] is non-null only between a
 * successful **Generate** and the next edit, because a code on screen beside fields it no longer
 * matches would join a different network from the one being read.
 */
internal data class WifiCreateState(
    val form: WifiFormState = WifiFormState(),
    val violations: List<WifiViolation> = emptyList(),
    val payload: String? = null,
    val exporting: Boolean = false,
    val editing: Boolean = false,
    val prefillFailed: Boolean = false,
) : UiState {

    fun reasonFor(field: WifiField): WifiViolationReason? =
        violations.firstOrNull { it.field == field }?.reason
}

/**
 * The form's raw contents.
 *
 * A near-copy of [WifiCredentials], and deliberately not the same type: this one may hold a
 * password for a network that has just been switched to open, and a half-typed SSID that the
 * builder would reject. The domain model represents credentials that make sense.
 */
internal data class WifiFormState(
    val ssid: String = "",
    val security: WifiSecurity = WifiSecurity.WPA,
    val password: String = "",
    val hidden: Boolean = false,
)

internal fun WifiFormState.toCredentials(): WifiCredentials = WifiCredentials(
    ssid = ssid.trim(),
    security = security,
    password = password,
    hidden = hidden,
)

internal fun WifiCredentials.toFormState(): WifiFormState = WifiFormState(
    ssid = ssid,
    security = security,
    password = password,
    hidden = hidden,
)

internal sealed interface WifiCreateIntent : UiIntent {

    data class SsidChanged(val value: String) : WifiCreateIntent

    data class SecurityChanged(val security: WifiSecurity) : WifiCreateIntent

    data class PasswordChanged(val value: String) : WifiCreateIntent

    data class HiddenChanged(val hidden: Boolean) : WifiCreateIntent

    data object GenerateRequested : WifiCreateIntent

    data object CopyPayloadRequested : WifiCreateIntent

    data object ShareImageRequested : WifiCreateIntent

    data object SaveImageRequested : WifiCreateIntent
}
