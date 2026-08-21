package com.minion.scaffold.feature.qrscan.presentation.compare

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.emv.model.CrcDiff
import com.minion.scaffold.core.emv.model.DiffStatus
import com.minion.scaffold.core.emv.model.EmvComparison
import com.minion.scaffold.core.emv.model.EmvSegmentDiff
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.presentation.QrScanPreviewData
import com.minion.scaffold.feature.qrscan.presentation.report.tagLabel

/**
 * Two payment codes, tag by tag.
 *
 * Grouped by top-level segment rather than flattened into a list of dotted paths, so it reads like
 * the report the user just came from: one card per tag, with its subtags inside. A merchant account
 * that changed slot says so on the card, which is the level the fact belongs at — the account moved,
 * not each of its four subtags independently.
 *
 * Every field is shown, not only the differing ones. A comparison that quietly hides fifty rows is
 * one whose "nothing else changed" the reader has to take on trust, and the coloured edge already
 * makes the changed rows findable in a long list.
 */
@Composable
internal fun EmvComparisonView(
    comparison: EmvComparison,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        // Keyed by position as well as tag. A tag is unique in a valid payload, and this tool
        // exists to be pointed at invalid ones — where a duplicate key crashes the list.
        itemsIndexed(
            items = comparison.segments,
            key = { index, segment -> "$index-${segment.tag}" },
        ) { _, segment ->
            SegmentDiffCard(segment = segment)
        }

        item(key = INTEGRITY_KEY) {
            Text(
                text = stringResource(R.string.qrscan_report_integrity),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item(key = CRC_KEY) {
            CrcDiffCard(crc = comparison.crc)
        }
    }
}

@Composable
private fun SegmentDiffCard(segment: EmvSegmentDiff, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val subtagIndent = dimensionResource(R.dimen.qrscan_subtag_indent)
    val tight = dimensionResource(R.dimen.qrscan_spacing_tight)

    DiffCard(
        label = resources.getString(
            R.string.qrscan_segment_heading,
            segment.tag,
            tagLabel(resources, segment.tag),
        ),
        status = segment.status,
        annotation = segment.movedFromTag?.let {
            resources.getString(R.string.qrscan_compare_moved, it)
        },
        modifier = modifier,
    ) {
        if (segment.subtags.isEmpty()) {
            DiffValues(
                baselineValue = segment.baseline?.node?.rawValue,
                candidateValue = segment.candidate?.node?.rawValue,
                status = segment.status,
            )
            return@DiffCard
        }

        // A template's own value is the concatenation of what is printed below it, so showing it
        // here would be the same characters twice with the interesting one buried in the middle.
        Column(
            modifier = Modifier.padding(start = subtagIndent),
            verticalArrangement = Arrangement.spacedBy(tight),
        ) {
            for (subtag in segment.subtags) {
                Column(verticalArrangement = Arrangement.spacedBy(tight)) {
                    Text(
                        text = resources.getString(
                            R.string.qrscan_compare_subtag,
                            subtag.tag,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = subtag.status.labelColor(),
                    )
                    DiffValues(
                        baselineValue = subtag.baselineValue,
                        candidateValue = subtag.candidateValue,
                        status = subtag.status,
                    )
                }
            }
        }
    }
}

/**
 * The checksums, side by side.
 *
 * Its own card rather than a row among the segments, and excluded from the difference count: tag 63
 * is derived from every other field, so a single edit necessarily changes it too and counting it
 * would report every one-field difference as two. Kept visible all the same, because a code whose
 * own checksum does not validate is the finding this tool exists for.
 */
@Composable
private fun CrcDiffCard(crc: CrcDiff, modifier: Modifier = Modifier) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val tight = dimensionResource(R.dimen.qrscan_spacing_tight)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (crc.bothValid) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            contentColor = if (crc.bothValid) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(tight),
        ) {
            Text(
                text = stringResource(R.string.qrscan_tag_crc),
                style = MaterialTheme.typography.labelLarge,
            )

            CrcSide(sideRes = R.string.qrscan_compare_side_baseline, passed = crc.baseline.passed)
            CrcSide(sideRes = R.string.qrscan_compare_side_candidate, passed = crc.candidate.passed)

            DiffValues(
                baselineValue = crc.baseline.expected,
                candidateValue = crc.candidate.expected,
                status = if (crc.same) DiffStatus.SAME else DiffStatus.CHANGED,
            )
        }
    }
}

@Composable
private fun CrcSide(@StringRes sideRes: Int, passed: Boolean) {
    Text(
        text = stringResource(
            R.string.qrscan_compare_side_value,
            stringResource(sideRes),
            stringResource(
                if (passed) R.string.qrscan_crc_passed else R.string.qrscan_crc_failed,
            ),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
}

private const val INTEGRITY_KEY = "integrity-header"
private const val CRC_KEY = "crc"

@Preview(showBackground = true)
@Composable
internal fun EmvComparisonViewPreview() {
    AppTheme {
        EmvComparisonView(
            comparison = QrScanPreviewData.changedEmvComparison,
        )
    }
}
