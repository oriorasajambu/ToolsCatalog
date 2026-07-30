package com.minion.scaffold.feature.qrscan.presentation.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.minion.scaffold.feature.qrscan.R

/**
 * One labelled fact from a scanned code.
 *
 * @param copyable false for values that are this app's words rather than the code's — a security
 *   type, a yes/no — which nobody would paste anywhere.
 */
internal data class ReportRow(
    val label: String,
    val value: String,
    val copyable: Boolean = true,
    val monospace: Boolean = true,
)

/**
 * A heading, a list of rows, and optionally something to do with them.
 *
 * Shared by every report that is a flat list of facts — Wi-Fi, a link, a contact card. Only the
 * payment report has structure worth its own layout; these three differ in their rows and nothing
 * else, so they differ in their rows and nothing else in the code either.
 *
 * @param footer an action for the whole code, such as opening a link. Rendered after the rows.
 */
@Composable
internal fun ReportRowList(
    heading: String,
    rows: List<ReportRow>,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    footer: @Composable (() -> Unit)? = null,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        item(key = HEADING_KEY) {
            Text(text = heading, style = MaterialTheme.typography.titleMedium)
        }

        // Keyed by position as well as label. A contact card can carry two addresses or a second
        // phone number, which render under the same label — and a duplicate key crashes the list.
        itemsIndexed(items = rows, key = { index, row -> "$index-${row.label}" }) { _, row ->
            ReportRowCard(row = row, onCopy = onCopy)
        }

        if (footer != null) {
            item(key = FOOTER_KEY) { footer() }
        }
    }
}

@Composable
private fun ReportRowCard(row: ReportRow, onCopy: (String) -> Unit) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.qrscan_spacing_tight),
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (row.copyable) {
                    IconButton(onClick = { onCopy(row.value) }) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.qrscan_copy_value),
                        )
                    }
                }
            }

            Text(
                text = row.value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (row.monospace) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

/** The same rows as plain text, for the clipboard and the share sheet. */
internal fun List<ReportRow>.toPlainText(heading: String): String = buildString {
    appendLine(heading)
    appendLine()
    for (row in this@toPlainText) {
        appendLine("- ${row.label}: ${row.value}")
    }
}

private const val HEADING_KEY = "heading"
private const val FOOTER_KEY = "footer"
