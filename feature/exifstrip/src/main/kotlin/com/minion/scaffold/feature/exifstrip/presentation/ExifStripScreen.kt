package com.minion.scaffold.feature.exifstrip.presentation

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.minion.scaffold.core.exif.model.Exposure
import com.minion.scaffold.core.exif.model.TrailingKind
import com.minion.scaffold.feature.exifstrip.presentation.component.describeBlocks
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.feature.exifstrip.R
import com.minion.scaffold.feature.exifstrip.presentation.component.ExportPanel
import com.minion.scaffold.feature.exifstrip.presentation.component.MetadataSections
import kotlinx.coroutines.launch

/**
 * The metadata stripper screen: pick a photo, see what it reveals, export a clean copy.
 *
 * @param onNavigateBack       Called when the user leaves the stripper.
 * @param onNavigateToSettings Called when the user opens the stripper's settings.
 * @param modifier             The [Modifier] for the screen.
 * @param viewModel            The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun ExifStripScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExifStripViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val noMapApp = stringResource(R.string.exifstrip_notice_no_map_app)

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.onIntent(ExifStripIntent.PhotoPicked(it)) }
    }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is ExifStripEffect.Share -> {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = MIME_TYPE_IMAGE
                    putExtra(Intent.EXTRA_STREAM, effect.uri)
                    // Without this the receiving app gets a Uri it has no permission to open, and
                    // the share silently delivers nothing.
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, null))
            }

            is ExifStripEffect.OpenInMaps -> {
                val geo = "geo:${effect.latitude},${effect.longitude}" +
                    "?q=${effect.latitude},${effect.longitude}"
                val opened = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, geo.toUri()))
                }.isSuccess

                if (!opened) coroutineScope.launch { snackbarHostState.showSnackbar(noMapApp) }
            }

            is ExifStripEffect.Copy -> coroutineScope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText(effect.label, effect.value)),
                )
                snackbarHostState.showSnackbar(
                    resources.getString(R.string.exifstrip_notice_copied),
                )
            }

            is ExifStripEffect.Notice -> coroutineScope.launch {
                snackbarHostState.showSnackbar(resources.getString(effect.notice.messageRes()))
            }
        }
    }

    ExifStripContent(
        state = state,
        onIntent = viewModel::onIntent,
        onPick = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onNavigateBack = onNavigateBack,
        onNavigateToSettings = onNavigateToSettings,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExifStripContent(
    state: ExifStripState,
    onIntent: (ExifStripIntent) -> Unit,
    onPick: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.exifstrip_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.exifstrip_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.exifstrip_navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = stringResource(R.string.exifstrip_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            when (val content = state.content) {
                ExifStripState.Content.Empty -> EmptyState(onPick = onPick)

                ExifStripState.Content.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                is ExifStripState.Content.Loaded -> {
                    AsyncImage(
                        model = content.uri,
                        contentDescription = stringResource(R.string.exifstrip_preview),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(PREVIEW_ASPECT),
                    )

                    Verdict(content)

                    ContainerBlocks(content = content)

                    MetadataSections(
                        metadata = content.metadata,
                        onCopy = { label, value ->
                            onIntent(ExifStripIntent.CopyRequested(label, value))
                        },
                        onOpenInMaps = { latitude, longitude ->
                            onIntent(ExifStripIntent.OpenInMapsRequested(latitude, longitude))
                        },
                    )

                    ExportPanel(
                        state = state,
                        onExport = { onIntent(ExifStripIntent.ExportRequested) },
                        onConvert = { onIntent(ExifStripIntent.ConvertRequested) },
                        onShare = { onIntent(ExifStripIntent.ShareRequested) },
                    )

                    OutlinedButton(
                        onClick = onPick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.exifstrip_pick_another))
                    }
                }

                is ExifStripState.Content.Failed -> FailureCard(
                    reason = content.reason,
                    onPick = onPick,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = dimensionResource(R.dimen.exifstrip_spacing)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(
            text = stringResource(R.string.exifstrip_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.exifstrip_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.Button(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.exifstrip_pick))
        }
    }
}

/**
 * The one-line answer, before any of the detail.
 *
 * Someone opening this tool has one question — "does this photo say where I live?" — and it should
 * be answered in the first screenful rather than at row ninety of a tag dump.
 */
@Composable
private fun Verdict(content: ExifStripState.Content.Loaded, modifier: Modifier = Modifier) {
    val metadata = content.metadata
    val worst = metadata.worstExposure

    // Keyed on `carriesAnything`, not on the Exif reader alone: a PNG whose only metadata is a text
    // chunk has no Exif at all, and saying "no metadata found" about it would be a false
    // reassurance — the one error this tool must never make.
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !content.carriesAnything -> MaterialTheme.colorScheme.secondaryContainer
                worst == Exposure.Identifying -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.tertiaryContainer
            },
        ),
    ) {
        Text(
            text = stringResource(
                when {
                    !content.carriesAnything -> R.string.exifstrip_verdict_clean
                    metadata.coordinates != null -> R.string.exifstrip_verdict_location
                    worst == Exposure.Identifying -> R.string.exifstrip_verdict_identifying
                    !metadata.hasAnything -> R.string.exifstrip_verdict_container_only
                    else -> R.string.exifstrip_verdict_some
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(dimensionResource(R.dimen.exifstrip_spacing)),
        )
    }
}

/**
 * What the container carries that the Exif reader cannot see.
 *
 * Text chunks, comments, XMP packets, vendor blocks, and anything appended past the end of the
 * image. Shown by kind and size rather than by content: the tool reports that a comment is present
 * and removes it, but printing an arbitrary blob into the UI is a different feature with its own
 * hazards.
 */
@Composable
private fun ContainerBlocks(
    content: ExifStripState.Content.Loaded,
    modifier: Modifier = Modifier,
) {
    if (content.containerBlocks.isEmpty() && content.trailing == null) return

    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.exifstrip_spacing)
    val summary = remember(content.containerBlocks) {
        describeBlocks(resources, content.containerBlocks)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.exifstrip_spacing_tight),
            ),
        ) {
            Text(
                text = stringResource(R.string.exifstrip_container_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.exifstrip_container_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.isNotEmpty()) {
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            }
            content.trailing?.let { trailing ->
                Text(
                    text = stringResource(
                        if (trailing.kind == TrailingKind.EmbeddedVideo) {
                            R.string.exifstrip_container_video
                        } else {
                            R.string.exifstrip_container_trailing
                        },
                        trailing.byteCount / 1024,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun FailureCard(
    reason: ExifStripState.FailureReason,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.exifstrip_spacing)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Text(
                text = stringResource(reason.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.exifstrip_pick_another))
            }
        }
    }
}

private fun ExifStripState.FailureReason.messageRes(): Int = when (this) {
    ExifStripState.FailureReason.Unreadable -> R.string.exifstrip_error_unreadable
    ExifStripState.FailureReason.TooLarge -> R.string.exifstrip_error_too_large
    ExifStripState.FailureReason.NotAnImage -> R.string.exifstrip_error_not_an_image
    ExifStripState.FailureReason.VerificationFailed -> R.string.exifstrip_error_verification
    ExifStripState.FailureReason.WriteFailed -> R.string.exifstrip_error_write
}

private fun ExifStripNotice.messageRes(): Int = when (this) {
    ExifStripNotice.Copied -> R.string.exifstrip_notice_copied
    ExifStripNotice.NoMapApp -> R.string.exifstrip_notice_no_map_app
    ExifStripNotice.NothingToRemove -> R.string.exifstrip_notice_nothing_to_remove
}

private const val MIME_TYPE_IMAGE = "image/*"
private const val PREVIEW_ASPECT = 4f / 3f
