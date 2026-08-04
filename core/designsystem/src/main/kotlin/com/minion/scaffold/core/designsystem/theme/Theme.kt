package com.minion.scaffold.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * **Midnight Pro** — the app's identity scheme.
 *
 * Maps the design direction's variables onto the Material roles once, so every screen inherits the
 * near-black ground and blurple accent by reading `MaterialTheme.colorScheme` — no screen names a
 * colour. The mapping worth knowing:
 *
 * - `--accent` → [primary]; buttons draw `onPrimary` (the dark ground) as their label, so an accent
 *   fill reads as a bright chip rather than white-on-violet.
 * - `--card` → the `surfaceContainer` family, `--bg` → [background]/[surface].
 * - `--muted` → [onSurfaceVariant], which is also what Material tints hairline borders with.
 * - The integrity-passed green is [secondaryContainer]; a failed check stays [errorContainer]. The
 *   report cards already branch on exactly those two roles, so they come out green/red unchanged.
 */
private val MidnightColorScheme = darkColorScheme(
    primary = MidnightAccent,
    onPrimary = MidnightBackground,
    primaryContainer = MidnightAccentContainer,
    onPrimaryContainer = MidnightAccent,

    secondary = MidnightSuccess,
    onSecondary = MidnightBackground,
    secondaryContainer = MidnightSuccessContainer,
    onSecondaryContainer = MidnightOnSuccessContainer,

    tertiary = MidnightAccentBright,
    onTertiary = MidnightBackground,
    tertiaryContainer = MidnightAccentContainer,
    onTertiaryContainer = MidnightAccentBright,

    background = MidnightBackground,
    onBackground = MidnightText,
    surface = MidnightBackground,
    onSurface = MidnightText,

    surfaceContainerLowest = MidnightSurfaceLowest,
    surfaceContainerLow = MidnightSurfaceLow,
    surfaceContainer = MidnightSurface,
    surfaceContainerHigh = MidnightSurfaceHigh,
    surfaceContainerHighest = MidnightSurfaceHighest,
    surfaceVariant = MidnightSurface,
    onSurfaceVariant = MidnightMuted,

    outline = MidnightLine,
    outlineVariant = MidnightLine,

    error = MidnightError,
    onError = MidnightBackground,
    errorContainer = MidnightErrorContainer,
    onErrorContainer = MidnightOnErrorContainer,

    inverseSurface = MidnightText,
    inverseOnSurface = MidnightBackground,
    inversePrimary = DayAccent,

    scrim = Color.Black,
)

/** The day scheme, kept coherent but unused while the app defaults to Midnight. */
private val DayColorScheme = lightColorScheme(
    primary = DayAccent,
    onPrimary = Color.White,
    primaryContainer = DayAccentContainer,
    onPrimaryContainer = DayOnAccentContainer,
    secondaryContainer = DaySuccessContainer,
    onSecondaryContainer = DayOnSuccessContainer,
    background = DayBackground,
    onBackground = DayText,
    surface = DayBackground,
    onSurface = DayText,
    surfaceVariant = DaySurface,
    onSurfaceVariant = DayMuted,
    outline = DayLine,
    outlineVariant = DayLine,
    error = DayError,
    errorContainer = DayErrorContainer,
    onErrorContainer = DayOnErrorContainer,
)

/**
 * The single theme wrapper. Everything the app draws sits inside exactly one of these, applied once
 * in `MainActivity`.
 *
 * Defaults to **Midnight, no dynamic colour** — the design is a deliberate brand direction, and
 * dynamic colour (wallpaper-derived) would discard it, which is exactly the case the Material
 * guidance flags for a product with a strong accent. A caller may still pass `darkTheme = false`
 * to preview the day scheme.
 *
 * @param darkTheme whether to use Midnight; on by default, so the whole app is Midnight.
 * @param content the app.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MidnightColorScheme else DayColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
