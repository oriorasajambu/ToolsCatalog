package com.minion.scaffold.feature.qrcreate.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.AppOutlinedButton
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.emv.model.EmvField
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.reference.CurrencyCodes
import com.minion.scaffold.core.emv.reference.MerchantCategoryCodes
import com.minion.scaffold.feature.qrcreate.R
import com.minion.scaffold.feature.qrcreate.presentation.form.AccountFormState
import com.minion.scaffold.core.designsystem.component.FormSection
import com.minion.scaffold.feature.qrcreate.presentation.form.EmvFormState
import com.minion.scaffold.core.designsystem.component.FormField
import com.minion.scaffold.core.designsystem.component.PickerField
import com.minion.scaffold.feature.qrcreate.presentation.form.describe
import com.minion.scaffold.feature.qrcreate.presentation.form.TipMode
import com.minion.scaffold.feature.qrcreate.presentation.preview.HandleQrExportEffects
import com.minion.scaffold.feature.qrcreate.presentation.preview.QrResultSection

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
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(horizontal = spacing, vertical = spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            if (state.prefillFailed) {
                item {
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
            }

            item { MerchantSection(state, onIntent) }
            item { TransactionSection(state, onIntent) }
            item { AcquirerSection(state, onIntent) }

            // Errors are attached to their own fields, which may be scrolled off-screen by the
            // time the button is reached. The count is what tells the user the press did
            // something and where to look.
            if (state.violations.isNotEmpty()) {
                item {
                    Text(
                        text = pluralStringResource(
                            R.plurals.qrcreate_violation_summary,
                            state.violations.size,
                            state.violations.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                AppButton(
                    text = stringResource(R.string.qrcreate_generate),
                    onClick = { onIntent(QrCreateIntent.GenerateRequested) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                QrResultSection(
                    payload = state.payload,
                    exporting = state.exporting,
                    emptyHint = stringResource(R.string.qrcreate_empty_hint),
                    onCopy = { onIntent(QrCreateIntent.CopyPayloadRequested) },
                    onShare = { onIntent(QrCreateIntent.ShareImageRequested) },
                    onSave = { onIntent(QrCreateIntent.SaveImageRequested) },
                )
            }
        }
    }
}

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
            label = stringResource(R.string.qrcreate_merchant_name),
            errorMessage = state.errorFor(EmvField.MERCHANT_NAME),
        )
        FormField(
            value = state.form.merchantCity,
            onValueChange = { onIntent(QrCreateIntent.FieldChanged(EmvField.MERCHANT_CITY, it)) },
            label = stringResource(R.string.qrcreate_merchant_city),
            errorMessage = state.errorFor(EmvField.MERCHANT_CITY),
        )
        FormField(
            value = state.form.countryCode,
            onValueChange = { onIntent(QrCreateIntent.FieldChanged(EmvField.COUNTRY_CODE, it)) },
            label = stringResource(R.string.qrcreate_country_code),
            errorMessage = state.errorFor(EmvField.COUNTRY_CODE),
        )
        FormField(
            value = state.form.postalCode,
            onValueChange = { onIntent(QrCreateIntent.FieldChanged(EmvField.POSTAL_CODE, it)) },
            label = stringResource(R.string.qrcreate_postal_code),
            errorMessage = state.errorFor(EmvField.POSTAL_CODE),
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
        Column(Modifier.selectableGroup()) {
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
                label = stringResource(R.string.qrcreate_amount),
                errorMessage = state.errorFor(EmvField.TRANSACTION_AMOUNT),
                keyboardType = KeyboardType.Decimal,
            )
        }

        TipFields(state, onIntent)

        PickerField(
            label = stringResource(R.string.qrcreate_currency),
            selectedLabel = state.form.currency?.let {
                stringResource(R.string.qrcreate_currency_option, it.alphaCode, it.name)
            },
            options = CurrencyCodes.all,
            optionLabel = { "${it.alphaCode} — ${it.name}" },
            onSelect = { onIntent(QrCreateIntent.CurrencySelected(it)) },
            errorMessage = state.errorFor(EmvField.TRANSACTION_CURRENCY),
        )

        PickerField(
            label = stringResource(R.string.qrcreate_category),
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
        label = stringResource(R.string.qrcreate_tip),
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
            label = stringResource(
                if (mode == TipMode.PERCENTAGE_FEE) {
                    R.string.qrcreate_tip_percentage_value
                } else {
                    R.string.qrcreate_tip_fixed_value
                },
            ),
            errorMessage = state.errorFor(EmvField.CONVENIENCE_FEE),
            keyboardType = KeyboardType.Decimal,
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
            label = stringResource(R.string.qrcreate_account_identifier),
            errorMessage = state.errorFor(EmvField.ACQUIRER_IDENTIFIER, index),
        )
        FormField(
            value = account.merchantPan,
            onValueChange = {
                onIntent(
                    QrCreateIntent.AccountFieldChanged(index, EmvField.ACQUIRER_MERCHANT_PAN, it),
                )
            },
            label = stringResource(R.string.qrcreate_account_merchant_pan),
            errorMessage = state.errorFor(EmvField.ACQUIRER_MERCHANT_PAN, index),
        )
        FormField(
            value = account.merchantId,
            onValueChange = {
                onIntent(
                    QrCreateIntent.AccountFieldChanged(index, EmvField.ACQUIRER_MERCHANT_ID, it),
                )
            },
            label = stringResource(R.string.qrcreate_account_merchant_id),
            errorMessage = state.errorFor(EmvField.ACQUIRER_MERCHANT_ID, index),
        )
        FormField(
            value = account.criteria,
            onValueChange = {
                onIntent(QrCreateIntent.AccountFieldChanged(index, EmvField.ACQUIRER_CRITERIA, it))
            },
            label = stringResource(R.string.qrcreate_account_criteria),
            errorMessage = state.errorFor(EmvField.ACQUIRER_CRITERIA, index),
        )
    }
}


@Composable
private fun InitiationOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}


/** The acquirer's template plus the national switch. Nothing conventional needs a third. */
private const val MAX_ACCOUNTS = 2


@Preview
@Composable
internal fun QrCreateEmptyPreview() {
    AppTheme {
        QrCreateContent(
            state = QrCreateState(),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
internal fun QrCreateFilledPreview() {
    AppTheme {
        QrCreateContent(
            state = QrCreateState(
                form = EmvFormState(
                    merchantName = "PAK BOS QR 1",
                    merchantCity = "Bekasi",
                    postalCode = "17151",
                    amount = "15000000.00",
                    merchantCategory = MerchantCategoryCodes.all.first(),
                    accounts = listOf(
                        AccountFormState(
                            tag = EmvFormState.ACQUIRER_TAG,
                            identifier = "ID.CO.CIMBNIAGA.WWW",
                            merchantId = "936000220000000282",
                            criteria = "UMI",
                        ),
                    ),
                ),
            ),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
