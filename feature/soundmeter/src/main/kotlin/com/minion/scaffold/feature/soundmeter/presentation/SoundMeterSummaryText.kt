package com.minion.scaffold.feature.soundmeter.presentation

import android.content.res.Resources
import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.core.sound.model.TimeWeighting
import com.minion.scaffold.core.sound.model.Weighting
import com.minion.scaffold.feature.soundmeter.R
import com.minion.scaffold.feature.soundmeter.domain.CaptureQuality

/**
 * The session as plain text, for the clipboard and the share sheet.
 *
 * Takes a [Resources] rather than being `@Composable`, for the reason `EmvLabels.kt` documents: two
 * implementations of the same mapping drift the first time a label is reworded, and the one nobody
 * looks at — the shared text — is the one that goes stale.
 *
 * **Every caveat travels with the numbers.** A figure pasted into a maintenance ticket or an email
 * outlives this screen and all of its context, and the reader will have no idea it came from an
 * uncalibrated phone microphone unless the text says so. So the weighting, the offset in use, the
 * capture quality, the time out of range and both disclaimers are part of the summary rather than
 * decoration around it — a bare "LAeq 71.2 dB" is a more confident claim than this app can make.
 */
internal fun SoundMeterState.toSummaryText(resources: Resources): String = buildString {
    appendLine(resources.getString(R.string.soundmeter_summary_title))
    appendLine()

    appendLine(
        resources.getString(
            R.string.soundmeter_summary_duration,
            stats.durationSeconds.toInt() / SECONDS_PER_MINUTE,
            stats.durationSeconds.toInt() % SECONDS_PER_MINUTE,
        ),
    )

    appendStat(resources, R.string.soundmeter_summary_leq, stats.leqDbSpl)
    appendStat(resources, R.string.soundmeter_summary_min, stats.minDbSpl)
    appendStat(resources, R.string.soundmeter_summary_max, stats.maxDbSpl)

    if (stats.secondsAboveThreshold > 0.0) {
        appendLine(
            resources.getString(
                R.string.soundmeter_summary_above_threshold,
                SoundReference.EXPOSURE_THRESHOLD_DB.toInt(),
                stats.secondsAboveThreshold.toInt(),
            ),
        )
    }

    if (stats.unmeasurableSeconds > 0.0) {
        appendLine(
            resources.getString(
                R.string.soundmeter_summary_unmeasurable,
                stats.unmeasurableSeconds.toInt(),
            ),
        )
    }

    appendLine()
    appendLine(
        resources.getString(
            R.string.soundmeter_summary_settings,
            resources.getString(weighting.summaryRes()),
            resources.getString(timeWeighting.summaryRes()),
            offsetDb,
        ),
    )
    appendLine(resources.getString(quality.summaryRes()))

    appendLine()
    appendLine(resources.getString(R.string.soundmeter_disclaimer_approximate))
    append(resources.getString(R.string.soundmeter_disclaimer_not_certified))
}

private fun StringBuilder.appendStat(resources: Resources, labelRes: Int, value: Double?) {
    val text = value
        ?.let { resources.getString(R.string.soundmeter_level_value, it) }
        ?: resources.getString(R.string.soundmeter_stat_empty)

    appendLine(resources.getString(labelRes, text))
}

private fun Weighting.summaryRes(): Int = when (this) {
    Weighting.A -> R.string.soundmeter_weighting_a
    Weighting.C -> R.string.soundmeter_weighting_c
    Weighting.Z -> R.string.soundmeter_weighting_z
}

private fun TimeWeighting.summaryRes(): Int = when (this) {
    TimeWeighting.Fast -> R.string.soundmeter_time_fast
    TimeWeighting.Slow -> R.string.soundmeter_time_slow
}

private fun CaptureQuality.summaryRes(): Int = when (this) {
    CaptureQuality.Unprocessed -> R.string.soundmeter_summary_input_unprocessed
    CaptureQuality.VoiceRecognition -> R.string.soundmeter_summary_input_voice_recognition
    CaptureQuality.Processed -> R.string.soundmeter_summary_input_processed
    CaptureQuality.Unavailable -> R.string.soundmeter_summary_input_unavailable
}

private const val SECONDS_PER_MINUTE = 60
