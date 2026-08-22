package com.minion.scaffold.feature.widget.glance

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.material3.ColorProviders
import com.minion.scaffold.core.designsystem.theme.AppColorSchemes

/**
 * The widget's colours.
 *
 * Two paths, for two different jobs:
 *
 *  - **API 31+** takes Glance's dynamic providers, so the widget themes itself to the wallpaper
 *    like every system widget sitting beside it. A home screen is the system's surface, not the
 *    app's, and a brand-coloured rectangle among wallpaper-tinted ones reads as a foreign object.
 *  - **API 29–30** has no dynamic colour, so it bridges the app's own two schemes. Day/night still
 *    follows the system exactly as `AppTheme` does, because the bridge is built from both schemes
 *    rather than from whichever one happened to be current.
 *
 * The schemes come from `:core:designsystem` rather than being restated here. Two independently
 * maintained copies of a palette is how a widget ends up a slightly different shade of the brand
 * than the app it belongs to.
 */
@Composable
internal fun WidgetGlanceTheme(content: @Composable () -> Unit) {
    val colors =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicThemeColorProviders
        } else {
            AppColorProviders
        }

    GlanceTheme(colors = colors, content = content)
}

/**
 * The app's palette, as Glance sees it.
 *
 * Built once at class-init rather than per render: it is derived from two compile-time constants
 * and cannot change while the process lives.
 */
private val AppColorProviders: ColorProviders = ColorProviders(
    light = AppColorSchemes.light,
    dark = AppColorSchemes.dark,
)
