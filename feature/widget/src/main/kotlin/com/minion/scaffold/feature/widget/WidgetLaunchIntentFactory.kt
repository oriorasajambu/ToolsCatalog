package com.minion.scaffold.feature.widget

import android.content.Intent

/**
 * Builds the intent a widget tile fires.
 *
 * Declared here and bound in `:app`, because a feature module may not depend on `:app` and so
 * cannot name `MainActivity`. The widget knows only "the thing that opens a tool" — the same shape
 * that keeps Firebase out of every feature behind `FeatureFlagRepository`.
 *
 * An explicit component intent rather than a deep link: a deep link needs `MainActivity` to accept
 * an app-scheme VIEW intent, which is a public surface any installed app can fire, plus a
 * `deepLinks` declaration on every route.
 *
 * A `fun interface` so `:app` binds it with a lambda and a test substitutes one.
 */
fun interface WidgetLaunchIntentFactory {

    /**
     * The intent that opens [toolId], or the tools home when it is `null`.
     *
     * `null` is what an unavailable tile fires. Resolving it to the tools home rather than to
     * nothing means a greyed tile still does something explicable when tapped.
     *
     * @param toolId The id of the tool to open, or `null` for the tools home.
     * @return An explicit intent naming this app's own entry activity.
     */
    fun intentFor(toolId: String?): Intent
}
