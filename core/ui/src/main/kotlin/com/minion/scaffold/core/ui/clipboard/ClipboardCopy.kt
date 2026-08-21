package com.minion.scaffold.core.ui.clipboard

import android.content.ClipData
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.ui.R
import kotlinx.coroutines.launch

/**
 * A `(String) -> Unit` that copies text and says so.
 *
 * Copying is the one action in this app with no visible result: the text goes somewhere the user
 * cannot see, the button does not change, and nothing moves. Without a confirmation the only way to
 * find out whether the tap registered is to go and paste somewhere. So the snackbar is not polish
 * here — it is the entire feedback for the action.
 *
 * Lives in `:core:ui` because six features copy something. `Clipboard.setClipEntry` suspends, so a
 * plain function cannot call it — hence a remembered lambda that launches on the composition's
 * scope.
 *
 * On API 33 and above the system shows its own clipboard confirmation as well, so a copy is
 * acknowledged twice there. That is the deliberate trade: the system chip does not exist below 33,
 * and it is the platform's to show or withhold, while this is ours and is always present.
 *
 * @param snackbarHostState Where the confirmation is shown; the screen's own host.
 * @param label             The clipboard entry's label, as other apps see it.
 * @param confirmation      What the snackbar says. Defaults to the shared wording; pass a more
 *                          specific message where the screen copies one particular thing.
 * @return A callback that copies the given text and confirms it.
 */
@Composable
fun rememberClipboardCopy(
    snackbarHostState: SnackbarHostState,
    label: String,
    confirmation: String = stringResource(R.string.copied_to_clipboard),
): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    return remember(clipboard, snackbarHostState, label, confirmation) {
        { text ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
                snackbarHostState.showSnackbar(confirmation)
            }
        }
    }
}
