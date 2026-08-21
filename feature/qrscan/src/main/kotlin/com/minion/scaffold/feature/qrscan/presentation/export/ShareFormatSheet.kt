package com.minion.scaffold.feature.qrscan.presentation.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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

/**
 * Which of the two shapes a scanned payment code leaves in.
 *
 * A sheet rather than a dialog, and rows rather than buttons, because each choice needs a sentence
 * under it. The text export is the report as read; the JSON export is a payment API response whose
 * unmappable third is filled with sample values — and this is the last screen before the document
 * goes to another app, so it is the last chance to say so. Two bare buttons would have nowhere to
 * put that.
 *
 * Offered only for payment codes. The other three formats share in one tap, with no sheet at all.
 *
 * @param onShareText Share the decoded report as plain text.
 * @param onShareJson Share the payment response document.
 * @param onDismiss   Leave without sharing anything. Back and an outside tap both land here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareFormatSheet(
    onShareText: () -> Unit,
    onShareJson: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Fully expanded on open. At its partial height a landscape phone shows the first
        // option and hides the second below the fold, so the JSON export could only be
        // reached by dragging the sheet up — a choice you cannot see is not a choice.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        ShareFormatOptions(onShareText = onShareText, onShareJson = onShareJson)
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
    onShareText: () -> Unit,
    onShareJson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

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
            summary = stringResource(R.string.qrscan_share_as_json_summary),
            onClick = onShareJson,
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
        ShareFormatOptions(onShareText = {}, onShareJson = {})
    }
}
