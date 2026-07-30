package com.minion.scaffold.feature.qrcreate.presentation.form

import android.content.res.Resources
import com.minion.scaffold.core.emv.model.ViolationReason
import com.minion.scaffold.feature.qrcreate.R

/**
 * Why a field was rejected, in words.
 *
 * Mirrors how the scan tool turns a `QrParseError` into a message: `:core:emv` reports typed
 * reasons and carries no copy, so the wording stays translatable and the rules stay testable
 * without string matching.
 *
 * Takes a [Resources] rather than being `@Composable` for the same reason the scan tool's labels
 * do — read `LocalResources.current` at the call site, never `LocalContext.current.getString()`,
 * which would not follow a locale change.
 */
internal fun ViolationReason.describe(resources: Resources): String = resources.getString(
    when (this) {
        ViolationReason.REQUIRED -> R.string.qrcreate_violation_required
        ViolationReason.TOO_LONG -> R.string.qrcreate_violation_too_long
        ViolationReason.WRONG_LENGTH -> R.string.qrcreate_violation_wrong_length
        ViolationReason.NOT_NUMERIC -> R.string.qrcreate_violation_not_numeric
        ViolationReason.NOT_AN_AMOUNT -> R.string.qrcreate_violation_not_an_amount
        ViolationReason.NOT_ALLOWED -> R.string.qrcreate_violation_not_allowed
        ViolationReason.UNSUPPORTED -> R.string.qrcreate_violation_unsupported
        ViolationReason.OUT_OF_RANGE -> R.string.qrcreate_violation_out_of_range
    },
)
