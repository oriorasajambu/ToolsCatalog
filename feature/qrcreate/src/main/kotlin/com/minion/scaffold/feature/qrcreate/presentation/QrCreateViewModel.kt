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

            QrCreateIntent.CopyPayloadRequested -> currentState.payload?.let { payload ->
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
     * Two things go with every edit. The generated [QrCreateState.payload] is discarded, because a
     * QR that no longer matches the fields beside it is worse than no QR. And the edited field's
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
                payload = null,
                tags = emptyList(),
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
                    tags = breakdownPayload(result.payload),
                    violations = emptyList(),
                )
            }

            is EmvBuildResult.Invalid -> reduce {
                copy(payload = null, tags = emptyList(), violations = result.violations)
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
        val payload = currentState.payload ?: return
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
