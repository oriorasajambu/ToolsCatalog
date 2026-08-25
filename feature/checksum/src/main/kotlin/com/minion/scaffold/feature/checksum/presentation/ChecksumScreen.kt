package com.minion.scaffold.feature.checksum.presentation

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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.FormField
import com.minion.scaffold.core.designsystem.component.FormSection
import com.minion.scaffold.core.designsystem.component.PickerField
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.text.model.TextOperation
import com.minion.scaffold.core.ui.clipboard.rememberClipboardCopy
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.feature.checksum.R

/**
 * The checksum screen: hash some text, and check the digest against one you were given.
 *
 * @param onNavigateBack Called when the user leaves the screen.
 * @param modifier       The [Modifier] for the screen.
 * @param viewModel      The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun ChecksumScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChecksumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val copyToClipboard = rememberClipboardCopy(
        snackbarHostState = snackbarHostState,
        label = stringResource(R.string.checksum_clipboard_label),
        confirmation = stringResource(R.string.checksum_digest_copied),
    )

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is ChecksumEffect.CopyDigest -> copyToClipboard(effect.digest)
        }
    }

    ChecksumContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChecksumContent(
    state: ChecksumState,
    onIntent: (ChecksumIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.checksum_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.checksum_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.checksum_navigate_back),
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
            InputSection(
                input = state.input,
                algorithm = state.algorithm,
                onIntent = onIntent,
            )

            DigestSection(
                digest = state.digest,
                onCopy = { onIntent(ChecksumIntent.CopyDigestRequested) },
            )

            VerifySection(
                expected = state.expected,
                verdict = state.verdict,
                onExpectedChange = { onIntent(ChecksumIntent.ExpectedChanged(it)) },
                spacing = spacing,
            )
        }
    }
}

@Composable
private fun InputSection(
    input: String,
    algorithm: TextOperation,
    onIntent: (ChecksumIntent) -> Unit,
) {
    // Resolved up front rather than inside `optionLabel`, which is a plain lambda the picker
    // calls while filtering — `stringResource` is composable and cannot be read from there.
    // `LocalResources`, not `LocalContext.getString`: a context read is not invalidated by a
    // configuration change, so the labels would go stale after a locale switch.
    val resources = LocalResources.current
    val labels = CHECKSUM_ALGORITHMS.associateWith { resources.getString(it.labelRes) }
    val selected = CHECKSUM_ALGORITHMS.first { it.operation == algorithm }

    FormSection(title = stringResource(R.string.checksum_section_input)) {
        PickerField(
            label = stringResource(R.string.checksum_algorithm),
            selectedLabel = labels[selected],
            options = CHECKSUM_ALGORITHMS,
            optionLabel = { labels.getValue(it) },
            onSelect = { onIntent(ChecksumIntent.AlgorithmChanged(it.operation)) },
            errorMessage = null,
        )

        OutlinedTextField(
            value = input,
            onValueChange = { onIntent(ChecksumIntent.InputChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(R.dimen.checksum_input_min_height)),
            label = { Text(text = stringResource(R.string.checksum_input)) },
            // Monospace so a pasted payload lines up character by character with what it came from.
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun DigestSection(digest: String, onCopy: () -> Unit) {
    FormSection(title = stringResource(R.string.checksum_section_digest)) {
        if (digest.isEmpty()) {
            Text(
                text = stringResource(R.string.checksum_digest_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@FormSection
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = digest,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.checksum_copy),
                )
            }
        }
    }
}

@Composable
private fun VerifySection(
    expected: String,
    verdict: Verdict,
    onExpectedChange: (String) -> Unit,
    spacing: Dp,
) {
    FormSection(title = stringResource(R.string.checksum_section_verify)) {
        FormField(
            value = expected,
            onValueChange = onExpectedChange,
            label = stringResource(R.string.checksum_expected),
            errorMessage = null,
            hint = stringResource(R.string.checksum_expected_hint),
        )

        VerdictRow(verdict = verdict, spacing = spacing)
    }
}

/**
 * The verdict, as an icon and a word.
 *
 * Both, not colour alone: a red field and a green field are the same field to a user who cannot
 * tell them apart, and this screen's whole output is which of the two it is.
 */
@Composable
private fun VerdictRow(verdict: Verdict, spacing: Dp) {
    val icon: ImageVector
    val tint: Color
    val message: String

    when (verdict) {
        // Nothing pasted in yet — the field's own hint says what to do, and a third neutral row
        // under it would only compete with it.
        Verdict.NOT_COMPARED -> return

        Verdict.MATCH -> {
            icon = Icons.Filled.CheckCircle
            tint = MaterialTheme.colorScheme.primary
            message = stringResource(R.string.checksum_verdict_match)
        }

        Verdict.MISMATCH -> {
            icon = Icons.Filled.Cancel
            tint = MaterialTheme.colorScheme.error
            message = stringResource(R.string.checksum_verdict_mismatch)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing / 2),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Preview
@Composable
internal fun ChecksumMatchPreview() {
    AppTheme {
        ChecksumContent(
            state = ChecksumState(
                input = "abc",
                digest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                expected = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
                verdict = Verdict.MATCH,
            ),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
internal fun ChecksumMismatchPreview() {
    AppTheme {
        ChecksumContent(
            state = ChecksumState(
                input = "abc",
                algorithm = TextOperation.MD5,
                digest = "900150983cd24fb0d6963f7d28e17f72",
                expected = "900150983cd24fb0d6963f7d28e17f00",
                verdict = Verdict.MISMATCH,
            ),
            onIntent = {},
            onNavigateBack = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
