package com.minion.scaffold.feature.qrscan.presentation.compare

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.scaffold.core.designsystem.component.AppOutlinedButton
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.domain.compare.FieldComparison
import com.minion.scaffold.feature.qrscan.domain.compare.QrComparison
import com.minion.scaffold.feature.qrscan.presentation.QrScanIntent
import com.minion.scaffold.feature.qrscan.presentation.QrScanPreviewData
import com.minion.scaffold.feature.qrscan.presentation.RawDiffState

/**
 * Two codes, read against each other.
 *
 * A verdict, then the fields, then what to do next. The verdict comes first because it is the
 * answer — a reader who trusts it never has to scroll, and one who does not has the evidence
 * directly underneath.
 *
 * @param comparison The two codes and their aligned fields.
 * @param rawDiff    How far along the character alignment is; only the Raw tab needs it.
 */
@Composable
internal fun CompareView(
    comparison: QrComparison,
    rawDiff: RawDiffState,
    onIntent: (QrScanIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    // The two codes, in an order-independent key. Scanning another code is news and lands the
    // reader back on the verdict and the fields; swapping the same two is a change of viewpoint and
    // leaves them where they were looking.
    val pair = remember(comparison) {
        listOf(comparison.baseline.payload, comparison.candidate.payload).sorted()
    }

    // View state, like the report's focused tag: which tab is showing is not something the
    // ViewModel decides, nor something a rotation should lose.
    var selectedTab by rememberSaveable(pair) { mutableIntStateOf(TAB_FIELDS) }

    // The alignment is quadratic in whatever the payloads do not share, so it runs when the tab
    // that needs it is opened rather than with every comparison. The intent is idempotent.
    LaunchedEffect(selectedTab, comparison.candidate.payload) {
        if (selectedTab == TAB_RAW) onIntent(QrScanIntent.RawDiffRequested)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        VerdictCard(
            comparison = comparison,
            modifier = Modifier.padding(horizontal = spacing),
        )

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == TAB_FIELDS,
                onClick = { selectedTab = TAB_FIELDS },
                text = { Text(stringResource(R.string.qrscan_compare_tab_fields)) },
            )
            Tab(
                selected = selectedTab == TAB_RAW,
                onClick = { selectedTab = TAB_RAW },
                text = { Text(stringResource(R.string.qrscan_compare_tab_raw)) },
            )
        }

        // `weight`, so the list scrolls under a fixed verdict and a fixed action row rather than
        // the whole screen scrolling and the answer leaving the top of it.
        val listModifier = Modifier.weight(1f)
        val listPadding = PaddingValues(horizontal = spacing)

        if (selectedTab == TAB_RAW) {
            RawDiffView(
                baselinePayload = comparison.baseline.payload,
                candidatePayload = comparison.candidate.payload,
                state = rawDiff,
                modifier = listModifier,
                contentPadding = listPadding,
            )
        } else {
            when (val fields = comparison.fields) {
                is FieldComparison.Payment -> EmvComparisonView(
                    comparison = fields.comparison,
                    modifier = listModifier,
                    contentPadding = listPadding,
                )

                FieldComparison.Flat -> FlatComparisonView(
                    baseline = comparison.baseline,
                    candidate = comparison.candidate,
                    modifier = listModifier,
                    contentPadding = listPadding,
                )
            }
        }

        CompareActions(
            onIntent = onIntent,
            modifier = Modifier.padding(horizontal = spacing),
            contentPadding = contentPadding,
        )
    }
}

/**
 * The one line a reader takes in before anything else.
 *
 * "Different" gets the error colour even though a difference is a legitimate finding rather than a
 * fault, because catching a code that is not the one you expected is the reason anybody opened this
 * screen — it is the alarm, and it has to read as one.
 */
@Composable
private fun VerdictCard(comparison: QrComparison, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val verdict = comparison.verdict(resources)

    val container = when (verdict) {
        CompareVerdict.Identical -> MaterialTheme.colorScheme.secondaryContainer
        CompareVerdict.Equivalent -> MaterialTheme.colorScheme.tertiaryContainer
        is CompareVerdict.Different -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (verdict) {
        CompareVerdict.Identical -> MaterialTheme.colorScheme.onSecondaryContainer
        CompareVerdict.Equivalent -> MaterialTheme.colorScheme.onTertiaryContainer
        is CompareVerdict.Different -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = onContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.qrscan_spacing_tight),
            ),
        ) {
            Text(
                text = verdict.describe(resources),
                style = MaterialTheme.typography.titleMedium,
            )

            SideLine(
                sideRes = R.string.qrscan_compare_side_baseline,
                summary = comparison.baseline.summaryTitle(resources),
            )
            SideLine(
                sideRes = R.string.qrscan_compare_side_candidate,
                summary = comparison.candidate.summaryTitle(resources),
            )

            // Tapping Compare while still pointing at the first sticker re-reads it within a
            // fraction of a second. "Identical" is then true and useless, so it says which.
            if (comparison.bytesIdentical) {
                Text(
                    text = stringResource(R.string.qrscan_compare_same_payload_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SideLine(@StringRes sideRes: Int, summary: String) {
    Text(
        text = stringResource(R.string.qrscan_compare_side_value, stringResource(sideRes), summary),
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * What to do with a finished comparison.
 *
 * Scan another keeps the baseline pinned, which is the workflow this feature is actually for —
 * checking a row of stickers against one known-good code. Swap is here because the two are usually
 * scanned in the order they came to hand rather than the order they mean, and then every "only in
 * the first" reads backwards.
 */
@Composable
private fun CompareActions(
    onIntent: (QrScanIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(
            dimensionResource(R.dimen.qrscan_spacing_tight),
        ),
    ) {
        AppOutlinedButton(
            text = stringResource(R.string.qrscan_compare_rescan),
            onClick = { onIntent(QrScanIntent.CompareRescanRequested) },
            modifier = Modifier.weight(1f),
        )
        AppOutlinedButton(
            text = stringResource(R.string.qrscan_compare_swap),
            onClick = { onIntent(QrScanIntent.CompareSwapped) },
            modifier = Modifier.weight(1f),
        )
    }
}

private const val TAB_FIELDS = 0
private const val TAB_RAW = 1

@Preview(showBackground = true)
@Composable
internal fun CompareViewChangedPreview() {
    AppTheme {
        CompareView(
            comparison = QrScanPreviewData.changedComparison,
            rawDiff = RawDiffState.NotComputed,
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun CompareViewEquivalentPreview() {
    AppTheme {
        CompareView(
            comparison = QrScanPreviewData.equivalentComparison,
            rawDiff = RawDiffState.NotComputed,
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun CompareViewIdenticalPreview() {
    AppTheme {
        CompareView(
            comparison = QrScanPreviewData.identicalComparison,
            rawDiff = RawDiffState.NotComputed,
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun CompareViewWifiPreview() {
    AppTheme {
        CompareView(
            comparison = QrScanPreviewData.wifiComparison,
            rawDiff = RawDiffState.NotComputed,
            onIntent = {},
        )
    }
}
