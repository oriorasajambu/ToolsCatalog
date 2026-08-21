package com.minion.scaffold.feature.qrcreate.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.AppOutlinedButton
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.emv.model.EmvBuildResult
import com.minion.scaffold.core.emv.model.EmvField
import com.minion.scaffold.core.emv.model.FieldViolation
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.ViolationReason
import com.minion.scaffold.core.emv.reference.CurrencyCodes
import com.minion.scaffold.core.emv.reference.MerchantCategoryCodes
import com.minion.scaffold.core.emv.usecase.BuildEmvPayloadUseCase
import com.minion.scaffold.core.emv.usecase.EmvPayloadBreakdownUseCase
import com.minion.scaffold.feature.qrcreate.R
import com.minion.scaffold.feature.qrcreate.presentation.form.AccountFormState
import com.minion.scaffold.core.designsystem.component.FormSection
import com.minion.scaffold.feature.qrcreate.presentation.form.EmvFormState
import com.minion.scaffold.core.designsystem.component.FormField
import com.minion.scaffold.core.designsystem.component.PickerField
import com.minion.scaffold.feature.qrcreate.presentation.form.describe
import com.minion.scaffold.feature.qrcreate.presentation.form.TipMode
import com.minion.scaffold.feature.qrcreate.presentation.form.toDraft
import com.minion.scaffold.feature.qrcreate.presentation.preview.HandleQrExportEffects
import com.minion.scaffold.feature.qrcreate.presentation.preview.QrResultSection
import com.minion.scaffold.feature.qrcreate.presentation.preview.TagBreakdownView

/**
 * The EMV QR authoring screen: fill in a merchant's details, generate a scannable payload.
 *
 * @param onNavigateBack Called when the user leaves the screen.
 * @param modifier       The [Modifier] for the screen.
 * @param viewModel      The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun QrCreateScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QrCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    HandleQrExportEffects(viewModel.effect, snackbarHostState)

    QrCreateContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * The form's sections, in the order they are laid out.
 *
 * The list exists so the screen can *find* a section as well as draw one. Two behaviours need an
 * index — scrolling to the first invalid section after a failed Generate, and scrolling to the
 * result after a successful one — and counting items by hand breaks the moment a conditional item
 * such as the prefill banner appears above them. Deriving both the `item` keys and the indices from
 * one list keeps them in step by construction.
 */
private enum class FormPart {
    PREFILL_NOTICE,
    MERCHANT,
    TRANSACTION,
    ACQUIRER,
    VIOLATION_SUMMARY,
    GENERATE,
    RESULT,
}

/** Which section the user has to scroll to in order to fix [this] field. */
private fun EmvField.formPart(): FormPart = when (this) {
    EmvField.MERCHANT_NAME,
    EmvField.MERCHANT_CITY,
    EmvField.COUNTRY_CODE,
    EmvField.POSTAL_CODE,
    -> FormPart.MERCHANT

    EmvField.MERCHANT_ACCOUNTS,
    EmvField.ACQUIRER_TAG,
    EmvField.ACQUIRER_IDENTIFIER,
    EmvField.ACQUIRER_MERCHANT_PAN,
    EmvField.ACQUIRER_MERCHANT_ID,
    EmvField.ACQUIRER_CRITERIA,
    -> FormPart.ACQUIRER

    else -> FormPart.TRANSACTION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrCreateContent(
    state: QrCreateState,
    onIntent: (QrCreateIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrcreate_spacing)
    val listState = rememberLazyListState()

    val parts = buildList {
        if (state.prefillFailed) add(FormPart.PREFILL_NOTICE)
        add(FormPart.MERCHANT)
        add(FormPart.TRANSACTION)
        add(FormPart.ACQUIRER)
        if (state.violations.isNotEmpty()) add(FormPart.VIOLATION_SUMMARY)
        add(FormPart.GENERATE)
        add(FormPart.RESULT)
    }

    // Generate failed: take the user to the first section holding a problem. The errors are
    // attached to their own fields, which by the time the button is reached are usually scrolled
    // off the top — without this the press appears to do nothing at all.
    //
    // Searched in layout order rather than by taking the first violation, because the builder
    // reports them in specification order: an empty form's first violation is an acquirer field,
    // and jumping there would scroll *past* the empty merchant fields above it.
    val firstInvalidPart = parts.firstOrNull { part ->
        state.violations.any { it.field.formPart() == part }
    }
    LaunchedEffect(firstInvalidPart) {
        firstInvalidPart?.let { part ->
            parts.indexOf(part).takeIf { it >= 0 }?.let { listState.animateScrollToItem(it) }
        }
    }

    // Generate succeeded: the result is the last item of a long form, so on a phone it is created
    // entirely off-screen. Scrolling to it is the only thing that makes the press look like it
    // worked.
    LaunchedEffect(state.payload, state.payloadStale) {
        if (state.hasUsablePayload) {
            parts.indexOf(FormPart.RESULT).takeIf { it >= 0 }
                ?.let { listState.animateScrollToItem(it) }
        }
    }

    if (state.confirmingReset) {
        ResetConfirmationDialog(
            onConfirm = { onIntent(QrCreateIntent.ResetConfirmed) },
            onDismiss = { onIntent(QrCreateIntent.ResetDismissed) },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (state.editing) {
                                R.string.qrcreate_title_edit
                            } else {
                                R.string.qrcreate_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.qrcreate_navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(QrCreateIntent.ResetRequested) }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.qrcreate_reset),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        // A LazyColumn, not a scrolling Column: the form is long and every field is a heavy
        // Material text field or dropdown. A `Column.verticalScroll` composes *all* of them on the
        // first frame, and on a slow device that first layout blocks the main thread past the
        // navigation enter transition — which is wall-clock timed, so it elapses during the block
        // and the screen snaps in with no slide. Lazily composing only the visible sections keeps
        // that first frame cheap enough for the push animation to actually play.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                // So the focused field is never left under the soft keyboard. The keyboard can
                // cover the lower half of the screen, and this form is taller than one screen.
                .imePadding(),
            contentPadding = PaddingValues(horizontal = spacing, vertical = spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            // Keyed by the enum, so an item keeps its identity when a conditional section above it
            // appears or disappears.
            parts.forEach { part ->
                item(key = part) {
                    when (part) {
                        FormPart.PREFILL_NOTICE -> PrefillFailedNotice(spacing)
                        FormPart.MERCHANT -> MerchantSection(state, onIntent)
                        FormPart.TRANSACTION -> TransactionSection(state, onIntent)
                        FormPart.ACQUIRER -> AcquirerSection(state, onIntent)
                        FormPart.VIOLATION_SUMMARY -> ViolationSummary(state.violations.size)
                        FormPart.GENERATE -> AppButton(
                            text = stringResource(R.string.qrcreate_generate),
                            onClick = { onIntent(QrCreateIntent.GenerateRequested) },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        FormPart.RESULT -> QrResultSection(
                            payload = state.payload,
                            exporting = state.exporting,
                            stale = state.payloadStale,
                            emptyHint = stringResource(R.string.qrcreate_empty_hint),
                            onCopy = { onIntent(QrCreateIntent.CopyPayloadRequested) },
                            onShare = { onIntent(QrCreateIntent.ShareImageRequested) },
                            onSave = { onIntent(QrCreateIntent.SaveImageRequested) },
                            payloadContent = { payload ->
                                TagBreakdownView(payload = payload, tags = state.tags)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefillFailedNotice(spacing: Dp) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = stringResource(R.string.qrcreate_prefill_failed),
            modifier = Modifier.padding(spacing),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * How many fields were rejected.
 *
 * A live region, so a screen reader announces the count when it appears. Without it, a blind user
 * presses Generate and is told nothing at all — the errors themselves are attached to fields
 * somewhere off-screen.
 */
@Composable
private fun ViolationSummary(count: Int) {
    Text(
        text = pluralStringResource(R.plurals.qrcreate_violation_summary, count, count),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ResetConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qrcreate_reset_confirm_title)) },
        text = { Text(stringResource(R.string.qrcreate_reset_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.qrcreate_reset_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.qrcreate_cancel))
            }
        },
    )
}

/**
 * The EMV tag each field writes, for showing beside its label.
 *
 * Duplicated here rather than read from `:core:emv`'s `EmvTagCatalog`, which is `internal` to that
 * module — deliberately, since its KDoc puts human-facing labelling in the presentation layer. What
 * this adds is the pairing of a tag with the box that fills it, which is a presentation concern.
 *
 * Values are the EMVCo Merchant Presented Mode tags. The account subtags are written in the same
 * dotted form the breakdown chips under the QR use (`26.00`), so a field and its bytes can be
 * matched up by eye.
 */
private object FieldTags {
    const val MERCHANT_CATEGORY = "52"
    const val CURRENCY = "53"
    const val AMOUNT = "54"
    const val TIP_INDICATOR = "55"
    const val CONVENIENCE_FEE_FIXED = "56"
    const val CONVENIENCE_FEE_PERCENTAGE = "57"
    const val COUNTRY = "58"
    const val MERCHANT_NAME = "59"
    const val MERCHANT_CITY = "60"
    const val POSTAL_CODE = "61"

    const val SUB_IDENTIFIER = "00"
    const val SUB_MERCHANT_PAN = "01"
    const val SUB_MERCHANT_ID = "02"
    const val SUB_CRITERIA = "03"

    /** The dotted path of a subtag inside the account template occupying [accountTag]. */
    fun inAccount(accountTag: String, subtag: String) = "$accountTag.$subtag"
}

/** [labelRes] with the EMV [tag] it writes appended. */
@Composable
private fun labelWithTag(@StringRes labelRes: Int, tag: String): String =
    stringResource(R.string.qrcreate_field_with_tag, stringResource(labelRes), tag)

/**
 * A field's validation failure in words, or null if it has none.
 *
 * The shared form components take a finished message rather than a typed reason, so each format
 * maps its own violations here — which is what lets the Wi-Fi screen use the same fields.
 */
@Composable
private fun QrCreateState.errorFor(field: EmvField, accountIndex: Int? = null): String? {
    val resources = LocalResources.current
    return reasonFor(field, accountIndex)?.describe(resources)
}

@Composable
private fun MerchantSection(state: QrCreateState, onIntent: (QrCreateIntent) -> Unit) {
    FormSection(title = stringResource(R.string.qrcreate_section_merchant)) {
        FormField(
            value = state.form.merchantName,
            onValueChange = { onIntent(QrCreateIntent.FieldChanged(EmvField.MERCHANT_NAME, it)) },
            label = labelWithTag(R.string.qrcreate_merchant_name, FieldTags.MERCHANT_NAME),
            errorMessage = state.errorFor(EmvField.MERCHANT_NAME),
            hint = stringResource(R.string.qrcreate_hint_merchant_name),
        )
        FormField(
            value = state.form.merchantCity,
            onValueChange = { onIntent(QrCreateIntent.FieldChanged(EmvField.MERCHANT_CITY, it)) },
            label = labelWithTag(R.string.qrcreate_merchant_city, FieldTags.MERCHANT_CITY),
            errorMessage = state.errorFor(EmvField.MERCHANT_CITY),
        )
        FormField(
            value = state.form.countryCode,
            onValueChange = { onIntent(QrCreateIntent.FieldChanged(EmvField.COUNTRY_CODE, it)) },
            label = labelWithTag(R.string.qrcreate_country_code, FieldTags.COUNTRY),
            errorMessage = state.errorFor(EmvField.COUNTRY_CODE),
            hint = stringResource(R.string.qrcreate_hint_country_code),
        )
        FormField(
            value = state.form.postalCode,
            onValueChange = { onIntent(QrCreateIntent.FieldChanged(EmvField.POSTAL_CODE, it)) },
            label = labelWithTag(R.string.qrcreate_postal_code, FieldTags.POSTAL_CODE),
            errorMessage = state.errorFor(EmvField.POSTAL_CODE),
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
private fun TransactionSection(state: QrCreateState, onIntent: (QrCreateIntent) -> Unit) {
    val dynamic = state.form.initiationMethod == PointOfInitiationMethod.DYNAMIC

    FormSection(title = stringResource(R.string.qrcreate_section_transaction)) {
        Text(
            text = stringResource(R.string.qrcreate_initiation_method),
            style = MaterialTheme.typography.labelLarge,
        )
        // Spaced, not flush: two 48dp touch targets sharing an edge read as one block, and the
        // gap is what makes them scan as separate choices.
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.qrcreate_spacing_tight),
            ),
        ) {
            InitiationOption(
                label = stringResource(R.string.qrcreate_initiation_dynamic),
                selected = dynamic,
                onSelect = {
                    onIntent(
                        QrCreateIntent.InitiationMethodChanged(PointOfInitiationMethod.DYNAMIC),
                    )
                },
            )
            InitiationOption(
                label = stringResource(R.string.qrcreate_initiation_static),
                selected = !dynamic,
                onSelect = {
                    onIntent(
                        QrCreateIntent.InitiationMethodChanged(PointOfInitiationMethod.STATIC),
                    )
                },
            )
        }

        // Hidden rather than disabled on a static code: the specification forbids the field
        // outright, so showing a greyed-out box invites the question of how to enable it.
        if (dynamic) {
            FormField(
                value = state.form.amount,
                onValueChange = {
                    onIntent(QrCreateIntent.FieldChanged(EmvField.TRANSACTION_AMOUNT, it))
                },
                label = labelWithTag(R.string.qrcreate_amount, FieldTags.AMOUNT),
                errorMessage = state.errorFor(EmvField.TRANSACTION_AMOUNT),
                keyboardType = KeyboardType.Decimal,
                // The chosen currency, shown against the digits. "15000000" reads very differently
                // depending on what it is denominated in, and the answer is already in state.
                prefix = state.form.currency?.alphaCode,
            )
        }

        TipFields(state, onIntent)

        PickerField(
            label = labelWithTag(R.string.qrcreate_currency, FieldTags.CURRENCY),
            selectedLabel = state.form.currency?.let {
                stringResource(R.string.qrcreate_currency_option, it.alphaCode, it.name)
            },
            options = CurrencyCodes.all,
            optionLabel = { "${it.alphaCode} — ${it.name}" },
            onSelect = { onIntent(QrCreateIntent.CurrencySelected(it)) },
            errorMessage = state.errorFor(EmvField.TRANSACTION_CURRENCY),
        )

        PickerField(
            label = labelWithTag(R.string.qrcreate_category, FieldTags.MERCHANT_CATEGORY),
            selectedLabel = state.form.merchantCategory?.let {
                stringResource(R.string.qrcreate_category_option, it.code, it.name)
            },
            options = MerchantCategoryCodes.all,
            optionLabel = { "${it.code} — ${it.name}" },
            onSelect = { onIntent(QrCreateIntent.CategorySelected(it)) },
            errorMessage = state.errorFor(EmvField.MERCHANT_CATEGORY_CODE),
        )
    }
}

/**
 * The tip mode, and the one companion value the chosen mode needs.
 *
 * A picker rather than four radio buttons: the initiation method above is a genuine either/or the
 * user must understand, while this is usually left alone, and two radio groups stacked would give
 * equal visual weight to the choice nobody makes.
 */
@Composable
private fun TipFields(state: QrCreateState, onIntent: (QrCreateIntent) -> Unit) {
    val mode = state.form.tipMode

    // Resolved up front: PickerField's optionLabel is a plain lambda called during layout, not a
    // composable, so it cannot read resources itself. `associateWith` is inline, which is what
    // lets stringResource run here at all.
    val modeLabels = TipMode.entries.associateWith { stringResource(it.labelRes()) }

    PickerField(
        label = labelWithTag(R.string.qrcreate_tip, FieldTags.TIP_INDICATOR),
        selectedLabel = modeLabels[mode],
        options = TipMode.entries,
        optionLabel = { modeLabels.getValue(it) },
        onSelect = { onIntent(QrCreateIntent.TipModeChanged(it)) },
        // The mode itself cannot be invalid — only the value it asks for can.
        errorMessage = null,
    )

    if (mode.takesValue) {
        FormField(
            value = state.form.tipValue,
            onValueChange = { onIntent(QrCreateIntent.FieldChanged(EmvField.CONVENIENCE_FEE, it)) },
            // The value lands in a different tag depending on the mode, so the label says which:
            // a fixed fee is tag 56 and a percentage is 57, and the two are not interchangeable.
            label = if (mode == TipMode.PERCENTAGE_FEE) {
                labelWithTag(
                    R.string.qrcreate_tip_percentage_value,
                    FieldTags.CONVENIENCE_FEE_PERCENTAGE,
                )
            } else {
                labelWithTag(R.string.qrcreate_tip_fixed_value, FieldTags.CONVENIENCE_FEE_FIXED)
            },
            errorMessage = state.errorFor(EmvField.CONVENIENCE_FEE),
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        )
    }
}

@StringRes
private fun TipMode.labelRes(): Int = when (this) {
    TipMode.NONE -> R.string.qrcreate_tip_none
    TipMode.PROMPT -> R.string.qrcreate_tip_prompt
    TipMode.FIXED_FEE -> R.string.qrcreate_tip_fixed
    TipMode.PERCENTAGE_FEE -> R.string.qrcreate_tip_percentage
}

@Composable
private fun AcquirerSection(state: QrCreateState, onIntent: (QrCreateIntent) -> Unit) {
    FormSection(title = stringResource(R.string.qrcreate_section_acquirer)) {
        state.form.accounts.forEachIndexed { index, account ->
            AccountFields(
                index = index,
                account = account,
                state = state,
                onIntent = onIntent,
                removable = state.form.accounts.size > 1,
            )
        }

        state.reasonFor(EmvField.MERCHANT_ACCOUNTS)?.let {
            Text(
                text = stringResource(R.string.qrcreate_violation_no_accounts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.form.accounts.size < MAX_ACCOUNTS) {
            AppOutlinedButton(
                text = stringResource(R.string.qrcreate_account_add),
                onClick = { onIntent(QrCreateIntent.AccountAdded) },
            )
        }
    }
}

@Composable
private fun AccountFields(
    index: Int,
    account: AccountFormState,
    state: QrCreateState,
    onIntent: (QrCreateIntent) -> Unit,
    removable: Boolean,
) {
    val spacing = dimensionResource(R.dimen.qrcreate_spacing_tight)

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.qrcreate_account_heading, account.tag),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (removable) {
                IconButton(onClick = { onIntent(QrCreateIntent.AccountRemoved(index)) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.qrcreate_account_remove),
                    )
                }
            }
        }

        FormField(
            value = account.identifier,
            onValueChange = {
                onIntent(
                    QrCreateIntent.AccountFieldChanged(index, EmvField.ACQUIRER_IDENTIFIER, it),
                )
            },
            label = labelWithTag(
                R.string.qrcreate_account_identifier,
                FieldTags.inAccount(account.tag, FieldTags.SUB_IDENTIFIER),
            ),
            errorMessage = state.errorFor(EmvField.ACQUIRER_IDENTIFIER, index),
            hint = stringResource(R.string.qrcreate_hint_account_identifier),
        )
        FormField(
            value = account.merchantPan,
            onValueChange = {
                onIntent(
                    QrCreateIntent.AccountFieldChanged(index, EmvField.ACQUIRER_MERCHANT_PAN, it),
                )
            },
            label = labelWithTag(
                R.string.qrcreate_account_merchant_pan,
                FieldTags.inAccount(account.tag, FieldTags.SUB_MERCHANT_PAN),
            ),
            errorMessage = state.errorFor(EmvField.ACQUIRER_MERCHANT_PAN, index),
            hint = stringResource(R.string.qrcreate_hint_account_merchant_pan),
        )
        FormField(
            value = account.merchantId,
            onValueChange = {
                onIntent(
                    QrCreateIntent.AccountFieldChanged(index, EmvField.ACQUIRER_MERCHANT_ID, it),
                )
            },
            label = labelWithTag(
                R.string.qrcreate_account_merchant_id,
                FieldTags.inAccount(account.tag, FieldTags.SUB_MERCHANT_ID),
            ),
            errorMessage = state.errorFor(EmvField.ACQUIRER_MERCHANT_ID, index),
            hint = stringResource(R.string.qrcreate_hint_account_merchant_id),
        )
        FormField(
            value = account.criteria,
            onValueChange = {
                onIntent(QrCreateIntent.AccountFieldChanged(index, EmvField.ACQUIRER_CRITERIA, it))
            },
            label = labelWithTag(
                R.string.qrcreate_account_criteria,
                FieldTags.inAccount(account.tag, FieldTags.SUB_CRITERIA),
            ),
            errorMessage = state.errorFor(EmvField.ACQUIRER_CRITERIA, index),
            hint = stringResource(R.string.qrcreate_hint_account_criteria),
            imeAction = ImeAction.Done,
        )
    }
}


/**
 * One option in the initiation-method group.
 *
 * The whole row is the target, not just the button: `Modifier.selectable` on the [Row] with the
 * [RadioButton]'s own `onClick` set to null is the pattern the enclosing `selectableGroup` expects.
 * With the click on the button alone, the label was dead to touch — a 20dp target beside words
 * that look tappable — and a screen reader read the control and its label as two unrelated nodes.
 */
@Composable
private fun InitiationOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}


/** The acquirer's template plus the national switch. Nothing conventional needs a third. */
private const val MAX_ACCOUNTS = 2


/**
 * The sample merchant every filled preview is built from.
 *
 * Deliberately maximal: a percentage convenience fee (so the tip value field is present), a postal
 * code, and **two** account templates — the acquirer's and the national switch — so the previews
 * exercise the removable-account path and every field the screen can draw. The default form shows
 * one account and no tip value, which is exactly the arrangement the old previews were stuck in.
 */
private val PREVIEW_FORM = EmvFormState(
    merchantName = "PAK BOS QR 1",
    merchantCity = "Bekasi",
    postalCode = "17151",
    amount = "15000000.00",
    tipMode = TipMode.PERCENTAGE_FEE,
    tipValue = "5.00",
    merchantCategory = MerchantCategoryCodes.all.first(),
    accounts = listOf(
        AccountFormState(
            tag = EmvFormState.ACQUIRER_TAG,
            identifier = "ID.CO.CIMBNIAGA.WWW",
            merchantPan = "936000220000000282",
            merchantId = "000123456789",
            criteria = "UMI",
        ),
        AccountFormState(
            tag = EmvFormState.NATIONAL_SWITCH_TAG,
            identifier = "ID.CO.QRIS.WWW",
            merchantId = "ID1020012345678",
            criteria = "UMI",
        ),
    ),
)

/**
 * A state whose payload is genuinely built from [form], rather than pasted in.
 *
 * Both use cases are pure and take nothing in their constructors, so a preview can run the real
 * builder. That matters more than it looks: a hand-written payload constant would carry a CRC
 * nobody recomputes, so the previews would drift into showing a QR that does not decode to the
 * form beside it — the precise failure the screen's stale handling exists to prevent.
 */
private fun generatedState(
    form: EmvFormState,
    stale: Boolean = false,
    editing: Boolean = false,
): QrCreateState {
    val payload = (BuildEmvPayloadUseCase()(form.toDraft()) as? EmvBuildResult.Success)?.payload
    return QrCreateState(
        form = form,
        payload = payload,
        payloadStale = stale,
        tags = payload?.let { EmvPayloadBreakdownUseCase()(it) }.orEmpty(),
        editing = editing,
    )
}

/**
 * Renders [QrCreateContent] at a height that fits the whole form.
 *
 * The screen is a `LazyColumn`, so at a phone's height a preview composes only what fits on one
 * screen and everything below the fold is simply absent — which is why the previous two previews
 * showed the merchant card and little else. [PREVIEW_HEIGHT] is tall enough for every section.
 */
@Composable
private fun PreviewScreen(state: QrCreateState) {
    AppTheme {
        QrCreateContent(
            state = state,
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

/** Tall enough to compose the whole form, including the result section at the bottom. */
private const val PREVIEW_HEIGHT = 2400

/**
 * Keeps these seven together in the Showkase catalog.
 *
 * Without a group they land in "Default Group" beside every other screen's previews, where a
 * name like "Empty" says nothing about which screen it belongs to.
 */
private const val PREVIEW_GROUP = "QR Create"

/** Nothing entered: what the screen looks like when it is opened from the tool catalog. */
@Preview(name = "Empty", group = PREVIEW_GROUP, heightDp = PREVIEW_HEIGHT)
@Composable
internal fun QrCreateEmptyPreview() {
    PreviewScreen(QrCreateState())
}

/**
 * Every field filled and a code generated — the screen at its fullest.
 *
 * This is the one to look at when changing layout: two account templates, the tip value field
 * present, and a real QR with its tag breakdown underneath.
 */
@Preview(name = "Filled and generated", group = PREVIEW_GROUP, heightDp = PREVIEW_HEIGHT)
@Composable
internal fun QrCreateFilledPreview() {
    PreviewScreen(generatedState(PREVIEW_FORM))
}

/**
 * A static code: the amount field is gone.
 *
 * Worth its own preview because the transaction card changes shape rather than merely greying a
 * box out — the specification forbids an amount here, so the field is absent.
 */
@Preview(name = "Static, no amount", group = PREVIEW_GROUP, heightDp = PREVIEW_HEIGHT)
@Composable
internal fun QrCreateStaticPreview() {
    PreviewScreen(
        generatedState(
            PREVIEW_FORM.copy(
                initiationMethod = PointOfInitiationMethod.STATIC,
                amount = "",
            ),
        ),
    )
}

/**
 * Generate pressed on an empty form: every required field marked, and the count above the button.
 *
 * The arrangement a first-time user is most likely to hit, and the one the error styling has to
 * survive — marked fields spread across all three cards.
 */
@Preview(name = "Violations", group = PREVIEW_GROUP, heightDp = PREVIEW_HEIGHT)
@Composable
internal fun QrCreateViolationsPreview() {
    PreviewScreen(
        QrCreateState(
            violations = listOf(
                FieldViolation(EmvField.MERCHANT_NAME, ViolationReason.REQUIRED),
                FieldViolation(EmvField.MERCHANT_CITY, ViolationReason.REQUIRED),
                FieldViolation(EmvField.TRANSACTION_AMOUNT, ViolationReason.REQUIRED),
                FieldViolation(EmvField.MERCHANT_CATEGORY_CODE, ViolationReason.REQUIRED),
                FieldViolation(EmvField.ACQUIRER_IDENTIFIER, ViolationReason.REQUIRED, 0),
            ),
        ),
    )
}

/**
 * The form edited after a successful Generate.
 *
 * The code is still there but scrimmed and unexportable. Previewed because it is the one state
 * that must never look ordinary: if the "out of date" badge and the faded QR stop reading as a
 * warning, someone scans a code that no longer matches the fields above it.
 */
@Preview(name = "Stale payload", group = PREVIEW_GROUP, heightDp = PREVIEW_HEIGHT)
@Composable
internal fun QrCreateStalePreview() {
    PreviewScreen(
        generatedState(PREVIEW_FORM.copy(merchantCity = "Depok"), stale = true),
    )
}

/**
 * Opened from the scanner with a payload that would not parse.
 *
 * The error card sits above the merchant section and the title reads "Edit QR" rather than
 * "Create QR", which is the only place that title variant appears.
 */
@Preview(name = "Prefill failed", group = PREVIEW_GROUP, heightDp = PREVIEW_HEIGHT)
@Composable
internal fun QrCreatePrefillFailedPreview() {
    PreviewScreen(QrCreateState(editing = true, prefillFailed = true))
}

/** The confirmation shown by the top bar's clear action. */
@Preview(name = "Reset confirmation", group = PREVIEW_GROUP, heightDp = PREVIEW_HEIGHT)
@Composable
internal fun QrCreateResetConfirmationPreview() {
    PreviewScreen(generatedState(PREVIEW_FORM).copy(confirmingReset = true))
}
