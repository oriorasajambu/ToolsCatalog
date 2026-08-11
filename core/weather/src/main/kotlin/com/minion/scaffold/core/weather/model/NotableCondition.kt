package com.minion.scaffold.core.weather.model

/**
 * A condition worth calling out on the forecast screen — never called an "alert" or a "warning"
 * anywhere user-facing, because both words imply an official source and this is computed by the
 * app itself. See `EvaluateNotableConditionsUseCase` for the thresholds that produce these.
 *
 * @property kind     What the condition is.
 * @property severity How pronounced it is.
 */
data class NotableCondition(val kind: Kind, val severity: Severity) {

    /** The kind of condition being called out. */
    enum class Kind { HEAVY_RAIN, HIGH_WIND, EXTREME_HEAT, EXTREME_COLD, SNOW }

    /** How pronounced a [NotableCondition] is. */
    enum class Severity { MODERATE, HIGH }
}
