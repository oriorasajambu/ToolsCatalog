package com.minion.scaffold.feature.level.presentation

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.level.model.Calibration
import com.minion.scaffold.core.level.model.GravitySample
import com.minion.scaffold.core.level.model.PoseState
import com.minion.scaffold.core.level.model.UpVector
import com.minion.scaffold.core.level.usecase.ApplyCalibrationUseCase
import com.minion.scaffold.core.level.usecase.ComputeTiltUseCase
import com.minion.scaffold.core.level.usecase.DetectStabilityUseCase
import com.minion.scaffold.core.level.usecase.ResolvePoseUseCase
import com.minion.scaffold.core.level.usecase.SmoothGravityUseCase
import com.minion.scaffold.core.level.usecase.SmoothingState
import com.minion.scaffold.core.level.usecase.StabilityState
import com.minion.scaffold.core.level.usecase.Steadiness
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.feature.level.domain.ClearCalibrationUseCase
import com.minion.scaffold.feature.level.domain.DismissCalibrationPromptUseCase
import com.minion.scaffold.feature.level.domain.GravitySource
import com.minion.scaffold.feature.level.domain.ObserveCalibrationPromptSeenUseCase
import com.minion.scaffold.feature.level.domain.ObserveCalibrationUseCase
import com.minion.scaffold.feature.level.domain.ObserveSoundEnabledUseCase
import com.minion.scaffold.feature.level.domain.SetSoundEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Folds the sensor stream through `:core:level` and hands the result to the screen.
 *
 * Everything of substance is a pure function in `:core:level`; what happens here is only the
 * threading of accumulated state from one sample to the next, which is exactly the shape those
 * functions were written for. The filter, the pose machine and the stability detector each hold no
 * state of their own, so the ordering below *is* the pipeline:
 *
 * ```
 * raw → calibrate → smooth → pose → stability → angles
 * ```
 *
 * Calibration comes first so that every consumer downstream — flat, edge, relative and the bubble —
 * is corrected by the same one rotation in the same one place.
 */
@HiltViewModel
internal class LevelViewModel @Inject constructor(
    private val gravitySource: GravitySource,
    private val applyCalibration: ApplyCalibrationUseCase,
    private val smoothGravity: SmoothGravityUseCase,
    private val resolvePose: ResolvePoseUseCase,
    private val detectStability: DetectStabilityUseCase,
    private val computeTilt: ComputeTiltUseCase,
    observeCalibration: ObserveCalibrationUseCase,
    observeSoundEnabled: ObserveSoundEnabledUseCase,
    observeCalibrationPromptSeen: ObserveCalibrationPromptSeenUseCase,
    private val setSoundEnabled: SetSoundEnabledUseCase,
    private val clearCalibration: ClearCalibrationUseCase,
    private val dismissCalibrationPrompt: DismissCalibrationPromptUseCase,
) : MviViewModel<LevelState, LevelIntent, LevelEffect>(LevelState()) {

    private var smoothing = SmoothingState()
    private var poseState = PoseState()
    private var stability = StabilityState()

    /** The most recent calibrated, smoothed reading — what a reference capture would take. */
    private var latestUp: UpVector? = null

    /**
     * How far the device's natural orientation is from the portrait the screen is locked to.
     *
     * The sensor frame is the *natural* orientation, which is portrait on phones but landscape on
     * many tablets and foldables. Without this the bubble on such a device would move sideways when
     * the user tilts forwards.
     */
    private var displayRotationDegrees = 0.0

    init {
        reduce { copy(sensor = gravitySource.sensor) }

        observeCalibration()
            .onEach { calibration -> reduce { copy(calibration = calibration) } }
            .launchIn(viewModelScope)

        observeSoundEnabled()
            .onEach { enabled -> reduce { copy(soundEnabled = enabled) } }
            .launchIn(viewModelScope)

        observeCalibrationPromptSeen()
            .onEach { seen -> reduce { copy(showCalibrationPrompt = !seen) } }
            .launchIn(viewModelScope)

        gravitySource.samples()
            .onEach(::onSample)
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: LevelIntent) {
        when (intent) {
            LevelIntent.FreezeToggled -> reduce {
                copy(frozen = if (frozen == null) tilt else null)
            }

            LevelIntent.ReferenceCaptured -> captureReference()

            LevelIntent.ReferenceCleared -> reduce { copy(reference = null) }

            LevelIntent.SoundToggled -> viewModelScope.launch {
                setSoundEnabled(!currentState.soundEnabled)
            }

            LevelIntent.CalibrationPromptDismissed -> viewModelScope.launch {
                dismissCalibrationPrompt()
            }

            LevelIntent.CalibrationCleared -> viewModelScope.launch {
                clearCalibration()
                emitEffect(LevelEffect.Notice(LevelNotice.CalibrationCleared))
            }

            is LevelIntent.DisplayRotationChanged -> displayRotationDegrees = intent.degrees
        }
    }

    private fun onSample(sample: GravitySample) {
        val calibration = currentState.calibration

        // Calibrate before smoothing, so the filter is working on corrected values and the two
        // cannot disagree about what "level" means.
        val corrected = sample.normalizedOrNull()
            ?.let { applyCalibration(it, calibration) }
            ?: return

        val correctedSample = GravitySample(
            x = corrected.x,
            y = corrected.y,
            z = corrected.z,
            timestampNanos = sample.timestampNanos,
        )

        smoothing = smoothGravity(smoothing, correctedSample)
        val smoothed = smoothing.value?.normalizedOrNull() ?: return

        poseState = resolvePose(poseState, smoothed, sample.timestampNanos)
        stability = detectStability(stability, smoothed, sample.timestampNanos)
        latestUp = smoothed

        // Rotated last, so only what is displayed is turned into the screen's frame; the pose
        // machine and the stability detector keep working in the device's own axes.
        val display = smoothed.rotatedInPlane(displayRotationDegrees)
        val relative = currentState.reference?.let { reference ->
            relativeTo(display, reference)
        }

        reduce {
            copy(
                tilt = computeTilt(relative ?: display),
                pose = poseState.pose,
                steadiness = stability.steadiness,
                bubbleX = display.x,
                bubbleY = display.y,
            )
        }
    }

    /**
     * Captures the current reading as the reference for relative measurement.
     *
     * Refused while moving. A reference is the thing every subsequent number is measured against,
     * so taking one from a reading that is not yet trustworthy poisons everything after it — and
     * unlike a bad live reading, the error does not go away when the phone settles.
     */
    private fun captureReference() {
        val up = latestUp
        if (up == null || currentState.steadiness != Steadiness.Steady) {
            viewModelScope.launch {
                emitEffect(LevelEffect.Notice(LevelNotice.ReferenceNotSteady))
            }
            return
        }

        val display = up.rotatedInPlane(displayRotationDegrees)
        reduce {
            copy(reference = ReferenceCapture(display.x, display.y, display.z))
        }
        viewModelScope.launch {
            emitEffect(LevelEffect.Notice(LevelNotice.ReferenceCaptured))
        }
    }

    /**
     * Re-expresses [up] as if [reference] were level.
     *
     * The rotation that carries the reference onto vertical, applied to the current reading — the
     * same construction the calibration solver uses, which is the point: relative mode *is*
     * calibration with a deliberately temporary lifetime.
     *
     * Not a subtraction of angles. Subtracting would only be right near level and would drift
     * exactly where a relative measurement is most likely to be taken — on something already
     * sloped.
     */
    private fun relativeTo(up: UpVector, reference: ReferenceCapture): UpVector {
        val referenceUp = UpVector(reference.upX, reference.upY, reference.upZ)

        val axisX = -referenceUp.y
        val axisY = referenceUp.x
        val crossMagnitude = kotlin.math.hypot(axisX, axisY)
        if (crossMagnitude < 1e-12) return up

        val angle = kotlin.math.atan2(crossMagnitude, referenceUp.z)
        val scale = angle / crossMagnitude

        return applyCalibration(
            up,
            Calibration.NONE.copy(x = axisX * scale, y = axisY * scale, z = 0.0),
        )
    }
}
