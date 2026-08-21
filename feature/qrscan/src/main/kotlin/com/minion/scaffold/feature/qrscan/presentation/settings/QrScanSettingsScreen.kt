package com.minion.scaffold.feature.qrscan.presentation.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.AppOutlinedButton
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.ui.clipboard.rememberClipboardCopy
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.domain.export.PaymentSchemaSource
import kotlinx.coroutines.launch

/**
 * Which JSON schema the export renders through, and what a template may refer to.
 *
 * Two things at once, deliberately. Choosing a schema and knowing what can go in one are the same
 * task — nobody imports a template without first finding out what `merchant_pan` is called — and
 * splitting them across two screens would mean keeping two lists in step with one vocabulary.
 */
@Composable
internal fun QrScanSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QrScanSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val copyToken = rememberClipboardCopy(
        snackbarHostState = snackbarHostState,
        label = stringResource(R.string.qrscan_schema_clipboard_label),
    )

    // SAF, so no storage permission: the picker grants access to the one document chosen.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onIntent(QrScanSettingsIntent.SchemaPicked(it)) } }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is QrScanSettingsEffect.CopyToken -> copyToken(effect.token)

            is QrScanSettingsEffect.ShareSchema -> {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = MIME_TYPE_JSON
                    putExtra(Intent.EXTRA_TEXT, effect.text)
                }
                context.startActivity(Intent.createChooser(share, null))
            }

            QrScanSettingsEffect.SchemaImported -> coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    resources.getString(R.string.qrscan_schema_imported),
                )
            }

            QrScanSettingsEffect.SchemaReset -> coroutineScope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.qrscan_schema_reset))
            }
        }
    }

    QrScanSettingsContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        onPickSchema = { picker.launch(SCHEMA_MIME_TYPES) },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Stateless, so every arrangement is previewable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScanSettingsContent(
    state: QrScanSettingsState,
    onIntent: (QrScanSettingsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onPickSchema: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.qrscan_schema_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.qrscan_navigate_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            item(key = ACTIVE_KEY) {
                ActiveSchemaCard(state = state, onIntent = onIntent, onPickSchema = onPickSchema)
            }

            state.importError?.let { error ->
                item(key = ERROR_KEY) {
                    ImportErrorCard(error = error, onIntent = onIntent)
                }
            }

            item(key = REFERENCE_KEY) {
                Text(
                    text = stringResource(R.string.qrscan_schema_reference_heading),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            item(key = REFERENCE_NOTE_KEY) {
                Text(
                    text = stringResource(R.string.qrscan_schema_reference_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(items = state.placeholders, key = { it.token }) { row ->
                PlaceholderCard(row = row, onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun ActiveSchemaCard(
    state: QrScanSettingsState,
    onIntent: (QrScanSettingsIntent) -> Unit,
    onPickSchema: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val tight = dimensionResource(R.dimen.qrscan_spacing_tight)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(tight),
        ) {
            Text(
                text = stringResource(R.string.qrscan_schema_active_heading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = when (state.source) {
                    PaymentSchemaSource.BuiltIn ->
                        stringResource(R.string.qrscan_schema_source_built_in)

                    PaymentSchemaSource.Custom -> state.label.ifBlank {
                        stringResource(R.string.qrscan_schema_source_custom)
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = when (state.source) {
                    PaymentSchemaSource.BuiltIn ->
                        stringResource(R.string.qrscan_schema_built_in_summary)

                    PaymentSchemaSource.Custom ->
                        stringResource(R.string.qrscan_schema_custom_summary)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.outdated) {
                Text(
                    text = stringResource(R.string.qrscan_schema_outdated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Column(
                modifier = Modifier.padding(top = tight),
                verticalArrangement = Arrangement.spacedBy(tight),
            ) {
                AppButton(
                    text = stringResource(R.string.qrscan_schema_import),
                    onClick = onPickSchema,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppOutlinedButton(
                    text = stringResource(R.string.qrscan_schema_export),
                    onClick = { onIntent(QrScanSettingsIntent.ExportRequested) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.canReset) {
                    AppOutlinedButton(
                        text = stringResource(R.string.qrscan_schema_reset_action),
                        onClick = { onIntent(QrScanSettingsIntent.ResetRequested) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportErrorCard(
    error: SchemaImportError,
    onIntent: (QrScanSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.qrscan_spacing_tight),
            ),
        ) {
            Text(
                text = when (error) {
                    SchemaImportError.NotJson ->
                        stringResource(R.string.qrscan_schema_error_not_json)

                    SchemaImportError.Unreadable ->
                        stringResource(R.string.qrscan_schema_error_unreadable)

                    is SchemaImportError.UnknownPlaceholders -> stringResource(
                        R.string.qrscan_schema_error_unknown,
                        error.tokens.joinToString(separator = ", "),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            AppOutlinedButton(
                text = stringResource(R.string.qrscan_schema_error_dismiss),
                onClick = { onIntent(QrScanSettingsIntent.ErrorDismissed) },
            )
        }
    }
}

@Composable
private fun PlaceholderCard(
    row: PlaceholderRow,
    onIntent: (QrScanSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val tight = dimensionResource(R.dimen.qrscan_spacing_tight)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(tight),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.qrscan_schema_token, row.token),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onIntent(QrScanSettingsIntent.CopyTokenRequested(row.token)) },
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.qrscan_copy_value),
                    )
                }
            }

            Text(
                text = row.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Only when the screen was opened from a report. An empty string then means this code
            // carries nothing there, which is a different statement from not having asked.
            row.value?.let { value ->
                Text(
                    text = value.ifBlank { stringResource(R.string.qrscan_compare_absent) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private const val MIME_TYPE_JSON = "application/json"

/** A picker filtered to what a template plausibly arrives as; many providers label JSON as text. */
private val SCHEMA_MIME_TYPES = arrayOf("application/json", "text/plain", "*/*")

private const val ACTIVE_KEY = "active"
private const val ERROR_KEY = "error"
private const val REFERENCE_KEY = "reference"
private const val REFERENCE_NOTE_KEY = "reference-note"

@Preview(showBackground = true)
@Composable
internal fun QrScanSettingsBuiltInPreview() {
    AppTheme {
        QrScanSettingsContent(
            state = QrScanSettingsState(
                placeholders = listOf(
                    PlaceholderRow("merchant_pan", "Subtag 01 of the primary account"),
                    PlaceholderRow("amount", "Tag 54 exactly as written, never reformatted"),
                ),
            ),
            onIntent = {},
            onNavigateBack = {},
            onPickSchema = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun QrScanSettingsCustomPreview() {
    AppTheme {
        QrScanSettingsContent(
            state = QrScanSettingsState(
                source = PaymentSchemaSource.Custom,
                label = "acquirer-v2.json",
                importError = SchemaImportError.UnknownPlaceholders(listOf("merchant_nmae")),
                placeholders = listOf(
                    PlaceholderRow("merchant_pan", "Subtag 01 of the primary account", "93600022"),
                    PlaceholderRow("amount", "Tag 54 exactly as written", ""),
                ),
            ),
            onIntent = {},
            onNavigateBack = {},
            onPickSchema = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
