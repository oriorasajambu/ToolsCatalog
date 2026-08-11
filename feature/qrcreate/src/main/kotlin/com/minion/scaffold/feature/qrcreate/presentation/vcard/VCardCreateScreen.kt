package com.minion.scaffold.feature.qrcreate.presentation.vcard

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.vcard.model.VCardViolationReason
import com.minion.scaffold.feature.qrcreate.R
import com.minion.scaffold.core.designsystem.component.FormField
import com.minion.scaffold.core.designsystem.component.FormSection
import com.minion.scaffold.feature.qrcreate.presentation.preview.HandleQrExportEffects
import com.minion.scaffold.feature.qrcreate.presentation.preview.QrResultSection

/**
 * The contact-card authoring screen: fill in contact details, generate a vCard QR.
 *
 * @param onNavigateBack Called when the user leaves the screen.
 * @param modifier       The [Modifier] for the screen.
 * @param viewModel      The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun VCardCreateScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VCardCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleQrExportEffects(viewModel.effect, snackbarHostState)

    VCardCreateContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VCardCreateContent(
    state: VCardCreateState,
    onIntent: (VCardCreateIntent) -> Unit,
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
                                R.string.vcardcreate_title_edit
                            } else {
                                R.string.vcardcreate_title
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing, vertical = spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            if (state.prefillFailed) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.vcardcreate_prefill_failed),
                        modifier = Modifier.padding(spacing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            ContactSection(state, onIntent)

            AppButton(
                text = stringResource(R.string.qrcreate_generate),
                onClick = { onIntent(VCardCreateIntent.GenerateRequested) },
                modifier = Modifier.fillMaxWidth(),
            )

            QrResultSection(
                payload = state.payload,
                exporting = state.exporting,
                emptyHint = stringResource(R.string.vcardcreate_empty_hint),
                onCopy = { onIntent(VCardCreateIntent.CopyPayloadRequested) },
                onShare = { onIntent(VCardCreateIntent.ShareImageRequested) },
                onSave = { onIntent(VCardCreateIntent.SaveImageRequested) },
            )
        }
    }
}

@Composable
private fun ContactSection(
    state: VCardCreateState,
    onIntent: (VCardCreateIntent) -> Unit,
) {
    val resources = LocalResources.current

    FormSection(title = stringResource(R.string.vcardcreate_section_contact)) {
        ContactField(
            state = state,
            field = VCardFormField.GIVEN_NAME,
            value = state.form.givenName,
            label = R.string.vcardcreate_given_name,
            onIntent = onIntent,
            resources = resources,
        )
        ContactField(
            state = state,
            field = VCardFormField.FAMILY_NAME,
            value = state.form.familyName,
            label = R.string.vcardcreate_family_name,
            onIntent = onIntent,
            resources = resources,
        )
        ContactField(
            state = state,
            field = VCardFormField.DISPLAY_NAME,
            value = state.form.displayName,
            label = R.string.vcardcreate_display_name,
            onIntent = onIntent,
            resources = resources,
        )
        ContactField(
            state = state,
            field = VCardFormField.ORGANIZATION,
            value = state.form.organization,
            label = R.string.vcardcreate_organization,
            onIntent = onIntent,
            resources = resources,
        )
        ContactField(
            state = state,
            field = VCardFormField.TITLE,
            value = state.form.title,
            label = R.string.vcardcreate_title_field,
            onIntent = onIntent,
            resources = resources,
        )
        ContactField(
            state = state,
            field = VCardFormField.PHONE,
            value = state.form.phone,
            label = R.string.vcardcreate_phone,
            onIntent = onIntent,
            resources = resources,
            keyboardType = KeyboardType.Phone,
        )
        ContactField(
            state = state,
            field = VCardFormField.EMAIL,
            value = state.form.email,
            label = R.string.vcardcreate_email,
            onIntent = onIntent,
            resources = resources,
            keyboardType = KeyboardType.Email,
        )
    }
}

/** Every field on this form differs only in which one it is, so they share one call shape. */
@Composable
private fun ContactField(
    state: VCardCreateState,
    field: VCardFormField,
    value: String,
    label: Int,
    onIntent: (VCardCreateIntent) -> Unit,
    resources: Resources,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    FormField(
        value = value,
        onValueChange = { onIntent(VCardCreateIntent.FieldChanged(field, it)) },
        label = stringResource(label),
        errorMessage = field.domainField
            ?.let(state::reasonFor)
            ?.describe(resources),
        keyboardType = keyboardType,
    )
}

/** `:core:vcard` reports typed reasons and carries no copy, as every format module does. */
internal fun VCardViolationReason.describe(resources: Resources): String = resources.getString(
    when (this) {
        VCardViolationReason.REQUIRED -> R.string.vcardcreate_violation_required
        VCardViolationReason.INVALID_EMAIL -> R.string.vcardcreate_violation_email
        VCardViolationReason.INVALID_PHONE -> R.string.vcardcreate_violation_phone
    },
)

@Preview
@Composable
internal fun VCardCreateEmptyPreview() {
    AppTheme {
        VCardCreateContent(
            state = VCardCreateState(),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
internal fun VCardCreateFilledPreview() {
    AppTheme {
        VCardCreateContent(
            state = VCardCreateState(
                form = VCardFormState(
                    displayName = "Jane Smith",
                    givenName = "Jane",
                    familyName = "Smith",
                    organization = "Acme Ltd",
                    title = "Engineer",
                    phone = "+62811234567",
                    email = "jane@acme.example",
                ),
            ),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
