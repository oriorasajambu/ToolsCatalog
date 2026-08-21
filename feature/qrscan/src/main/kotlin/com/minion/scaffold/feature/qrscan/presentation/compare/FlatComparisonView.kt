package com.minion.scaffold.feature.qrscan.presentation.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.domain.ScannedContent
import com.minion.scaffold.feature.qrscan.presentation.QrScanPreviewData

/**
 * Two Wi-Fi codes, links or contact cards, field by field.
 *
 * The rows come from the report views' own builders, so a comparison shows exactly the fields the
 * two reports behind it showed — including the ones a card omits when they are blank, which is how
 * "the second card has no organisation" turns into a row of its own rather than into silence.
 *
 * @param baseline  The pinned code.
 * @param candidate The code it is being read against. Always the same format as [baseline].
 */
@Composable
internal fun FlatComparisonView(
    baseline: ScannedContent,
    candidate: ScannedContent,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    val rows = remember(baseline, candidate, resources) {
        diffRows(
            baseline = baseline.flatReportRows(resources),
            candidate = candidate.flatReportRows(resources),
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        // Keyed by position as well as label. A contact card can carry two phone numbers, which
        // render under the same label — and a duplicate key crashes the list.
        itemsIndexed(items = rows, key = { index, row -> "$index-${row.label}" }) { _, row ->
            DiffCard(label = row.label, status = row.status) {
                DiffValues(
                    baselineValue = row.baselineValue,
                    candidateValue = row.candidateValue,
                    status = row.status,
                    monospace = row.monospace,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun FlatComparisonViewPreview() {
    AppTheme {
        FlatComparisonView(
            baseline = QrScanPreviewData.wifiComparison.baseline,
            candidate = QrScanPreviewData.wifiComparison.candidate,
        )
    }
}
