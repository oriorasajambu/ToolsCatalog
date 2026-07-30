package com.minion.scaffold.feature.qrcreate.presentation

import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.emv.model.EmvField
import com.minion.scaffold.core.emv.model.FieldViolation
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.ViolationReason
import com.minion.scaffold.core.emv.reference.Currency
import com.minion.scaffold.core.emv.reference.MerchantCategory
import com.minion.scaffold.feature.qrcreate.presentation.form.EmvFormState
import com.minion.scaffold.feature.qrcreate.presentation.form.TipMode

/**
 * What the create screen renders.
 *
 * [payload] is non-null only between a successful **Generate** and the next edit. Any change to
 * the form clears it, because a QR left on screen while the fields no longer match it is the one
 * genuinely dangerous state here — someone would scan a code encoding different values from the
 * ones they are reading.
 */
internal data class QrCreateState(
    val form: EmvFormState = EmvFormState(),
    val violations: List<FieldViolation> = emptyList(),
    val payload: String? = null,
    val exporting: Boolean = false,
    /** Opened with a payload to change, rather than to write one from nothing. */
    val editing: Boolean = false,
    /** The payload handed to this screen could not be read. */
    val prefillFailed: Boolean = false,
) : UiState {

    /**
     * Why [field] was rejected, or null if it was not.
     *
     * A list rather than a map because it never holds more than a dozen entries, and a linear scan
     * of twelve at recomposition is cheaper than the key type a map would need — violations are
     * identified by a field *and* an optional account index.
     */
    fun reasonFor(field: EmvField, accountIndex: Int? = null): ViolationReason? = violations
        .firstOrNull { it.field == field && it.accountIndex == accountIndex }
        ?.reason
}

internal sealed interface QrCreateIntent : UiIntent {

    /**
     * A keystroke in a top-level text field.
     *
     * One intent for every text field, keyed by the same [EmvField] the builder reports violations
     * against — so clearing the edited field's error is automatic rather than a mapping someone
     * has to maintain per field.
     */
    data class FieldChanged(val field: EmvField, val value: String) : QrCreateIntent

    /** A keystroke in a merchant account template's field. */
    data class AccountFieldChanged(
        val index: Int,
        val field: EmvField,
        val value: String,
    ) : QrCreateIntent

    data class InitiationMethodChanged(val method: PointOfInitiationMethod) : QrCreateIntent

    data class CurrencySelected(val currency: Currency) : QrCreateIntent

    data class CategorySelected(val category: MerchantCategory) : QrCreateIntent

    data class TipModeChanged(val mode: TipMode) : QrCreateIntent

    /** Add the national switch template alongside the acquirer's. */
    data object AccountAdded : QrCreateIntent

    data class AccountRemoved(val index: Int) : QrCreateIntent

    /** Validate the whole form and, if it passes, write the payload. */
    data object GenerateRequested : QrCreateIntent

    data object CopyPayloadRequested : QrCreateIntent

    data object ShareImageRequested : QrCreateIntent

    data object SaveImageRequested : QrCreateIntent
}
