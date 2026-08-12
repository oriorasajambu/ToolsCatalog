package com.minion.scaffold.feature.speedometer.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.feature.speedometer.R
import com.minion.scaffold.feature.speedometer.domain.Constellation
import com.minion.scaffold.feature.speedometer.domain.SatelliteStatus

/**
 * What the receiver can see, shown while it is still looking.
 *
 * ## Why a cold start needs a picture
 *
 * With no network there is no almanac to download, so the first fix can take anywhere from thirty
 * seconds to several minutes, and indoors it may never arrive. A spinner gives no way to tell those
 * apart. Bars and no fix means the receiver is working and needs longer; no bars means go outside.
 *
 * It is also the most direct demonstration that the tool needs no network at all — which is the whole
 * premise, and otherwise something the user has to take on trust.
 */
@Composable
internal fun SatelliteView(
    status: SatelliteStatus,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.speedometer_spacing)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.speedometer_spacing_tight),
            ),
        ) {
            Text(
                text = stringResource(
                    R.string.speedometer_satellites_used,
                    status.usedInFix,
                    status.visible,
                ),
                style = MaterialTheme.typography.titleSmall,
            )

            if (status.hasAny) {
                SignalBars(status.signalStrengths)

                if (status.constellations.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.speedometer_constellations,
                            status.constellations
                                .filter { it != Constellation.Unknown }
                                .joinToString { it.label() },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.speedometer_no_satellites),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One bar per satellite, height by carrier-to-noise density.
 *
 * C/N0 runs from about 20 dB-Hz (barely detectable) to 50 (strong), which is the range the bars are
 * scaled over. Drawn rather than laid out because thirty of them is thirty composables for a picture
 * that is thirty rectangles.
 */
@Composable
private fun SignalBars(strengths: List<Float>, modifier: Modifier = Modifier) {
    val weak = MaterialTheme.colorScheme.outlineVariant
    val strong = MaterialTheme.colorScheme.primary

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.speedometer_bars_height))
            .drawBehind {
                if (strengths.isEmpty()) return@drawBehind

                val slot = size.width / strengths.size
                val barWidth = slot * BAR_FILL

                strengths.forEachIndexed { index, cn0 ->
                    val fraction = ((cn0 - MIN_CN0) / (MAX_CN0 - MIN_CN0)).coerceIn(0f, 1f)
                    val height = size.height * fraction

                    drawRect(
                        color = if (fraction >= STRONG_FRACTION) strong else weak,
                        topLeft = Offset(index * slot, size.height - height),
                        size = Size(barWidth, height),
                    )
                }
            },
    )
}

private fun Constellation.label(): String = when (this) {
    Constellation.Gps -> "GPS"
    Constellation.Glonass -> "GLONASS"
    Constellation.Galileo -> "Galileo"
    Constellation.BeiDou -> "BeiDou"
    Constellation.Qzss -> "QZSS"
    Constellation.Irnss -> "NavIC"
    Constellation.Sbas -> "SBAS"
    Constellation.Unknown -> "?"
}

/** Barely detectable to strong, in dB-Hz. */
private const val MIN_CN0 = 15f
private const val MAX_CN0 = 45f

/** Above this fraction a satellite is usable rather than merely visible. */
private const val STRONG_FRACTION = 0.5f

private const val BAR_FILL = 0.7f
