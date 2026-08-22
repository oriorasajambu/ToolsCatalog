package com.minion.scaffold.feature.widget

/**
 * Redraws every instance of the widget.
 *
 * An interface so the Glance types stay inside this module. `:app` has to be able to ask for a
 * redraw — it is where the feature-flag stream is collected — but it has no business knowing that
 * the widget is drawn with Glance at all.
 *
 * Every call is deliberate. Nothing on the widget is time-varying, so `updatePeriodMillis` is zero
 * and this is the only thing that makes it change.
 */
interface WidgetUpdater {

    /**
     * Re-renders all placed instances.
     *
     * Suspends until the render completes. A no-op where none are placed.
     */
    suspend fun updateAll()
}
