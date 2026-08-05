package com.minion.scaffold.feature.ocr.presentation

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.ocr.model.RecognizedText
import com.minion.scaffold.core.ui.permission.PermissionState

/**
 * The OCR tool. One screen with three phases — aim, choose, edit — rather than three routes,
 * because they are one continuous act and backing out of any of them means "go back to aiming".
 */
internal data class OcrState(
    val permission: PermissionState = PermissionState.Unknown,
    val stage: Stage = Stage.Capture,

    /**
     * Where text is in the live viewfinder, in view pixels.
     *
     * Boxes only, deliberately — see `OcrAnalyzer` for why the recognised strings are never shown
     * before capture.
     */
    val hintBoxes: List<Rect> = emptyList(),

    /** Captures taken so far. More than one only when the user is appending pages. */
    val captures: List<CaptureUi> = emptyList(),

    /** Set while a capture or a picked image is being recognised. */
    val isRecognising: Boolean = false,

    /**
     * A transient inline message — "no text found", "that image could not be opened".
     *
     * Inline rather than a failure screen: the fix is nearly always to reframe and try again, and
     * a screen transition for a one-second correction gets in the way.
     */
    val notice: OcrNotice? = null,

    /** The edited text, once the user reaches [Stage.Result]. */
    val editedText: String = "",
) : UiState {

    enum class Stage {

        /** Live viewfinder, or the gallery entry point when the camera is unavailable. */
        Capture,

        /** A still is on screen with its blocks selectable. */
        Selection,

        /** The assembled text, editable. */
        Result,
    }

    /** The capture being selected from, or null outside [Stage.Selection]. */
    val currentCapture: CaptureUi? get() = captures.lastOrNull()

    val hasCaptures: Boolean get() = captures.isNotEmpty()
}

/**
 * One captured page: the image, what was recognised in it, and which blocks the user kept.
 *
 * [bitmap] is held in memory and never written to disk — people OCR passports, receipts and bank
 * letters, and the app's photo picker was chosen precisely to minimise storage access. The cost is
 * that a process death loses the image; the *text* survives, because that is where the user's work
 * actually is.
 */
internal data class CaptureUi(
    val id: String,
    val bitmap: Bitmap,
    val text: RecognizedText,
    val selectedBlockIds: Set<String>,
)

/** A short, self-clearing message shown over the current stage. */
internal enum class OcrNotice {
    NoTextFound,
    ImageUnreadable,
    CaptureFailed,

    /** The handover to Text tools was shortened to fit the navigation argument. */
    TextTruncated,
}

internal sealed interface OcrIntent : UiIntent {

    /** See `PermissionState.resolve` for why `shouldShowRationale` is read at the call site. */
    data class PermissionResult(val granted: Boolean, val shouldShowRationale: Boolean) : OcrIntent

    data object AppSettingsRequested : OcrIntent

    data class ImagePicked(val uri: Uri) : OcrIntent

    data class HintBoxesChanged(val boxes: List<Rect>) : OcrIntent

    data class BlockToggled(val blockId: String) : OcrIntent

    data object SelectAllToggled : OcrIntent

    /**
     * Re-runs recognition on the current capture, rotated a quarter turn.
     *
     * ML Kit's Latin model tolerates a few degrees of skew but not a 90° rotation, so a photo of a
     * book spine or a landscape document comes back empty. Rotating and retrying is cheap because
     * the bitmap is still held.
     */
    data object RotateAndRetry : OcrIntent

    /** Accept this capture's selection and go back to the viewfinder for another page. */
    data object AddAnotherPage : OcrIntent

    /** Accept the selection and move to the editable result. */
    data object SelectionConfirmed : OcrIntent

    data class CaptureRemoved(val captureId: String) : OcrIntent

    data class ResultEdited(val text: String) : OcrIntent

    data object CopyRequested : OcrIntent

    data object ShareRequested : OcrIntent

    data object SendToTextToolsRequested : OcrIntent

    /** Discard everything and start over. */
    data object Restarted : OcrIntent

    data object NoticeDismissed : OcrIntent
}

internal sealed interface OcrEffect : UiEffect {

    data object OpenAppSettings : OcrEffect

    data class CopyText(val text: String) : OcrEffect

    data class ShareText(val text: String) : OcrEffect

    /**
     * Hand the extracted text to the text tools.
     *
     * Carries the text rather than a route so `:app` decides what "open text tools" means — this
     * module knows a route contract exists, not which screen serves it.
     */
    data class SendToTextTools(val text: String) : OcrEffect
}
