package com.minion.scaffold.core.data.widget

/**
 * Asks the launcher to place the widget on the home screen.
 *
 * Declared here and bound in `:app` for the same reason `WidgetLaunchIntentFactory` is: naming the
 * widget provider means naming a component in `:feature:widget`, and `:feature:tools` — which owns
 * the configuration screen — may not depend on another feature.
 */
interface WidgetPinRequester {

    /**
     * Whether the current launcher supports being asked at all.
     *
     * Launcher-dependent even above API 26, which is why this is a value to check rather than an
     * assumption. The configuration screen hides its button entirely when this is false: a button
     * that silently does nothing on some launchers is worse than no button.
     */
    val isSupported: Boolean

    /**
     * Asks for the widget to be pinned.
     *
     * Opens a system dialog the user answers. Nothing is returned: the launcher does not report
     * back, and the widget appearing is its own confirmation.
     */
    fun requestPin()
}
