package com.minion.scaffold.feature.qrcreate.presentation.preview

import android.content.ClipData
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.feature.qrcreate.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Carries out [QrExportEffect]s: clipboard, share sheet, confirmation snackbar.
 *
 * One implementation for every authoring screen. Each format's ViewModel decides *what* to export
 * and this decides *how*, so adding a format does not mean another copy of the clipboard and
 * share-intent plumbing to keep in step.
 */
@Composable
internal fun HandleQrExportEffects(
    effects: Flow<QrExportEffect>,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    // Read in composition so a configuration change re-reads them. Resolving these inside the
    // handler would pin the locale that was active when the screen was first composed.
    val clipboardLabel = stringResource(R.string.qrcreate_clipboard_label)
    val savedMessage = stringResource(R.string.qrcreate_saved)
    val failedMessage = stringResource(R.string.qrcreate_export_failed)

    ObserveAsEvents(effects) { effect ->
        when (effect) {
            is QrExportEffect.CopyText -> coroutineScope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText(clipboardLabel, effect.text)),
                )
            }

            is QrExportEffect.ShareImage -> {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = MIME_TYPE_PNG
                    putExtra(Intent.EXTRA_STREAM, effect.uri)
                    // Without this the receiving app gets a Uri it has no right to read, and the
                    // share silently produces a broken attachment.
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, null))
            }

            is QrExportEffect.ShowExportMessage -> coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    when (effect.outcome) {
                        ExportOutcome.SAVED_TO_GALLERY -> savedMessage
                        ExportOutcome.EXPORT_FAILED -> failedMessage
                    },
                )
            }
        }
    }
}

private const val MIME_TYPE_PNG = "image/png"
