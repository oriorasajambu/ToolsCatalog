package com.minion.scaffold.feature.qrcreate.presentation.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.wifi.model.WifiField
import com.minion.scaffold.core.wifi.model.WifiSecurity
import com.minion.scaffold.feature.qrcreate.R
import com.minion.scaffold.core.designsystem.component.FormField
import com.minion.scaffold.core.designsystem.component.FormSection
import com.minion.scaffold.core.designsystem.component.PasswordField
import com.minion.scaffold.core.designsystem.component.PickerField
import com.minion.scaffold.feature.qrcreate.presentation.preview.HandleQrExportEffects
import com.minion.scaffold.feature.qrcreate.presentation.preview.QrResultSection

@Composable
internal fun WifiCreateScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WifiCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleQrExportEffects(viewModel.effect, snackbarHostState)

    WifiCreateContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiCreateContent(
    state: WifiCreateState,
    onIntent: (WifiCreateIntent) -> Unit,
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
                                R.string.wificreate_title_edit
                            } else {
                                R.string.wificreate_title
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
                        text = stringResource(R.string.wificreate_prefill_failed),
                        modifier = Modifier.padding(spacing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            NetworkSection(state, onIntent)

            AppButton(
                text = stringResource(R.string.qrcreate_generate),
                onClick = { onIntent(WifiCreateIntent.GenerateRequested) },
                modifier = Modifier.fillMaxWidth(),
            )

            QrResultSection(
                payload = state.payload,
                exporting = state.exporting,
                emptyHint = stringResource(R.string.wificreate_empty_hint),
                onCopy = { onIntent(WifiCreateIntent.CopyPayloadRequested) },
                onShare = { onIntent(WifiCreateIntent.ShareImageRequested) },
                onSave = { onIntent(WifiCreateIntent.SaveImageRequested) },
            )
        }
    }
}

@Composable
private fun NetworkSection(state: WifiCreateState, onIntent: (WifiCreateIntent) -> Unit) {
    val resources = LocalResources.current
    val securityLabels = WifiSecurity.entries.associateWith { stringResource(it.labelRes()) }

    FormSection(title = stringResource(R.string.wificreate_section_network)) {
        FormField(
            value = state.form.ssid,
            onValueChange = { onIntent(WifiCreateIntent.SsidChanged(it)) },
            label = stringResource(R.string.wificreate_ssid),
            errorMessage = state.reasonFor(WifiField.SSID)?.describe(resources),
        )

        PickerField(
            label = stringResource(R.string.wificreate_security),
            selectedLabel = securityLabels[state.form.security],
            options = WifiSecurity.entries,
            optionLabel = { securityLabels.getValue(it) },
            onSelect = { onIntent(WifiCreateIntent.SecurityChanged(it)) },
            errorMessage = null,
        )

        // Hidden rather than disabled for an open network: there is nothing to type, so a greyed
        // box would only invite the question of how to enable it.
        if (state.form.security != WifiSecurity.OPEN) {
            PasswordField(
                value = state.form.password,
                onValueChange = { onIntent(WifiCreateIntent.PasswordChanged(it)) },
                label = stringResource(R.string.wificreate_password),
                errorMessage = state.reasonFor(WifiField.PASSWORD)?.describe(resources),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.wificreate_hidden),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.form.hidden,
                onCheckedChange = { onIntent(WifiCreateIntent.HiddenChanged(it)) },
            )
        }
    }
}

private fun WifiSecurity.labelRes(): Int = when (this) {
    WifiSecurity.WPA -> R.string.wificreate_security_wpa
    WifiSecurity.WEP -> R.string.wificreate_security_wep
    WifiSecurity.OPEN -> R.string.wificreate_security_open
}

@Preview
@Composable
internal fun WifiCreateEmptyPreview() {
    AppTheme {
        WifiCreateContent(
            state = WifiCreateState(),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
internal fun WifiCreateOpenNetworkPreview() {
    AppTheme {
        WifiCreateContent(
            state = WifiCreateState(
                form = WifiFormState(ssid = "Cafe Guest", security = WifiSecurity.OPEN),
            ),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
internal fun WifiCreateGeneratedPreview() {
    AppTheme {
        WifiCreateContent(
            state = WifiCreateState(
                form = WifiFormState(ssid = "Guest", password = "hunter2!", hidden = true),
                payload = "WIFI:T:WPA;S:Guest;P:hunter2!;H:true;;",
                editing = true,
            ),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
