package com.minion.scaffold.feature.exifstrip.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.minion.scaffold.core.exif.model.MetadataKind
import com.minion.scaffold.core.exif.model.SegmentSummary
import com.minion.scaffold.core.exif.model.TrailingKind
import com.minion.scaffold.feature.exifstrip.R
import com.minion.scaffold.feature.exifstrip.presentation.ExifStripState

/**
 * The export controls, and afterwards the evidence.
 *
 * There is deliberately no before-and-after image comparison. With a lossless strip the pixels are
 * identical by construction, so putting two identical pictures side by side would imply a difference
 * that does not exist. The whole diff is in the metadata, which is what the rest of the screen shows.
 */
@Composable
internal fun ExportPanel(
    state: ExifStripState,
    onExport: () -> Unit,
    onConvert: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.exifstrip_spacing)
    val loaded = state.content as? ExifStripState.Content.Loaded ?: return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            when {
                state.exporting -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                // A container that cannot be taken apart byte by byte. The conversion is offered as
                // its own labelled action rather than as a silent fallback, because it is the one
                // path that recompresses and the guarantee is only worth something if the exception
                // is visible.
                loaded.convertibleFormat != null -> ConvertOffer(
                    format = loaded.convertibleFormat,
                    onConvert = onConvert,
                )

                state.export == null -> Button(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.exifstrip_export))
                }

                else -> ExportResultBlock(export = state.export, onShare = onShare)
            }

            // Stated unconditionally rather than only when the name looks like a date. The first
            // version guessed, using a regex over the display name — and guessed wrong, because the
            // system picker often reports a synthesised name rather than the one the user knows the
            // file by. Since the export renames every time, saying so plainly is both simpler and
            // always true, and it removes a whole class of confident-but-wrong line from a screen
            // whose entire job is being trustworthy.
            if (state.export == null) {
                Text(
                    text = stringResource(R.string.exifstrip_renames),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConvertOffer(format: String, onConvert: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            dimensionResource(R.dimen.exifstrip_spacing_tight),
        ),
    ) {
        Text(
            text = stringResource(R.string.exifstrip_convert_explanation, format.uppercase()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onConvert, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.exifstrip_convert))
        }
    }
}

/**
 * What the export produced, and what it left behind.
 *
 * Naming the retained items rather than declaring success is the point: "stripped" has to mean
 * something checkable, and the only way a reader can tell the difference between a thorough job and
 * a confident one is being shown the list.
 */
@Composable
private fun ExportResultBlock(
    export: ExifStripState.ExportState,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val removedText = remember(export.removed) { describeBlocks(resources, export.removed) }
    val retainedText = remember(export.retained) { describeBlocks(resources, export.retained) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            dimensionResource(R.dimen.exifstrip_spacing_tight),
        ),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(dimensionResource(R.dimen.exifstrip_spacing)),
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.exifstrip_spacing_tight),
                ),
            ) {
                Text(
                    text = stringResource(R.string.exifstrip_verified),
                    style = MaterialTheme.typography.titleSmall,
                )

                if (export.removed.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.exifstrip_removed_summary,
                            removedText,
                            export.bytesSaved / BYTES_PER_KILOBYTE,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                export.trailing?.let { trailing ->
                    Text(
                        text = stringResource(
                            if (trailing.kind == TrailingKind.EmbeddedVideo) {
                                R.string.exifstrip_removed_video
                            } else {
                                R.string.exifstrip_removed_trailing
                            },
                            trailing.byteCount / BYTES_PER_KILOBYTE,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = if (export.retained.isEmpty()) {
                        stringResource(R.string.exifstrip_retained_nothing)
                    } else {
                        stringResource(
                            R.string.exifstrip_retained_summary,
                            retainedText,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (export.recompressed) {
                    Text(
                        text = stringResource(R.string.exifstrip_recompressed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.exifstrip_pixels_identical),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = stringResource(R.string.exifstrip_renamed, export.fileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.exifstrip_share))
        }

        // Says where the file is, because it is deliberately nowhere the user can browse to. Saving
        // to the gallery would put the clean copy somewhere it gets indexed and backed up, next to
        // the original it is meant to replace.
        Text(
            text = stringResource(R.string.exifstrip_share_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A readable list of what a set of blocks contains, grouped by kind.
 *
 * Grouped because a file with two text chunks otherwise reads "a comment, a comment", which looks
 * like a bug in the sentence rather than a fact about the file.
 */
internal fun describeBlocks(
    resources: android.content.res.Resources,
    blocks: List<SegmentSummary>,
): String = blocks
    .groupBy { it.kind }
    .map { (_, group) ->
        val label = group.first().describe(resources)
        if (group.size == 1) label else "$label ${'×'}${group.size}"
    }
    .joinToString()

/**
 * Takes a `Resources` rather than being `@Composable`, so it can be called from inside a
 * `joinToString` lambda — and for the reason `EmvLabels.kt` documents: one mapping, used everywhere,
 * cannot drift from itself.
 */
private fun SegmentSummary.describe(resources: android.content.res.Resources): String =
    resources.getString(
        when (kind) {
            MetadataKind.Exif -> R.string.exifstrip_kind_exif
            MetadataKind.Orientation -> R.string.exifstrip_kind_orientation
            MetadataKind.Xmp -> R.string.exifstrip_kind_xmp
            MetadataKind.Iptc -> R.string.exifstrip_kind_iptc
            MetadataKind.Comment -> R.string.exifstrip_kind_comment
            MetadataKind.IccProfile -> R.string.exifstrip_kind_icc
            MetadataKind.Timestamp -> R.string.exifstrip_kind_timestamp
            MetadataKind.Unknown -> R.string.exifstrip_kind_unknown
        },
        name,
    )

private const val BYTES_PER_KILOBYTE = 1024
