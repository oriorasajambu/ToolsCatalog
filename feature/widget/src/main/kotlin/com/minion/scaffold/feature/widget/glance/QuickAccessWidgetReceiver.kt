package com.minion.scaffold.feature.widget.glance

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The manifest component the launcher talks to.
 *
 * Deliberately empty beyond naming the widget. `ACTION_APPWIDGET_UPDATE` is handled by
 * [GlanceAppWidgetReceiver] itself, and everything else the widget reacts to arrives through its
 * own receiver rather than being bolted on here.
 *
 * Not `@AndroidEntryPoint`: a broadcast receiver named in the manifest is constructed by the
 * framework, so nothing can be injected into it. The widget reaches the graph through
 * `WidgetEntryPoint` instead, at render time.
 */
internal class QuickAccessWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = QuickAccessWidget()
}
