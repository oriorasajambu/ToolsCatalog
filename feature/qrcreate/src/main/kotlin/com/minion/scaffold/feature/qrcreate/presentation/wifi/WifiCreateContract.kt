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
 *
 * @property form          The form's raw contents.
 * @property violations    The current validation failures, empty when valid.
 * @property payload       The generated payload, or `null` before Generate or after an edit.
 * @property exporting     Whether an export is in progress.
 * @property editing       Whether the screen opened pre-filled for editing.
 * @property prefillFailed Whether a pre-fill payload could not be parsed.
 */
internal data class WifiCreateState(
    val form: WifiFormState = WifiFormState(),
    val violations: List<WifiViolation> = emptyList(),
    val payload: String? = null,
    val exporting: Boolean = false,
    val editing: Boolean = false,
    val prefillFailed: Boolean = false,
) : UiState {

    /**
     * The violation for [field], if any.
     *
     * @param field The form field to look up.
     * @return The rejection reason, or `null` when [field] is valid.
     */
    fun reasonFor(field: WifiField): WifiViolationReason? =
        violations.firstOrNull { it.field == field }?.reason
}

/**
 * The form's raw contents.
 *
 * A near-copy of [WifiCredentials], and deliberately not the same type: this one may hold a
 * password for a network that has just been switched to open, and a half-typed SSID that the
 * builder would reject. The domain model represents credentials that make sense.
 *
 * @property ssid     The typed network name.
 * @property security The selected security type.
 * @property password The typed passphrase or key.
 * @property hidden   Whether the network is marked hidden.
 */
internal data class WifiFormState(
    val ssid: String = "",
    val security: WifiSecurity = WifiSecurity.WPA,
    val password: String = "",
    val hidden: Boolean = false,
)

/**
 * The domain credentials this form represents.
 *
 * @receiver The raw form contents.
 * @return The trimmed [WifiCredentials] to hand to the builder.
 */
internal fun WifiFormState.toCredentials(): WifiCredentials = WifiCredentials(
    ssid = ssid.trim(),
    security = security,
    password = password,
    hidden = hidden,
)

/**
 * The form contents that reproduce these credentials, for the editing path.
 *
 * @receiver The parsed credentials to edit.
 * @return The form state pre-filled from them.
 */
internal fun WifiCredentials.toFormState(): WifiFormState = WifiFormState(
    ssid = ssid,
    security = security,
    password = password,
    hidden = hidden,
)

/** Everything the user can do on the Wi-Fi authoring screen. */
internal sealed interface WifiCreateIntent : UiIntent {

    /**
     * The SSID field changed.
     *
     * @property value The new SSID.
     */
    data class SsidChanged(val value: String) : WifiCreateIntent

    /**
     * The security type changed.
     *
     * @property security The newly selected security type.
     */
    data class SecurityChanged(val security: WifiSecurity) : WifiCreateIntent

    /**
     * The password field changed.
     *
     * @property value The new passphrase or key.
     */
    data class PasswordChanged(val value: String) : WifiCreateIntent

    /**
     * The hidden-network toggle changed.
     *
     * @property hidden Whether the network is marked hidden.
     */
    data class HiddenChanged(val hidden: Boolean) : WifiCreateIntent

    /** Generate the QR from the current form. */
    data object GenerateRequested : WifiCreateIntent

    /** Copy the generated payload. */
    data object CopyPayloadRequested : WifiCreateIntent

    /** Share the generated QR image. */
    data object ShareImageRequested : WifiCreateIntent

    /** Save the generated QR image to the gallery. */
    data object SaveImageRequested : WifiCreateIntent
}
