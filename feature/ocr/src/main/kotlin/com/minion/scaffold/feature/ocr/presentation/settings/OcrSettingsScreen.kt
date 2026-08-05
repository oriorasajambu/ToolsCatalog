package com.minion.scaffold.feature.ocr.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.ocr.model.OcrEngine
import com.minion.scaffold.feature.ocr.R

@Composable
internal fun OcrSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OcrSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    OcrSettingsContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrSettingsContent(
    state: OcrSettingsState,
    onIntent: (OcrSettingsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.ocr_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.ocr_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.ocr_navigate_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        // Scrollable because the two supporting lines plus the notes below run past a short screen
        // in a large font scale, and a settings screen that cannot reach its own attribution is a
        // licence problem rather than a layout one.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.ocr_settings_engine_header),
                modifier = Modifier.padding(horizontal = spacing, vertical = spacing / 2),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            // selectableGroup() is what makes this announce as "1 of 2" to a screen reader rather
            // than as two unrelated radio buttons that happen to sit next to each other.
            Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                OcrEngine.entries.forEach { engine ->
                    EngineRow(
                        engine = engine,
                        selected = state.engine == engine,
                        onSelected = { onIntent(OcrSettingsIntent.EngineSelected(engine)) },
                    )
                }
            }

            // Stated rather than left to be discovered: the viewfinder's guide boxes always come
            // from ML Kit, so someone who selects PaddleOCR and watches the overlay would otherwise
            // be quietly misled about which engine is running.
            Text(
                text = stringResource(R.string.ocr_settings_hints_note),
                modifier = Modifier.padding(spacing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = spacing))

            // MIT and Apache-2.0 both require the notice to ship with the software. The source
            // headers carry the licence text; this is where a user can actually see it.
            Text(
                text = stringResource(R.string.ocr_settings_attribution),
                modifier = Modifier.padding(spacing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EngineRow(
    engine: OcrEngine,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    ListItem(
        modifier = Modifier.selectable(
            selected = selected,
            onClick = onSelected,
            role = Role.RadioButton,
        ),
        headlineContent = { Text(text = stringResource(engine.toLabelRes())) },
        supportingContent = { Text(text = stringResource(engine.toDetailRes())) },
        leadingContent = {
            // null onClick: the whole row owns the click, and a separately clickable radio would
            // make the row announce twice and give a second, smaller touch target for the same act.
            RadioButton(selected = selected, onClick = null)
        },
    )
}

@StringRes
private fun OcrEngine.toLabelRes(): Int = when (this) {
    OcrEngine.MlKit -> R.string.ocr_engine_mlkit
    OcrEngine.PaddleOcr -> R.string.ocr_engine_paddle
}

@StringRes
private fun OcrEngine.toDetailRes(): Int = when (this) {
    OcrEngine.MlKit -> R.string.ocr_engine_mlkit_detail
    OcrEngine.PaddleOcr -> R.string.ocr_engine_paddle_detail
}

@Preview
@Composable
internal fun OcrSettingsPreview() {
    AppTheme {
        OcrSettingsContent(
            state = OcrSettingsState(engine = OcrEngine.MlKit),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}
