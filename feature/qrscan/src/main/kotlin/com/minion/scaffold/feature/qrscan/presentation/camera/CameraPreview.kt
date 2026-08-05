package com.minion.scaffold.feature.qrscan.presentation.camera

import androidx.camera.view.TransformExperimental
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.camera.CameraViewfinder
import com.minion.scaffold.feature.qrscan.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * The scanning viewfinder: an aiming reticle over the shared camera preview.
 *
 * The camera itself — controller, torch, zoom, tap-to-focus, coordinate transform — lives in
 * `:core:camera`, shared with the OCR tool. What is left here is everything specific to reading a
 * QR code: the reticle, whether a code is aimed, and the brief lock before the payload is handed
 * on.
 *
 * Aiming lives here rather than in the ViewModel. It is geometry in view pixels that means nothing
 * once the camera is gone, and the only part worth testing — [isAimed] — is a pure function that
 * needs none of this.
 *
 * @param scanningEnabled whether to look for codes. False detaches the analyzer, which is what
 *   stops the camera re-reading a code whose report is already on screen.
 */
@androidx.annotation.OptIn(TransformExperimental::class)
@Composable
internal fun CameraPreview(
    scanningEnabled: Boolean,
    torchEnabled: Boolean,
    onToggleTorch: () -> Unit,
    onPayloadDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    var detection by remember { mutableStateOf<DetectedCode?>(null) }
    var reticle by remember { mutableStateOf(Rect.Zero) }

    /** Set the moment a code is accepted, so detection jitter cannot re-fire the lock. */
    var locked by remember { mutableStateOf(false) }

    // rememberUpdatedState, not a direct capture: the analyzer is remembered across recompositions,
    // so capturing the lambda would pin the very first one.
    val currentOnPayloadDetected by rememberUpdatedState(onPayloadDetected)

    // State writes from the analysis executor are safe — snapshot state is thread-safe to write.
    val analyzer = remember { BarcodeAnalyzer { detection = it } }

    // The viewfinder attaches and detaches the analyzer but does not own it, because it cannot know
    // whether one holds a native detector. This one does, so closing it is this file's job.
    DisposableEffect(analyzer) {
        onDispose { analyzer.close() }
    }

    /**
     * Watches for an aimed code, then holds green briefly and hands the payload on.
     *
     * Keyed on [scanningEnabled] alone — deliberately **not** on the detection or the aim state.
     * Those change at the camera's frame rate, and keying on them cancelled this coroutine
     * mid-`delay` whenever the code drifted or left frame during the hold: the payload was never
     * delivered, `locked` stayed set, and the reticle sat green forever. That was the bug.
     *
     * `first()` latches the payload at the moment of aiming, so a code that moves away afterwards
     * is still the one reported. Having already buzzed to say it was captured, dropping it would be
     * the wrong answer.
     */
    LaunchedEffect(scanningEnabled) {
        if (!scanningEnabled) {
            detection = null
            return@LaunchedEffect
        }

        detection = null
        locked = false

        // Reads both detection and the reticle, so it re-evaluates when either changes — including
        // a rotation that moves the box out from under a code.
        val payload = snapshotFlow {
            detection?.takeIf { isAimed(code = it.bounds, reticle = reticle) }?.payload
        }
            .filterNotNull()
            .first()

        locked = true
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        delay(LOCK_HOLD_MILLIS)
        currentOnPayloadDetected(payload)
    }

    val seen = detection
    val aim = when {
        // Stays green through the hold even if the code drifts, so the reticle agrees with the
        // haptic that already fired.
        locked -> AimState.Locked
        seen == null -> AimState.Searching
        isAimed(code = seen.bounds, reticle = reticle) -> AimState.Locked
        else -> AimState.OffTarget
    }

    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    CameraViewfinder(
        analyzer = analyzer.takeIf { scanningEnabled },
        torchEnabled = torchEnabled,
        onToggleTorch = onToggleTorch,
        onTransformChanged = analyzer::onPreviewTransformChanged,
        // The reticle is measured off the viewfinder's own bounds, so the size lands here rather
        // than inside the overlay — the aim check below needs it outside composition.
        modifier = modifier.onSizeChanged { reticle = reticleIn(it) },
    ) {
        ScanReticle(
            reticle = reticle,
            aim = aim,
            modifier = Modifier.matchParentSize(),
        )

        aim.hintRes()?.let { hint ->
            Text(
                text = stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(spacing),
            )
        }
    }
}

private fun AimState.hintRes(): Int? = when (this) {
    AimState.Searching -> R.string.qrscan_aim_searching
    AimState.OffTarget -> R.string.qrscan_aim_off_target
    // Nothing to say once it is locked — the green box and the haptic have said it.
    AimState.Locked -> null
}

/** How long the reticle stays green before the report replaces the viewfinder. */
private const val LOCK_HOLD_MILLIS = 250L
