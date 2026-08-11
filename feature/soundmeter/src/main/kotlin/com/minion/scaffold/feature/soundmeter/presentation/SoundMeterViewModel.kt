package com.minion.scaffold.feature.soundmeter.presentation

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.sound.model.BlockLevel
import com.minion.scaffold.core.sound.model.SessionState
import com.minion.scaffold.core.sound.model.SessionStats
import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.core.sound.model.TimeWeighting
import com.minion.scaffold.core.sound.model.Weighting
import com.minion.scaffold.core.sound.usecase.AccumulateSessionUseCase
import com.minion.scaffold.core.sound.usecase.ApplyTimeWeightingUseCase
import com.minion.scaffold.core.sound.usecase.ComputeBlockLevelUseCase
import com.minion.scaffold.core.sound.usecase.TimeWeightingState
import com.minion.scaffold.core.sound.usecase.WeightingFilter
import com.minion.scaffold.core.sound.usecase.WeightingFilterFactory
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.feature.soundmeter.domain.AudioBlock
import com.minion.scaffold.feature.soundmeter.domain.AudioSource
import com.minion.scaffold.feature.soundmeter.domain.CaptureEvent
import com.minion.scaffold.feature.soundmeter.domain.CaptureQuality
import com.minion.scaffold.feature.soundmeter.domain.ObserveSoundPreferencesUseCase
import com.minion.scaffold.feature.soundmeter.domain.SetTimeWeightingUseCase
import com.minion.scaffold.feature.soundmeter.domain.SetWeightingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Folds the audio stream through `:core:sound` and hands the result to the screen.
 *
 * Everything of substance is a pure function in `:core:sound`; what happens here is the threading of
 * accumulated state from one block to the next, plus the two things that are genuinely presentation
 * concerns — how fast the chart is fed, and how long an out-of-range condition has to persist before
 * the readout admits it.
 *
 * ```
 * block → weight → level → time-weight → display
 *                     └──→ session accumulator
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SoundMeterViewModel @Inject constructor(
    private val audioSource: AudioSource,
    private val filterFactory: WeightingFilterFactory,
    private val computeBlockLevel: ComputeBlockLevelUseCase,
    private val applyTimeWeighting: ApplyTimeWeightingUseCase,
    private val accumulateSession: AccumulateSessionUseCase,
    observeSoundPreferences: ObserveSoundPreferencesUseCase,
    private val setWeighting: SetWeightingUseCase,
    private val setTimeWeighting: SetTimeWeightingUseCase,
) : MviViewModel<SoundMeterState, SoundMeterIntent, SoundMeterEffect>(SoundMeterState()) {

    /** Whether the screen is on show. The microphone follows this, not the ViewModel's lifetime. */
    private val screenVisible = MutableStateFlow(false)

    /**
     * Whether the microphone may be opened at all.
     *
     * Kept beside [screenVisible] and combined with it, rather than letting the capture be attempted
     * and report back that it could not open. Found on device: opening without permission produced a
     * `Failed` event, which the status line rendered as "the microphone stopped responding" —
     * directly underneath the card explaining that access had been denied. Two contradictory
     * explanations of one situation, and the more prominent one was wrong.
     */
    private val permissionGranted = MutableStateFlow(false)

    private var filter: WeightingFilter? = null
    private var smoothing = TimeWeightingState()
    private var session = SessionState()

    /** Scratch for the weighted block, sized on the first block and reused after that. */
    private var weighted = DoubleArray(0)

    private val history = ArrayDeque<Double?>()
    private var historyBucketSeconds = 0.0
    private var historyBucketPeak: Double? = null
    private var historyBucketMeasurable = false

    /**
     * Consecutive blocks agreeing that the input is out of range, in each direction.
     *
     * A dwell rather than an immediate switch. Blocks arrive every 21 ms, so a single stray clipped
     * sample would otherwise make the whole readout flash "too loud" for one frame — the audio
     * equivalent of the flicker the level's pose machine needed a Schmitt trigger and a dwell to
     * fix. Asymmetric on purpose: quick to warn, slower to clear, because being told the reading is
     * untrustworthy a moment too long is harmless and the reverse is not.
     */
    private var outOfRangeBlocks = 0
    private var inRangeBlocks = 0
    private var latchedOutOfRange: SoundMeterState.Reading? = null

    private var processedInputNoticed = false

    init {
        combine(
            observeSoundPreferences.weighting,
            observeSoundPreferences.timeWeighting,
            observeSoundPreferences.offsetDb,
        ) { weighting, timeWeighting, offsetDb ->
            Triple(weighting, timeWeighting, offsetDb)
        }
            .onEach { (weighting, timeWeighting, offsetDb) ->
                // The filter is bound to a weighting, so a change means a new one. Dropping it here
                // and rebuilding on the next block keeps the sample rate — which only the capture
                // knows — out of this decision.
                if (weighting != currentState.weighting) filter = null

                reduce {
                    copy(
                        weighting = weighting,
                        timeWeighting = timeWeighting,
                        offsetDb = offsetDb,
                    )
                }
            }
            .launchIn(viewModelScope)

        // Gated on visibility rather than collected outright. `viewModelScope` outlives the screen,
        // so a bare `launchIn` would hold the microphone open with the phone in a pocket.
        // `flatMapLatest` tears the upstream down on pause, which releases the recorder through the
        // flow's own `awaitClose`.
        combine(screenVisible, permissionGranted) { visible, granted -> visible && granted }
            .distinctUntilChanged()
            .flatMapLatest { canCapture -> if (canCapture) audioSource.capture() else emptyFlow() }
            .onEach(::onCaptureEvent)
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: SoundMeterIntent) {
        when (intent) {
            SoundMeterIntent.ScreenResumed -> screenVisible.value = true

            SoundMeterIntent.ScreenPaused -> {
                screenVisible.value = false
                resetSignalState()
                reduce {
                    copy(
                        capturing = false,
                        silenced = false,
                        reading = SoundMeterState.Reading.Idle,
                    )
                }
            }

            is SoundMeterIntent.PermissionResult -> {
                permissionGranted.value = intent.granted

                reduce {
                    copy(
                        permission = PermissionState.resolve(
                            granted = intent.granted,
                            shouldShowRationale = intent.shouldShowRationale,
                        ),
                        // Without this the gauge sits on "Listening…" underneath a card saying
                        // access was refused, which is simply untrue.
                        reading = if (intent.granted) reading else SoundMeterState.Reading.Idle,
                    )
                }
            }

            SoundMeterIntent.AppSettingsRequested -> emit(SoundMeterEffect.OpenAppSettings)

            SoundMeterIntent.StartPressed -> {
                session = SessionState()
                clearHistory()
                reduce {
                    copy(measuring = true, stats = SessionStats.EMPTY, history = emptyList())
                }
            }

            SoundMeterIntent.StopPressed -> {
                reduce { copy(measuring = false) }
                if (!currentState.stats.hasMeasurement) {
                    emit(SoundMeterEffect.Notice(SoundMeterNotice.NothingMeasured))
                }
            }

            SoundMeterIntent.ResetPressed -> {
                session = SessionState()
                clearHistory()
                reduce { copy(stats = SessionStats.EMPTY, history = emptyList()) }
                emit(SoundMeterEffect.Notice(SoundMeterNotice.SessionReset))
            }

            is SoundMeterIntent.WeightingChanged -> viewModelScope.launch {
                setWeighting(intent.weighting)
            }

            is SoundMeterIntent.TimeWeightingChanged -> viewModelScope.launch {
                setTimeWeighting(intent.timeWeighting)
            }

            SoundMeterIntent.CopySummaryRequested -> emit(SoundMeterEffect.CopySummary)

            SoundMeterIntent.ShareSummaryRequested -> emit(SoundMeterEffect.ShareSummary)
        }
    }

    private fun onCaptureEvent(event: CaptureEvent) {
        when (event) {
            is CaptureEvent.Started -> {
                resetSignalState()
                reduce {
                    copy(
                        capturing = true,
                        failure = null,
                        quality = event.quality,
                        reading = SoundMeterState.Reading.Waiting,
                    )
                }

                // Once per screen instance rather than once per resume: a warning repeated every
                // time the user glances away and back is noise rather than information.
                if (!processedInputNoticed && event.quality == CaptureQuality.Processed) {
                    processedInputNoticed = true
                    emit(SoundMeterEffect.Notice(SoundMeterNotice.ProcessedInput))
                }
            }

            is CaptureEvent.Captured -> onBlock(event.block)

            is CaptureEvent.Silenced -> reduce {
                copy(
                    silenced = event.silenced,
                    reading = if (event.silenced) SoundMeterState.Reading.Idle else reading,
                )
            }

            is CaptureEvent.Failed -> reduce {
                copy(
                    failure = event.reason,
                    capturing = false,
                    reading = SoundMeterState.Reading.Idle,
                )
            }
        }
    }

    private fun onBlock(block: AudioBlock) {
        // Silence handed over by the system while another app holds the microphone is not a
        // measurement of anything, and folding it in would drag Leq down for the whole session.
        if (currentState.silenced) return

        val activeFilter = filter
            ?.takeIf { it.sampleRate == block.sampleRate && it.weighting == currentState.weighting }
            ?: filterFactory.create(currentState.weighting, block.sampleRate)
                .also { filter = it }

        if (weighted.size < block.count) weighted = DoubleArray(block.count)
        activeFilter.process(block.samples, block.count, weighted)

        val level = computeBlockLevel(
            raw = block.samples,
            weighted = weighted,
            count = block.count,
            offsetDb = SoundReference.offsetDb(currentState.offsetDb),
        )

        val displayed = when (level) {
            is BlockLevel.Measured -> {
                smoothing = applyTimeWeighting(
                    state = smoothing,
                    blockDbSpl = level.dbSpl,
                    timeWeighting = currentState.timeWeighting,
                    blockSeconds = block.seconds,
                )
                smoothing.levelDbSpl
            }

            // The smoothing is *not* advanced through an out-of-range block. Feeding it a level it
            // knows to be wrong would leave the average contaminated for a time constant afterwards,
            // long after the reading claimed to be trustworthy again.
            else -> smoothing.levelDbSpl
        }

        if (currentState.measuring) {
            session = accumulateSession(session, level, displayed, block.seconds)
        }

        val reading = resolveReading(level, displayed)

        // Resolved out here rather than inside `reduce`, where `history` would bind to the state's
        // own property instead of the deque and quietly copy the list back onto itself.
        val chart = if (accumulateHistory(level, displayed, block.seconds)) {
            history.toList()
        } else {
            null
        }
        val measuring = currentState.measuring

        reduce {
            copy(
                reading = reading,
                stats = if (measuring) session.toStats() else stats,
                history = chart ?: this.history,
            )
        }
    }

    /**
     * Decides what the readout says, with a dwell in each direction.
     *
     * See [outOfRangeBlocks] for why this is not a straight `when` over the block's verdict.
     */
    private fun resolveReading(
        level: BlockLevel,
        displayed: Double?,
    ): SoundMeterState.Reading {
        val outOfRange = when (level) {
            BlockLevel.Clipped -> SoundMeterState.Reading.TooLoud
            BlockLevel.BelowFloor -> SoundMeterState.Reading.TooQuiet
            is BlockLevel.Measured -> null
        }

        if (outOfRange != null) {
            inRangeBlocks = 0
            outOfRangeBlocks++
            if (outOfRangeBlocks >= OUT_OF_RANGE_BLOCKS) latchedOutOfRange = outOfRange
        } else {
            outOfRangeBlocks = 0
            inRangeBlocks++
            if (inRangeBlocks >= IN_RANGE_BLOCKS) latchedOutOfRange = null
        }

        latchedOutOfRange?.let { return it }

        return displayed
            ?.let { SoundMeterState.Reading.Level(it) }
            ?: SoundMeterState.Reading.Waiting
    }

    /**
     * Feeds the chart at a tenth of the block rate.
     *
     * Each point is the **peak** of its bucket rather than the mean. A minute-long chart at one
     * point per 100 ms is already throwing away nine tenths of the blocks, and averaging them would
     * throw away the events — a door slam lasting 80 ms is exactly what someone scrolling back
     * through the strip is looking for, and a mean would flatten it into the background.
     *
     * The chart runs whether or not a session is being measured: it is a rolling window on the last
     * minute, not a session statistic, and it is the thing that answers "is this room steadily noisy
     * or does it spike" before anyone presses anything.
     *
     * @return whether a point was completed, and so whether the state needs the new list.
     */
    private fun accumulateHistory(
        level: BlockLevel,
        displayed: Double?,
        blockSeconds: Double,
    ): Boolean {
        if (level is BlockLevel.Measured && displayed != null) {
            historyBucketMeasurable = true
            historyBucketPeak = maxOf(historyBucketPeak ?: displayed, displayed)
        }

        historyBucketSeconds += blockSeconds
        if (historyBucketSeconds < SoundMeterState.HISTORY_INTERVAL_SECONDS) return false

        history.addLast(if (historyBucketMeasurable) historyBucketPeak else null)
        while (history.size > SoundMeterState.HISTORY_POINTS) history.removeFirst()

        historyBucketSeconds = 0.0
        historyBucketPeak = null
        historyBucketMeasurable = false
        return true
    }

    private fun clearHistory() {
        history.clear()
        historyBucketSeconds = 0.0
        historyBucketPeak = null
        historyBucketMeasurable = false
    }

    /**
     * Drops everything derived from the signal.
     *
     * The filter has a delay line and the smoothing has a value; carrying either across a gap would
     * make the first reading of a new visit partly a memory of the last one.
     */
    private fun resetSignalState() {
        filter = null
        smoothing = TimeWeightingState()
        outOfRangeBlocks = 0
        inRangeBlocks = 0
        latchedOutOfRange = null
    }

    private fun emit(effect: SoundMeterEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }

    private companion object {

        /** ~64 ms of agreement before the readout says it is out of range. */
        const val OUT_OF_RANGE_BLOCKS = 3

        /** ~210 ms of agreement before it takes it back. */
        const val IN_RANGE_BLOCKS = 10
    }
}
