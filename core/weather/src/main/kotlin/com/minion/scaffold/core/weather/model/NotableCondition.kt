package com.minion.scaffold.core.weather.model

/**
 * A condition worth calling out on the forecast screen — never called an "alert" or a "warning"
 * anywhere user-facing, because both words imply an official source and this is computed by the
 * app itself. See `EvaluateNotableConditionsUseCase` for the thresholds that produce these.
 */
data class NotableCondition(val kind: Kind, val severity: Severity) {

    enum class Kind { HEAVY_RAIN, HIGH_WIND, EXTREME_HEAT, EXTREME_COLD, SNOW }

    enum class Severity { MODERATE, HIGH }
}
