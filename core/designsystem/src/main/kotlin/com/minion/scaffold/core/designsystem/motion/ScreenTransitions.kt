package com.minion.scaffold.core.designsystem.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/**
 * The app's reusable screen transitions.
 *
 * Two sets: a **modal** motion for the camera — up to open, down to close — and a **push** motion
 * for every other screen — in from the right, out to the right. Both come from these primitives, so
 * the direction is defined once here rather than at each navigation call site.
 *
 * These are plain [EnterTransition]/[ExitTransition] values, deliberately free of any navigation
 * type, so the design system stays independent of `navigation-compose`. A caller drops them into a
 * `composable(enterTransition = { slideUpEnter() })` or a `NavHost(...)` default; the navigation
 * scope the lambda receives is simply ignored.
 *
 * The counterpart of each motion — what the screen *underneath* does — is deliberately left as the
 * navigation default of holding still. A screen sliding in over a static one, then sliding off to
 * reveal it, is the whole of a push; animating the background too only muddies the two directions
 * the motion is meant to make legible.
 */

/** Enter bottom-to-top: the camera rising into view. Offsets by a full height, so it starts off-screen. */
fun slideUpEnter(): EnterTransition =
    slideInVertically(animationSpec = tween(DURATION_MILLIS, easing = FastOutSlowInEasing)) { it }

/** Exit top-to-bottom: the camera dropping away as it closes. */
fun slideDownExit(): ExitTransition =
    slideOutVertically(animationSpec = tween(DURATION_MILLIS, easing = FastOutSlowInEasing)) { it }

/** Enter right-to-left: a screen sliding in from the right edge — the standard push. */
fun slideInFromRight(): EnterTransition =
    slideInHorizontally(animationSpec = tween(DURATION_MILLIS, easing = FastOutSlowInEasing)) { it }

/** Exit left-to-right: a screen sliding off to the right as it closes. */
fun slideOutToRight(): ExitTransition =
    slideOutHorizontally(animationSpec = tween(DURATION_MILLIS, easing = FastOutSlowInEasing)) { it }

/**
 * One full-screen transition, near the Material long-transition duration. Long enough to read the
 * direction, short enough not to sit between taps.
 */
private const val DURATION_MILLIS = 800
