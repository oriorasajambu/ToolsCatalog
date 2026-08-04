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
 * **Signal** — the app's light scheme: a bright mint ground, white cards on soft green lines, and a
 * single emerald accent for tiles, fills and the featured scan card. Imported from the
 * "ToolsCatalog · Direction 1b — Signal" design direction; the values are that direction's own CSS
 * variables (`--bg`, `--card`, `--accent`, …) lifted verbatim. Kept valid but not the default — the
 * app ships Midnight — so a light toggle has somewhere real to land.
 */

/** `--accent`: the one emerald, used as tile, fill and the featured card's ground. */
internal val SignalAccent = Color(0xFF0E9E7E)

/** The gradient's far stop and the link-hover — a deeper teal for the on-light tertiary role. */
internal val SignalAccentDeep = Color(0xFF0B8F86)

/** A light emerald for the inverse (on-dark) primary role. */
internal val SignalAccentBright = Color(0xFF5FD3B4)

/** `--bg`: the mint ground everything sits on. */
internal val SignalBackground = Color(0xFFF1F7F4)

/** The tonal steps of raised surface — `--card` white, cooling by a whisper toward the ground. */
internal val SignalSurfaceLowest = Color(0xFFFFFFFF)
internal val SignalSurfaceLow = Color(0xFFFFFFFF)
internal val SignalSurface = Color(0xFFFFFFFF)
internal val SignalSurfaceHigh = Color(0xFFFBFDFC)
internal val SignalSurfaceHighest = Color(0xFFF7FBF9)

/** `--text`: near-black with a green cast. */
internal val SignalText = Color(0xFF0C1F1A)

/** `--muted`: supporting text and, as `onSurfaceVariant`, the hairline card borders. */
internal val SignalMuted = Color(0xFF5F776F)

/** `--line`: the `#E3EEE9` card border. */
internal val SignalLine = Color(0xFFE3EEE9)

/** `--soft`: the mint an icon tile sits on. */
internal val SignalAccentContainer = Color(0xFFDDF4EC)

/** Success — the integrity-passed green from the report card (bg, border and title). */
internal val SignalSuccess = Color(0xFF137A5B)
internal val SignalSuccessContainer = Color(0xFFE4F6EE)
internal val SignalOnSuccessContainer = Color(0xFF0B7C57)

/** Failure — a red that reads on the mint-white ground. */
internal val SignalError = Color(0xFFB3261E)
internal val SignalErrorContainer = Color(0xFFF9DEDC)
internal val SignalOnErrorContainer = Color(0xFF410E0B)
