package com.minion.scaffold.feature.qrcreate.presentation

import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.emv.model.EmvField
import com.minion.scaffold.core.emv.model.FieldViolation
import com.minion.scaffold.core.emv.model.PayloadTag
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.ViolationReason
import com.minion.scaffold.core.emv.reference.Currency
import com.minion.scaffold.core.emv.reference.MerchantCategory
import com.minion.scaffold.feature.qrcreate.presentation.form.EmvFormState
import com.minion.scaffold.feature.qrcreate.presentation.form.TipMode

/**
 * What the create screen renders.
 *
 * [payload] survives an edit, but [payloadStale] then marks it as no longer matching the form.
 *
 * This used to clear outright, on the grounds that a QR on screen while the fields no longer match
 * it is the one genuinely dangerous state here — someone could scan a code encoding different
 * values from the ones they are reading. That danger is real and has not gone away; what changed is
 * the mitigation. A code that silently vanishes mid-edit reads as a bug and tells the user nothing,
 * so the payload is now kept, obscured behind a scrim so it cannot be scanned off the screen, and
 * every export is refused while [payloadStale] is true. The user keeps the context; the hazard is
 * still closed.
 */
internal data class QrCreateState(
    /** The form's raw contents. */
    val form: EmvFormState = EmvFormState(),
    /** The current validation failures, empty when valid. */
    val violations: List<FieldViolation> = emptyList(),
    /** The generated payload, or `null` before the first successful Generate. */
    val payload: String? = null,
    /**
     * Whether [payload] predates the current form contents.
     *
     * True from the first edit after a Generate until the next one. Nothing may be exported while
     * it is set — see the note on this class.
     */
    val payloadStale: Boolean = false,
    /**
     * The generated payload broken into its tags for highlighting, or empty when there is no
     * payload. Kept alongside [payload] when it goes stale: the breakdown describes the payload it
     * was built from, which is exactly what the scrimmed, out-of-date code still shows.
     */
    val tags: List<PayloadTag> = emptyList(),
    /** Whether an export is in progress. */
    val exporting: Boolean = false,
    /** Opened with a payload to change, rather than to write one from nothing. */
    val editing: Boolean = false,
    /** The payload handed to this screen could not be read. */
    val prefillFailed: Boolean = false,
    /**
     * Whether the "clear the form" confirmation is showing.
     *
     * State rather than a `remember` in the composable: clearing a twelve-field form is
     * destructive, so the question has to survive a rotation taken mid-decision.
     */
    val confirmingReset: Boolean = false,
) : UiState {

    /** Whether there is a payload the user may act on — generated, and still matching the form. */
    val hasUsablePayload: Boolean get() = payload != null && !payloadStale

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

    /**
     * The point-of-initiation method changed (static vs dynamic).
     *
     * @property method The newly selected method.
     */
    data class InitiationMethodChanged(val method: PointOfInitiationMethod) : QrCreateIntent

    /**
     * A currency was selected.
     *
     * @property currency The selected currency.
     */
    data class CurrencySelected(val currency: Currency) : QrCreateIntent

    /**
     * A merchant category was selected.
     *
     * @property category The selected category.
     */
    data class CategorySelected(val category: MerchantCategory) : QrCreateIntent

    /**
     * The tip mode changed.
     *
     * @property mode The newly selected tip mode.
     */
    data class TipModeChanged(val mode: TipMode) : QrCreateIntent

    /** Add the national switch template alongside the acquirer's. */
    data object AccountAdded : QrCreateIntent

    /**
     * Remove a merchant account template.
     *
     * @property index The index of the account to remove.
     */
    data class AccountRemoved(val index: Int) : QrCreateIntent

    /** Validate the whole form and, if it passes, write the payload. */
    data object GenerateRequested : QrCreateIntent

    /** The user asked to clear the form; opens the confirmation. */
    data object ResetRequested : QrCreateIntent

    /** The user confirmed clearing the form. */
    data object ResetConfirmed : QrCreateIntent

    /** The user backed out of clearing the form. */
    data object ResetDismissed : QrCreateIntent

    /** Copy the generated payload. */
    data object CopyPayloadRequested : QrCreateIntent

    /** Share the generated QR image. */
    data object ShareImageRequested : QrCreateIntent

    /** Save the generated QR image to the gallery. */
    data object SaveImageRequested : QrCreateIntent
}
