package com.minion.scaffold.feature.qrcreate.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.emv.model.EmvBuildResult
import com.minion.scaffold.core.emv.model.EmvField
import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.usecase.BuildEmvPayloadUseCase
import com.minion.scaffold.core.emv.usecase.EmvDraftFromPayloadUseCase
import com.minion.scaffold.core.emv.usecase.EmvPayloadBreakdownUseCase
import com.minion.scaffold.core.navigation.QrCreateRoute
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.feature.qrcreate.data.QrImageExporter
import com.minion.scaffold.feature.qrcreate.presentation.preview.ExportOutcome
import com.minion.scaffold.feature.qrcreate.presentation.preview.QrExportEffect
import com.minion.scaffold.feature.qrcreate.presentation.form.AccountFormState
import com.minion.scaffold.feature.qrcreate.presentation.form.EmvFormState
import com.minion.scaffold.feature.qrcreate.presentation.form.toDraft
import com.minion.scaffold.feature.qrcreate.presentation.form.toFormState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class QrCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    draftFromPayload: EmvDraftFromPayloadUseCase,
    private val buildEmvPayload: BuildEmvPayloadUseCase,
    private val breakdownPayload: EmvPayloadBreakdownUseCase,
    private val imageExporter: QrImageExporter,
) : MviViewModel<QrCreateState, QrCreateIntent, QrExportEffect>(
    initialState(savedStateHandle, draftFromPayload),
) {

    override fun onIntent(intent: QrCreateIntent) {
        when (intent) {
            is QrCreateIntent.FieldChanged -> editForm(intent.field) {
                it.withField(intent.field, intent.value)
            }

            is QrCreateIntent.AccountFieldChanged -> editForm(intent.field, intent.index) {
                it.withAccountField(intent.index, intent.field, intent.value)
            }

            is QrCreateIntent.InitiationMethodChanged -> editForm(EmvField.TRANSACTION_AMOUNT) {
                // Switching to static drops the amount rather than carrying a value the payload
                // is about to refuse — the field disappears, and a hidden value the user cannot
                // see or clear would keep failing validation for no visible reason.
                val static = intent.method == PointOfInitiationMethod.STATIC
                it.copy(
                    initiationMethod = intent.method,
                    amount = if (static) "" else it.amount,
                )
            }

            is QrCreateIntent.CurrencySelected -> editForm(EmvField.TRANSACTION_CURRENCY) {
                it.copy(currency = intent.currency)
            }

            is QrCreateIntent.CategorySelected -> editForm(EmvField.MERCHANT_CATEGORY_CODE) {
                it.copy(merchantCategory = intent.category)
            }

            is QrCreateIntent.TipModeChanged -> editForm(EmvField.CONVENIENCE_FEE) {
                // Any mode change drops the value, including between the two that both take one:
                // a fixed fee of 15000 carried over into percentage mode would read as 15000%,
                // and be rejected for a reason that has nothing to do with what the user did.
                it.copy(tipMode = intent.mode, tipValue = "")
            }

            QrCreateIntent.AccountAdded -> editForm(EmvField.MERCHANT_ACCOUNTS) {
                it.copy(
                    accounts = it.accounts + AccountFormState(EmvFormState.NATIONAL_SWITCH_TAG),
                )
            }

            is QrCreateIntent.AccountRemoved -> editForm(EmvField.MERCHANT_ACCOUNTS) {
                it.copy(accounts = it.accounts.filterIndexed { i, _ -> i != intent.index })
            }

            QrCreateIntent.GenerateRequested -> generate()

            QrCreateIntent.ResetRequested -> reduce { copy(confirmingReset = true) }

            QrCreateIntent.ResetDismissed -> reduce { copy(confirmingReset = false) }

            // `editing` survives: the screen was opened to change a scanned code, and clearing the
            // form does not change what the user came here to do or what the title should read.
            QrCreateIntent.ResetConfirmed -> reduce { QrCreateState(editing = editing) }

            // Guarded on the payload being current, not merely present. Everything below exports
            // what is on screen, and while it is stale what is on screen is the old form's code.
            QrCreateIntent.CopyPayloadRequested -> currentState
                .takeIf { it.hasUsablePayload }
                ?.payload
                ?.let { payload ->
                    viewModelScope.launch { emitEffect(QrExportEffect.CopyText(payload)) }
                }

            QrCreateIntent.ShareImageRequested -> export { exporter, payload ->
                exporter.writeShareableImage(payload)?.let(QrExportEffect::ShareImage)
                    ?: QrExportEffect.ShowExportMessage(ExportOutcome.EXPORT_FAILED)
            }

            QrCreateIntent.SaveImageRequested -> export { exporter, payload ->
                QrExportEffect.ShowExportMessage(
                    if (exporter.saveToGallery(payload)) {
                        ExportOutcome.SAVED_TO_GALLERY
                    } else {
                        ExportOutcome.EXPORT_FAILED
                    },
                )
            }
        }
    }

    /**
     * Applies a form change, and drops anything the change invalidates.
     *
     * Two things go with every edit. The generated [QrCreateState.payload] is marked stale rather
     * than discarded — it stays on screen behind a scrim, unexportable, so the user can see that
     * their earlier result exists and needs regenerating instead of watching it vanish. And the
     * edited field's
     * violation is cleared, because a "required" error sitting under a field the user is actively
     * filling in is noise.
     */
    private fun editForm(
        clearing: EmvField?,
        accountIndex: Int? = null,
        transform: (EmvFormState) -> EmvFormState,
    ) {
        reduce {
            copy(
                form = transform(form),
                payloadStale = payload != null,
                violations = violations.filterNot {
                    it.field == clearing && it.accountIndex == accountIndex
                },
            )
        }
    }

    /**
     * Validates everything, then writes the payload if it passes.
     *
     * The only place validation runs. Checking as the user types would mark fields they have not
     * reached yet, and building as they type would flicker a QR in and out of existence on every
     * keystroke.
     */
    private fun generate() {
        when (val result = buildEmvPayload(currentState.form.toDraft())) {
            is EmvBuildResult.Success -> reduce {
                copy(
                    payload = result.payload,
                    payloadStale = false,
                    tags = breakdownPayload(result.payload),
                    violations = emptyList(),
                )
            }

            // A failed Generate leaves any previous payload in place and stale. Clearing it here
            // would punish the user for asking a question: they pressed Generate, were told what
            // is wrong, and would also silently lose the code they had.
            is EmvBuildResult.Invalid -> reduce {
                copy(violations = result.violations)
            }
        }
    }

    /**
     * Runs an export against the generated payload and emits whatever it reports.
     *
     * Does nothing without a payload — the buttons are hidden until there is one, so reaching here
     * empty would mean a race, not a user action. [QrCreateState.exporting] stops a second tap
     * starting a parallel encode of the same image while the first is still writing.
     */
    private fun export(action: suspend (QrImageExporter, String) -> QrExportEffect) {
        // Same guard as the copy path: a stale payload is the previous form's code, and writing
        // it to the gallery or a share sheet is the one way a wrong QR leaves this screen.
        val payload = currentState.takeIf { it.hasUsablePayload }?.payload ?: return
        if (currentState.exporting) return

        reduce { copy(exporting = true) }
        viewModelScope.launch {
            val effect = action(imageExporter, payload)
            reduce { copy(exporting = false) }
            emitEffect(effect)
        }
    }
}

/**
 * The form as it should first appear.
 *
 * Computed before construction rather than in an `init` block, so the screen's very first frame
 * already shows the scanned values — a form that renders empty and fills in a frame later reads as
 * a glitch, and would flash the "nothing generated yet" hint over a code just scanned.
 */
private fun initialState(
    savedStateHandle: SavedStateHandle,
    draftFromPayload: EmvDraftFromPayloadUseCase,
): QrCreateState {
    val payload = savedStateHandle.get<String>(QrCreateRoute.ARG_PAYLOAD)
        ?: return QrCreateState()

    return when (val result = draftFromPayload(payload)) {
        is EmvParseResult.Success -> QrCreateState(
            form = result.value.toFormState(),
            editing = true,
        )

        // Unreachable from the scanner, which only forwards payloads that already parsed. The
        // route is public, though, and a malformed argument should explain itself rather than
        // produce an empty form with no reason given.
        is EmvParseResult.Failure -> QrCreateState(editing = true, prefillFailed = true)
    }
}
