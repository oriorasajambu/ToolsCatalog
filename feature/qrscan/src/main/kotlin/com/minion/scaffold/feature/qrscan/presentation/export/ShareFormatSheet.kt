package com.minion.scaffold.feature.qrscan.presentation.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.domain.export.PaymentSchemaSource

/**
 * Which of the two shapes a scanned payment code leaves in, and which contract the JSON follows.
 *
 * A sheet rather than a dialog, and rows rather than buttons, because each choice needs a sentence
 * under it. This is the last screen before a document goes to another app, so it is the last chance
 * to say what that document will contain — which is why the JSON row's wording depends on whether
 * the built-in schema or an imported one is active. Under the default it can promise exactly which
 * fields are sample values; under a custom template it must not, because the app no longer knows.
 *
 * Offered only for payment codes. The other three formats share in one tap, with no sheet at all.
 *
 * @param schemaSource Which schema the JSON export will use.
 * @param schemaLabel  The imported file's name, or empty under the built-in.
 * @param onShareText  Share the decoded report as plain text.
 * @param onShareJson  Share the payment response document.
 * @param onOpenSchema Open the schema settings, carrying this code so the reference can resolve.
 * @param onDismiss    Leave without sharing anything. Back and an outside tap both land here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// Two pieces of schema state and the four things the sheet offers. A sheet is all actions.
@Suppress("LongParameterList")
internal fun ShareFormatSheet(
    schemaSource: PaymentSchemaSource,
    schemaLabel: String,
    onShareText: () -> Unit,
    onShareJson: () -> Unit,
    onOpenSchema: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Fully expanded on open. At its partial height a landscape phone shows the first
        // option and hides the rest below the fold, so the JSON export could only be
        // reached by dragging the sheet up — a choice you cannot see is not a choice.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        ShareFormatOptions(
            schemaSource = schemaSource,
            schemaLabel = schemaLabel,
            onShareText = onShareText,
            onShareJson = onShareJson,
            onOpenSchema = onOpenSchema,
        )
    }
}

/**
 * The sheet's contents, without the sheet.
 *
 * Split out so the arrangement can be previewed — a `ModalBottomSheet` renders into its own window
 * and shows up empty in the catalog.
 */
@Composable
internal fun ShareFormatOptions(
    schemaSource: PaymentSchemaSource,
    schemaLabel: String,
    onShareText: () -> Unit,
    onShareJson: () -> Unit,
    onOpenSchema: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    val schemaName = when (schemaSource) {
        PaymentSchemaSource.BuiltIn -> stringResource(R.string.qrscan_schema_source_built_in)
        PaymentSchemaSource.Custom ->
            schemaLabel.ifBlank { stringResource(R.string.qrscan_schema_source_custom) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(
            text = stringResource(R.string.qrscan_share_format_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = spacing),
        )

        ShareFormatRow(
            title = stringResource(R.string.qrscan_share_as_text),
            summary = stringResource(R.string.qrscan_share_as_text_summary),
            onClick = onShareText,
        )

        ShareFormatRow(
            title = stringResource(R.string.qrscan_share_as_json),
            summary = when (schemaSource) {
                PaymentSchemaSource.BuiltIn ->
                    stringResource(R.string.qrscan_share_as_json_summary)

                PaymentSchemaSource.Custom ->
                    stringResource(R.string.qrscan_share_as_json_summary_custom)
            },
            onClick = onShareJson,
        )

        HorizontalDivider()

        // Configuring rather than sharing, hence the rule above it. It is here because this is
        // where somebody finds out which contract they are about to send — and discovering it is
        // the wrong one should not mean backing out of the report to fix it.
        ShareFormatRow(
            title = stringResource(R.string.qrscan_share_schema_row, schemaName),
            summary = stringResource(R.string.qrscan_share_schema_row_summary),
            onClick = onOpenSchema,
        )
    }
}

@Composable
private fun ShareFormatRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Column(
        // Clickable before padding, so the whole row is the target rather than only the words.
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing, vertical = spacing),
        verticalArrangement = Arrangement.spacedBy(
            dimensionResource(R.dimen.qrscan_spacing_tight),
        ),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun ShareFormatOptionsPreview() {
    AppTheme {
        ShareFormatOptions(
            schemaSource = PaymentSchemaSource.BuiltIn,
            schemaLabel = "",
            onShareText = {},
            onShareJson = {},
            onOpenSchema = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun ShareFormatOptionsCustomPreview() {
    AppTheme {
        ShareFormatOptions(
            schemaSource = PaymentSchemaSource.Custom,
            schemaLabel = "acquirer-v2.json",
            onShareText = {},
            onShareJson = {},
            onOpenSchema = {},
        )
    }
}
