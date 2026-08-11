package com.minion.scaffold.feature.exifstrip.presentation.component

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.minion.scaffold.core.exif.model.Coordinates
import com.minion.scaffold.core.exif.model.EmbeddedThumbnail
import com.minion.scaffold.core.exif.model.Exposure
import com.minion.scaffold.core.exif.model.MetadataBand
import com.minion.scaffold.core.exif.model.MetadataCategory
import com.minion.scaffold.core.exif.model.MetadataEntry
import com.minion.scaffold.core.exif.model.PhotoMetadata
import com.minion.scaffold.feature.exifstrip.R

/**
 * What the photo is carrying, worst first.
 *
 * Location leads because it is the reason the tool exists; everything else is a nuisance by
 * comparison. The rest of the tags are present but collapsed — the ranking decides prominence, never
 * visibility, because a curated list quietly asserts that the tags someone chose are the only ones
 * worth worrying about, and maker notes and XMP can carry anything.
 */
@Composable
internal fun MetadataSections(
    metadata: PhotoMetadata,
    onCopy: (String, String) -> Unit,
    onOpenInMaps: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.exifstrip_spacing)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        for (band in metadata.bands) {
            BandCard(
                band = band,
                coordinates = metadata.coordinates,
                onCopy = onCopy,
                onOpenInMaps = onOpenInMaps,
            )
        }

        metadata.thumbnail?.let { ThumbnailCard(it) }

        if (metadata.other.isNotEmpty()) {
            CollapsedEntries(entries = metadata.other, onCopy = onCopy)
        }
    }
}

@Composable
private fun BandCard(
    band: MetadataBand,
    coordinates: Coordinates?,
    onCopy: (String, String) -> Unit,
    onOpenInMaps: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.exifstrip_spacing)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = band.category.exposure.containerColour(),
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.exifstrip_spacing_tight),
            ),
        ) {
            Text(
                text = stringResource(band.category.titleRes()),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(band.category.explanationRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            for (entry in band.entries) {
                EntryRow(entry = entry, onCopy = onCopy)
            }

            if (band.category == MetadataCategory.Location && coordinates != null) {
                LocationActions(coordinates = coordinates, onOpenInMaps = onOpenInMaps)
            }
        }
    }
}

/**
 * The only control in the feature that sends anything anywhere.
 *
 * There is no reverse geocoding and no map preview: turning coordinates into a place name is a
 * network call carrying the exact position from the photo the user is trying to sanitise. Handing
 * them to a map app on an explicit tap makes the leak a choice, with a destination they can see.
 */
@Composable
private fun LocationActions(
    coordinates: Coordinates,
    onOpenInMaps: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = {
            onOpenInMaps(coordinates.latitude.toString(), coordinates.longitude.toString())
        },
        modifier = modifier,
    ) {
        Text(text = stringResource(R.string.exifstrip_open_in_maps))
    }
}

@Composable
private fun EntryRow(
    entry: MetadataEntry,
    onCopy: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCopy(entry.label, entry.value) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(LABEL_WEIGHT),
        )
        Text(
            text = entry.value,
            style = MaterialTheme.typography.bodySmall,
            // Monospace so coordinates line up and can be read off a digit at a time.
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(VALUE_WEIGHT),
        )
    }
}

/**
 * The embedded preview, called out separately.
 *
 * The least understood item on the screen, and worth its own card for it: a thumbnail is generated
 * when the photo is taken and is not always regenerated when it is edited, so a cropped or redacted
 * image can carry a small copy of what it looked like before.
 */
@Composable
private fun ThumbnailCard(thumbnail: EmbeddedThumbnail, modifier: Modifier = Modifier) {
    val spacing = dimensionResource(R.dimen.exifstrip_spacing)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.exifstrip_spacing_tight),
            ),
        ) {
            Text(
                text = stringResource(R.string.exifstrip_thumbnail_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.exifstrip_thumbnail_body),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = if (thumbnail.width > 0 && thumbnail.height > 0) {
                    stringResource(
                        R.string.exifstrip_thumbnail_detail,
                        thumbnail.width,
                        thumbnail.height,
                        thumbnail.byteCount / BYTES_PER_KILOBYTE,
                    )
                } else {
                    stringResource(
                        R.string.exifstrip_thumbnail_detail_size_only,
                        thumbnail.byteCount / BYTES_PER_KILOBYTE,
                    )
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun CollapsedEntries(
    entries: List<MetadataEntry>,
    onCopy: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val spacing = dimensionResource(R.dimen.exifstrip_spacing)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(spacing)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.exifstrip_other_tags, entries.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.exifstrip_collapse else R.string.exifstrip_expand,
                    ),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        dimensionResource(R.dimen.exifstrip_spacing_tight),
                    ),
                ) {
                    for (entry in entries) {
                        EntryRow(entry = entry, onCopy = onCopy)
                    }
                }
            }
        }
    }
}

@Composable
private fun Exposure.containerColour(): Color = when (this) {
    Exposure.Identifying -> MaterialTheme.colorScheme.errorContainer
    Exposure.Sensitive -> MaterialTheme.colorScheme.tertiaryContainer
    Exposure.Descriptive -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@StringRes
private fun MetadataCategory.titleRes(): Int = when (this) {
    MetadataCategory.Location -> R.string.exifstrip_band_location
    MetadataCategory.Device -> R.string.exifstrip_band_device
    MetadataCategory.Time -> R.string.exifstrip_band_time
    MetadataCategory.Provenance -> R.string.exifstrip_band_provenance
    MetadataCategory.Capture -> R.string.exifstrip_band_capture
}

@StringRes
private fun MetadataCategory.explanationRes(): Int = when (this) {
    MetadataCategory.Location -> R.string.exifstrip_band_location_body
    MetadataCategory.Device -> R.string.exifstrip_band_device_body
    MetadataCategory.Time -> R.string.exifstrip_band_time_body
    MetadataCategory.Provenance -> R.string.exifstrip_band_provenance_body
    MetadataCategory.Capture -> R.string.exifstrip_band_capture_body
}

private const val LABEL_WEIGHT = 2f
private const val VALUE_WEIGHT = 3f
private const val BYTES_PER_KILOBYTE = 1024
