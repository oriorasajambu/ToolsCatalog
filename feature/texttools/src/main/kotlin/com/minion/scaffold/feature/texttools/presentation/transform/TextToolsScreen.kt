package com.minion.scaffold.feature.texttools.presentation.transform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.FormSection
import com.minion.scaffold.core.designsystem.component.PickerField
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.core.ui.clipboard.rememberClipboardCopy
import com.minion.scaffold.feature.texttools.R
import com.minion.scaffold.feature.texttools.presentation.label
import com.minion.scaffold.feature.texttools.presentation.describe

/**
 * The text transform screen: an input, an operation picker, and a live output.
 *
 * @param onNavigateBack Called when the user leaves the screen.
 * @param modifier       The [Modifier] for the screen.
 * @param viewModel      The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun TextToolsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TextToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val copyToClipboard = rememberClipboardCopy(
        snackbarHostState = snackbarHostState,
        label = stringResource(R.string.texttools_clipboard_label),
        confirmation = stringResource(R.string.texttools_text_copied),
    )

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is TextToolsEffect.CopyText -> copyToClipboard(effect.text)
        }
    }

    TextToolsContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextToolsContent(
    state: TextToolsState,
    onIntent: (TextToolsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.texttools_spacing)
    val operationLabels = TEXT_OPERATIONS.associateWith { it.label(resources) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.texttools_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.texttools_navigate_back),
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
            FormSection(title = stringResource(R.string.texttools_section_input)) {
                PickerField(
                    label = stringResource(R.string.texttools_operation),
                    selectedLabel = operationLabels[state.operation],
                    options = TEXT_OPERATIONS,
                    optionLabel = { operationLabels.getValue(it) },
                    onSelect = { onIntent(TextToolsIntent.OperationChanged(it)) },
                    errorMessage = null,
                )

                OutlinedTextField(
                    value = state.input,
                    onValueChange = { onIntent(TextToolsIntent.InputChanged(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensionResource(R.dimen.texttools_field_min_height)),
                    label = { Text(text = stringResource(R.string.texttools_input)) },
                    isError = state.error != null,
                    // Monospace so an encoded string or a hash lines up character by character.
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    supportingText = state.error?.let { { Text(text = it.describe(resources)) } },
                )
            }

            OutputSection(
                output = state.output,
                onCopy = { onIntent(TextToolsIntent.CopyOutputRequested) },
            )
        }
    }
}

@Composable
private fun OutputSection(output: String, onCopy: () -> Unit) {
    FormSection(title = stringResource(R.string.texttools_section_output)) {
        if (output.isEmpty()) {
            Text(
                text = stringResource(R.string.texttools_output_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@FormSection
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = output,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.texttools_copy),
                )
            }
        }
    }
}

@Preview
@Composable
internal fun TextToolsPreview() {
    AppTheme {
        TextToolsContent(
            state = TextToolsState(input = "Man", output = "TWFu"),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
