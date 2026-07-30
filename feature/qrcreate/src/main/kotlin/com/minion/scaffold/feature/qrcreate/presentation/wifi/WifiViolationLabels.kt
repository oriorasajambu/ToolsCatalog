package com.minion.scaffold.feature.qrcreate.presentation.wifi

import android.content.res.Resources
import com.minion.scaffold.core.wifi.model.WifiViolationReason
import com.minion.scaffold.feature.qrcreate.R

/**
 * Why a Wi-Fi field was rejected, in words.
 *
 * `:core:wifi` reports typed reasons and carries no copy, exactly as `:core:emv` does — so the
 * rules stay testable without string matching and the wording stays translatable.
 */
internal fun WifiViolationReason.describe(resources: Resources): String = resources.getString(
    when (this) {
        WifiViolationReason.REQUIRED -> R.string.wificreate_violation_required
        WifiViolationReason.TOO_LONG -> R.string.wificreate_violation_too_long
        WifiViolationReason.TOO_SHORT -> R.string.wificreate_violation_too_short
        WifiViolationReason.INVALID_WEP_KEY -> R.string.wificreate_violation_invalid_wep_key
    },
)
