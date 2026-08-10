package com.minion.scaffold.feature.level.domain

import com.minion.scaffold.core.level.model.Calibration
import kotlinx.coroutines.flow.Flow

/**
 * What the level remembers between visits.
 *
 * Only two things, and they are remembered for opposite reasons: the calibration because it
 * describes the *device* and is expensive to redo, and the beeper toggle because a preference the
 * user has to set every time is not a preference.
 *
 * Notably absent is the relative-mode reference. That one is deliberately never persisted — a
 * forgotten reference from yesterday would make every reading today silently wrong, which is the
 * exact failure calibration already has to guard against, and there is no reason to invite it twice.
 */
internal interface LevelPreferencesRepository {

    /** [Calibration.NONE] until a flip has been completed. */
    val calibration: Flow<Calibration>

    /** Off by default — a tool that starts beeping the moment it opens is a hostile tool. */
    val soundEnabled: Flow<Boolean>

    /** Whether the first-use prompt explaining calibration has been dismissed. */
    val calibrationPromptSeen: Flow<Boolean>

    suspend fun setCalibration(calibration: Calibration)

    /** Back to uncalibrated. The escape hatch for a flip taken on a surface that was not flat. */
    suspend fun clearCalibration()

    suspend fun setSoundEnabled(enabled: Boolean)

    suspend fun setCalibrationPromptSeen()
}
