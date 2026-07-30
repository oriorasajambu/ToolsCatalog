package com.minion.scaffold.feature.qrcreate.presentation.url

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
import com.minion.scaffold.core.url.model.UrlViolationReason
import com.minion.scaffold.feature.qrcreate.R
import com.minion.scaffold.core.designsystem.component.FormField
import com.minion.scaffold.core.designsystem.component.FormSection
import com.minion.scaffold.feature.qrcreate.presentation.preview.HandleQrExportEffects
import com.minion.scaffold.feature.qrcreate.presentation.preview.QrResultSection

@Composable
internal fun UrlCreateScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UrlCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleQrExportEffects(viewModel.effect, snackbarHostState)

    UrlCreateContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrlCreateContent(
    state: UrlCreateState,
    onIntent: (UrlCreateIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
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
                                R.string.urlcreate_title_edit
                            } else {
                                R.string.urlcreate_title
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
                        text = stringResource(R.string.urlcreate_prefill_failed),
                        modifier = Modifier.padding(spacing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            FormSection(title = stringResource(R.string.urlcreate_section_link)) {
                FormField(
                    value = state.link,
                    onValueChange = { onIntent(UrlCreateIntent.LinkChanged(it)) },
                    label = stringResource(R.string.urlcreate_link),
                    errorMessage = state.violation?.describe(resources),
                    keyboardType = KeyboardType.Uri,
                )
                Text(
                    text = stringResource(R.string.urlcreate_scheme_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AppButton(
                text = stringResource(R.string.qrcreate_generate),
                onClick = { onIntent(UrlCreateIntent.GenerateRequested) },
                modifier = Modifier.fillMaxWidth(),
            )

            QrResultSection(
                payload = state.payload,
                exporting = state.exporting,
                emptyHint = stringResource(R.string.urlcreate_empty_hint),
                onCopy = { onIntent(UrlCreateIntent.CopyPayloadRequested) },
                onShare = { onIntent(UrlCreateIntent.ShareImageRequested) },
                onSave = { onIntent(UrlCreateIntent.SaveImageRequested) },
            )
        }
    }
}

/** `:core:url` reports typed reasons and carries no copy, as every format module does. */
internal fun UrlViolationReason.describe(resources: Resources): String = resources.getString(
    when (this) {
        UrlViolationReason.REQUIRED -> R.string.urlcreate_violation_required
        UrlViolationReason.MALFORMED -> R.string.urlcreate_violation_malformed
        UrlViolationReason.UNSUPPORTED_SCHEME -> R.string.urlcreate_violation_scheme
    },
)

@Preview
@Composable
internal fun UrlCreateEmptyPreview() {
    AppTheme {
        UrlCreateContent(
            state = UrlCreateState(),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
internal fun UrlCreateGeneratedPreview() {
    AppTheme {
        UrlCreateContent(
            state = UrlCreateState(
                link = "https://example.com/menu",
                payload = "https://example.com/menu",
            ),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
