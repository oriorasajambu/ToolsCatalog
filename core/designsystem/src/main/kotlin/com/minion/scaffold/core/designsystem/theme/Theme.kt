package com.minion.scaffold.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
    inversePrimary = SignalAccent,

    scrim = Color.Black,
)

/**
 * **Signal** — the app's light scheme, mapped from the "Direction 1b — Signal" variables the same
 * way Midnight is: the emerald `--accent` → [primary] (drawing white as `onPrimary`, so a fill
 * reads as a solid emerald button); `--soft` → the accent-container the icon tiles sit on, with the
 * accent itself as `onPrimaryContainer` for the glyph; `--card` white → the `surfaceContainer`
 * family and `--bg` mint → [background]/[surface]; `--muted` → [onSurfaceVariant], which also tints
 * the hairline borders, and `--line` → [outline]. The report card's integrity-passed green is
 * [secondaryContainer], a failed check [errorContainer] — the same two roles Midnight uses, so
 * those cards come out green/red here without touching a screen.
 */
private val SignalColorScheme = lightColorScheme(
    primary = SignalAccent,
    onPrimary = Color.White,
    primaryContainer = SignalAccentContainer,
    onPrimaryContainer = SignalAccent,

    secondary = SignalSuccess,
    onSecondary = Color.White,
    secondaryContainer = SignalSuccessContainer,
    onSecondaryContainer = SignalOnSuccessContainer,

    tertiary = SignalAccentDeep,
    onTertiary = Color.White,
    tertiaryContainer = SignalAccentContainer,
    onTertiaryContainer = SignalAccentDeep,

    background = SignalBackground,
    onBackground = SignalText,
    surface = SignalBackground,
    onSurface = SignalText,

    surfaceContainerLowest = SignalSurfaceLowest,
    surfaceContainerLow = SignalSurfaceLow,
    surfaceContainer = SignalSurface,
    surfaceContainerHigh = SignalSurfaceHigh,
    surfaceContainerHighest = SignalSurfaceHighest,
    surfaceVariant = SignalSurface,
    onSurfaceVariant = SignalMuted,

    outline = SignalLine,
    outlineVariant = SignalLine,

    error = SignalError,
    onError = Color.White,
    errorContainer = SignalErrorContainer,
    onErrorContainer = SignalOnErrorContainer,

    inverseSurface = SignalText,
    inverseOnSurface = SignalBackground,
    inversePrimary = SignalAccentBright,

    scrim = Color.Black,
)

/**
 * An ordered set of background tints for highlighting distinct items — the payload tags on the QR
 * create screen paint one band per tag from this, cycling when there are more tags than colours.
 *
 * Kept outside `ColorScheme` because it is a *list*, not a role: the Material scheme has no slot for
 * "the eight interchangeable accents", and forcing these into `tertiary` and friends would give them
 * meanings they do not have. Read via [LocalTagHighlightPalette].
 *
 * @property bands The tint colours in order; glyphs drawn over them stay `onSurface` for contrast.
 */
data class TagHighlightPalette(val bands: List<Color>)

/**
 * The active [TagHighlightPalette]. Provided by [AppTheme] per light/dark scheme; the empty default
 * only applies outside a theme, where a consumer falls back to plain text.
 */
val LocalTagHighlightPalette = staticCompositionLocalOf { TagHighlightPalette(emptyList()) }

/**
 * A colour for each of [count] items, in order — the assignment the QR screens share so a tag reads
 * as the same colour whether it is scanned or created.
 *
 * Cycles [TagHighlightPalette.bands] and nudges any slot that would repeat its predecessor's, so
 * consecutive items never share a colour even past the eighth. Empty when there are no bands
 * (outside a theme), which the caller reads as "fall back to plain text".
 *
 * @param count How many items need a colour, in the order they appear.
 * @return [count] colours, index-aligned to the items; empty if [bands] is empty.
 */
fun TagHighlightPalette.cycle(count: Int): List<Color> {
    if (bands.isEmpty()) return emptyList()

    val result = ArrayList<Color>(count)
    var previous = -1
    repeat(count) { index ->
        var slot = index % bands.size
        if (slot == previous && bands.size > 1) slot = (slot + 1) % bands.size
        result += bands[slot]
        previous = slot
    }
    return result
}

private val MidnightTagPalette = TagHighlightPalette(
    listOf(
        MidnightTag1, MidnightTag2, MidnightTag3, MidnightTag4,
        MidnightTag5, MidnightTag6, MidnightTag7, MidnightTag8,
    ),
)

private val SignalTagPalette = TagHighlightPalette(
    listOf(
        SignalTag1, SignalTag2, SignalTag3, SignalTag4,
        SignalTag5, SignalTag6, SignalTag7, SignalTag8,
    ),
)

/**
 * The two schemes [AppTheme] installs, for surfaces that cannot call it.
 *
 * The home-screen widget is the reason this exists. Glance renders a `RemoteViews` tree outside
 * any `MaterialTheme`, so it builds its own `ColorProviders` — but from *these* schemes, so the
 * widget and the app cannot drift apart. Two independently maintained copies of a palette is how
 * a widget ends up a slightly different shade of the brand than the app it belongs to.
 *
 * Exposed as `ColorScheme` rather than as anything Glance-shaped on purpose: this module owns
 * tokens, and taking a widget toolkit onto its classpath would put Glance on every consumer of the
 * design system. The conversion belongs to the one module that draws a widget.
 *
 * Anything drawing inside the app should read `MaterialTheme.colorScheme` and never touch this.
 */
object AppColorSchemes {

    /** Signal — the light direction. */
    val light: ColorScheme get() = SignalColorScheme

    /** Midnight — the dark direction. */
    val dark: ColorScheme get() = MidnightColorScheme
}

/**
 * The single theme wrapper. Everything the app draws sits inside exactly one of these, applied once
 * in `MainActivity`.
 *
 * Follows the **system** light/dark setting — Midnight in dark, Signal in light — and never uses
 * dynamic colour: the design is a deliberate brand direction, and dynamic colour (wallpaper-derived)
 * would discard it, which is exactly the case the Material guidance flags for a product with a
 * strong accent. Both schemes are hand-mapped from their design directions, so following the system
 * switches between two intentional brands rather than one brand and a tinted fallback. A caller may
 * still pin `darkTheme` explicitly to force a fixed scheme; a `@Preview` that leaves it unset picks
 * up the preview's own light/dark configuration.
 *
 * @param darkTheme whether to use Midnight; defaults to the system setting via [isSystemInDarkTheme].
 * @param content the app.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTagHighlightPalette provides if (darkTheme) MidnightTagPalette else SignalTagPalette,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) MidnightColorScheme else SignalColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
