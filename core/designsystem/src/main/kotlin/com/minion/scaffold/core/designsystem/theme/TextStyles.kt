package com.minion.scaffold.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * The type treatments the Material scale has no slot for.
 *
 * Both of these are a Material label style with the tracking opened up — the wide-set, quiet
 * capitals a screen uses to name a section without shouting. They live here rather than in
 * [AppTypography] because `labelMedium` and `labelLarge` are shared by two dozen call sites across
 * the app, and widening the tracking on the slot itself would re-track every one of them to suit
 * one screen.
 *
 * They live here rather than as a `.copy(letterSpacing = 1.6.sp)` in the screen for the reason
 * [AppTypography] gives: a `TextStyle` built inline is a type decision the design system cannot
 * see, so restyling the product stops being a single change.
 */
object AppTextStyles {

    /** The small capitals introducing a card — "READER" over the scanner hero. */
    val eyebrow: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium.copy(letterSpacing = EYEBROW_TRACKING)

    /** The heading standing over a list of rows — "CREATE", "UTILITIES". */
    val sectionHeading: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge.copy(letterSpacing = SECTION_HEADING_TRACKING)
}

private val EYEBROW_TRACKING = 1.6.sp
private val SECTION_HEADING_TRACKING = 1.2.sp
