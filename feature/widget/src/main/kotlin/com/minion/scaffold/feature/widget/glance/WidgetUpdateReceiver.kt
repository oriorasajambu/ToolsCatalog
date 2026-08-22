package com.minion.scaffold.feature.widget.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The three system events that make a drawn widget wrong.
 *
 * `ACTION_APPWIDGET_UPDATE` is not among them — [QuickAccessWidgetReceiver] already handles that.
 * These are the ones nothing else would notice:
 *
 *  - **Locale changed.** Tile labels are `@StringRes` resolved at render, and a widget is *not*
 *    re-rendered on a locale switch, so without this they stay in the old language indefinitely.
 *    The same class of bug as the `LocalResources` rule in CLAUDE.md.
 *  - **Package replaced.** An update can remove a tool from the catalog. The reconcile prune has
 *    to run, or a tile with no catalog entry keeps drawing.
 *  - **Boot completed.** Cheap insurance that a device coming back up draws current state.
 *
 * A `goAsync` rather than a plain launch: a broadcast receiver's process may be killed the moment
 * `onReceive` returns, and rendering is suspending work that would not survive it.
 */
internal class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return

        val pending = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.Default).launch {
            try {
                QuickAccessWidget().updateAll(appContext)
            } finally {
                // In a finally: leaving a pending result unfinished holds the process alive until
                // the system times it out, and an ANR from a widget redraw is a poor trade.
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED,
        )
    }
}
