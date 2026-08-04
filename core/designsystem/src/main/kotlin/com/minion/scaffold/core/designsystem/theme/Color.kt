package com.minion.scaffold.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The raw palette. The only file in the app where a hex literal is allowed to appear.
 *
 * These names describe the *colour*, not its job — `MidnightAccent`, not `primary`. The mapping
 * from colour to role happens once, in the schemes in `Theme.kt`, so a rebrand changes the values
 * here and nothing else.
 *
 * **Midnight Pro** — a near-black ground with a single blurple accent used as both line and glow.
 * Imported from the "ToolsCatalog · Direction 1c — Midnight Pro" design direction; the values are
 * that direction's own CSS variables (`--bg`, `--card`, `--accent`, …) lifted verbatim.
 */

/** `--accent`: the one blurple, used as line, fill and glow. */
internal val MidnightAccent = Color(0xFF8B7DF6)

/** A lighter accent for the rare on-dark-container role. */
internal val MidnightAccentBright = Color(0xFF9C8CFF)

/** `--bg`: the near-black ground everything sits on. */
internal val MidnightBackground = Color(0xFF0E1017)

/** The tonal steps of raised surface, `--bg` up through `--card` (`#171A24`) and a touch beyond. */
internal val MidnightSurfaceLowest = Color(0xFF0F111A)
internal val MidnightSurfaceLow = Color(0xFF14161F)
internal val MidnightSurface = Color(0xFF171A24)
internal val MidnightSurfaceHigh = Color(0xFF191C27)
internal val MidnightSurfaceHighest = Color(0xFF1C1F2B)

/** `--text`: near-white. */
internal val MidnightText = Color(0xFFEEF0F6)

/** `--muted`: supporting text and, as `outline`, the hairline card borders. */
internal val MidnightMuted = Color(0xFF8A90A6)

/** `--line`: the `#242838` card border. */
internal val MidnightLine = Color(0xFF242838)

/** The soft violet an icon tile sits on — `--soft` (rgba accent .15) resolved over the ground. */
internal val MidnightAccentContainer = Color(0xFF211F33)

/** Success — the integrity-passed green from the report card. */
internal val MidnightSuccess = Color(0xFF48C78E)
internal val MidnightSuccessContainer = Color(0xFF12281E)
internal val MidnightOnSuccessContainer = Color(0xFF7BE0AE)

/** Failure — a red that reads on the dark ground. */
internal val MidnightError = Color(0xFFFF5D6C)
internal val MidnightErrorContainer = Color(0xFF33161C)
internal val MidnightOnErrorContainer = Color(0xFFFFB3BB)

/**
 * A coherent violet-on-white light scheme, kept valid but unused by default — the app ships
 * Midnight. It exists so a future light toggle has somewhere to land.
 */
internal val DayAccent = Color(0xFF5B4CD6)
internal val DayAccentContainer = Color(0xFFE7E3FF)
internal val DayOnAccentContainer = Color(0xFF1A1440)
internal val DayBackground = Color(0xFFFBFBFE)
internal val DaySurface = Color(0xFFF3F3F9)
internal val DayText = Color(0xFF14161F)
internal val DayMuted = Color(0xFF5A5F73)
internal val DayLine = Color(0xFFC9CCDA)
internal val DaySuccessContainer = Color(0xFFC9F2DE)
internal val DayOnSuccessContainer = Color(0xFF0B3A26)
internal val DayError = Color(0xFFB3261E)
internal val DayErrorContainer = Color(0xFFF9DEDC)
internal val DayOnErrorContainer = Color(0xFF410E0B)
