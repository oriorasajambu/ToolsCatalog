package com.minion.scaffold.feature.texttools.presentation

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.feature.texttools.R
import kotlinx.coroutines.launch

/**
 * A `(String) -> Unit` that puts text on the clipboard.
 *
 * `Clipboard.setClipEntry` suspends, so a plain function cannot call it — hence a remembered lambda
 * that launches on the composition's scope. Both screens in this feature copy, which is why it is a
 * helper rather than the same six lines twice.
 *
 * @return A callback that copies the given text to the clipboard.
 */
@Composable
internal fun rememberClipboardCopy(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.texttools_clipboard_label)

    return remember(clipboard, label) {
        { text ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
            }
        }
    }
}
