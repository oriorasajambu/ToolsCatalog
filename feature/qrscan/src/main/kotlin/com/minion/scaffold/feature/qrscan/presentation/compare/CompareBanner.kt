package com.minion.scaffold.feature.qrscan.presentation.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.domain.ScannedContent
import com.minion.scaffold.feature.qrscan.presentation.QrScanPreviewData

/**
 * What the camera is looking for, while it is looking for the second code.
 *
 * Names the pinned code rather than saying "comparing": a user who walked to the far end of a
 * counter needs to know *which* sticker is in the slot, and by then the fact that a comparison is
 * running is the part they remember.
 *
 * Sits above the viewfinder rather than over it, so theme colours are correct here. Anything drawn
 * *on* the preview needs fixed colours instead — a theme colour answers to the palette rather than
 * to the image behind it, which is why `ScanReticle` carries its own.
 *
 * @param baseline The pinned code.
 * @param onCancel Abandons the comparison and returns to the pinned code's report.
 */
@Composable
internal fun CompareBanner(
    baseline: ScannedContent,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = spacing),
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.qrscan_spacing_tight),
                ),
            ) {
                Text(
                    text = stringResource(
                        R.string.qrscan_compare_banner,
                        baseline.summaryTitle(resources),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.qrscan_compare_hint),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.qrscan_compare_cancel),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun CompareBannerPreview() {
    AppTheme {
        CompareBanner(baseline = QrScanPreviewData.paymentContent, onCancel = {})
    }
}
