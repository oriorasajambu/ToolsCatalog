package com.minion.scaffold.feature.texttools.presentation.generate

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.FormSection
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.text.model.CharacterClass
import com.minion.scaffold.core.text.model.PasswordProblem
import com.minion.scaffold.feature.texttools.R
import com.minion.scaffold.feature.texttools.presentation.rememberClipboardCopy

/**
 * The generator screen: pick a kind, set its options, and generate a value.
 *
 * @param onNavigateBack Called when the user leaves the screen.
 * @param modifier       The [Modifier] for the screen.
 * @param viewModel      The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun GenerateScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GenerateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val copyToClipboard = rememberClipboardCopy()

    com.minion.scaffold.core.ui.mvi.ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is GenerateEffect.CopyText -> copyToClipboard(effect.text)
        }
    }

    GenerateContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateContent(
    state: GenerateState,
    onIntent: (GenerateIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.texttools_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.generate_title)) },
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
            KindSection(state.kind, onIntent)

            when (state.kind) {
                GenerateKind.PASSWORD -> PasswordOptions(state, onIntent)
                GenerateKind.RANDOM_HEX -> HexOptions(state, onIntent)
                GenerateKind.UUID -> Unit
            }

            AppButton(
                text = stringResource(R.string.generate_button),
                onClick = { onIntent(GenerateIntent.GenerateRequested) },
                modifier = Modifier.fillMaxWidth(),
            )

            ResultSection(output = state.output, problem = state.problem, onIntent = onIntent)
        }
    }
}

@Composable
private fun KindSection(kind: GenerateKind, onIntent: (GenerateIntent) -> Unit) {
    FormSection(title = stringResource(R.string.generate_kind)) {
        Column(Modifier.selectableGroup()) {
            for (option in GenerateKind.entries) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = option == kind,
                        onClick = { onIntent(GenerateIntent.KindChanged(option)) },
                    )
                    Text(
                        text = stringResource(option.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordOptions(state: GenerateState, onIntent: (GenerateIntent) -> Unit) {
    val spacing = dimensionResource(R.dimen.texttools_spacing_tight)

    FormSection(title = stringResource(R.string.generate_password_options)) {
        Text(
            text = stringResource(R.string.generate_length, state.passwordLength),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = state.passwordLength.toFloat(),
            onValueChange = { onIntent(GenerateIntent.PasswordLengthChanged(it.toInt())) },
            valueRange = MIN_PASSWORD_LENGTH.toFloat()..MAX_PASSWORD_LENGTH.toFloat(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            for (characterClass in CharacterClass.entries) {
                FilterChip(
                    selected = characterClass in state.passwordClasses,
                    onClick = { onIntent(GenerateIntent.PasswordClassToggled(characterClass)) },
                    label = { Text(text = stringResource(characterClass.labelRes())) },
                )
            }
        }
    }
}

@Composable
private fun HexOptions(state: GenerateState, onIntent: (GenerateIntent) -> Unit) {
    FormSection(title = stringResource(R.string.generate_hex_options)) {
        Text(
            text = stringResource(R.string.generate_bytes, state.hexByteCount),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = state.hexByteCount.toFloat(),
            onValueChange = { onIntent(GenerateIntent.HexByteCountChanged(it.toInt())) },
            valueRange = MIN_HEX_BYTES.toFloat()..MAX_HEX_BYTES.toFloat(),
        )
    }
}

@Composable
private fun ResultSection(
    output: String?,
    problem: PasswordProblem?,
    onIntent: (GenerateIntent) -> Unit,
) {
    FormSection(title = stringResource(R.string.generate_result)) {
        when {
            problem != null -> Text(
                text = stringResource(problem.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            output == null -> Text(
                text = stringResource(R.string.generate_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Shown in clear, with no reveal toggle: it was made to be copied, so masking a value
            // the user just asked to see would be theatre — the call the Wi-Fi payload makes too.
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onIntent(GenerateIntent.CopyOutputRequested) }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.texttools_copy),
                    )
                }
            }
        }
    }
}

@StringRes
private fun GenerateKind.labelRes(): Int = when (this) {
    GenerateKind.UUID -> R.string.generate_kind_uuid
    GenerateKind.PASSWORD -> R.string.generate_kind_password
    GenerateKind.RANDOM_HEX -> R.string.generate_kind_hex
}

@StringRes
private fun CharacterClass.labelRes(): Int = when (this) {
    CharacterClass.LOWERCASE -> R.string.generate_class_lower
    CharacterClass.UPPERCASE -> R.string.generate_class_upper
    CharacterClass.DIGITS -> R.string.generate_class_digits
    CharacterClass.SYMBOLS -> R.string.generate_class_symbols
}

@StringRes
private fun PasswordProblem.messageRes(): Int = when (this) {
    PasswordProblem.NO_CHARACTER_CLASS -> R.string.generate_problem_no_class
    PasswordProblem.LENGTH_TOO_SHORT -> R.string.generate_problem_too_short
}

private const val MIN_PASSWORD_LENGTH = 4
private const val MAX_PASSWORD_LENGTH = 64
private const val MIN_HEX_BYTES = 4
private const val MAX_HEX_BYTES = 64

@Preview
@Composable
internal fun GeneratePreview() {
    AppTheme {
        GenerateContent(
            state = GenerateState(output = "aB3!kM9pQ2rT7xZ0"),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}
