package com.minion.scaffold.feature.ocr.presentation

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.ocr.model.OcrEngine
import com.minion.scaffold.core.ocr.model.RecognizedText
import com.minion.scaffold.core.ui.permission.PermissionState

/**
 * The OCR tool. One screen with three phases — aim, choose, edit — rather than three routes,
 * because they are one continuous act and backing out of any of them means "go back to aiming".
 */
internal data class OcrState(
    /** The camera permission state. */
    val permission: PermissionState = PermissionState.Unknown,
    /** Which phase of the aim → choose → edit flow is active. */
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

    /**
     * The engine the user has selected, mirrored from preferences.
     *
     * Held in state so a recognition can be compared against it — that mismatch is what raises
     * [OcrNotice.EngineUnavailable] when the selected engine could not run.
     */
    val engine: OcrEngine = OcrEngine.DEFAULT,
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

    /** Whether at least one capture has been taken. */
    val hasCaptures: Boolean get() = captures.isNotEmpty()
}

/**
 * One captured page: the image, what was recognised in it, and which blocks the user kept.
 *
 * [bitmap] is held in memory and never written to disk — people OCR passports, receipts and bank
 * letters, and the app's photo picker was chosen precisely to minimise storage access. The cost is
 * that a process death loses the image; the *text* survives, because that is where the user's work
 * actually is.
 *
 * @property id               A stable identifier for the capture.
 * @property bitmap           The captured image, held in memory only.
 * @property text             What was recognised in it.
 * @property selectedBlockIds The ids of the blocks the user kept.
 */
internal data class CaptureUi(
    val id: String,
    val bitmap: Bitmap,
    val text: RecognizedText,
    val selectedBlockIds: Set<String>,
)

/** A short, self-clearing message shown over the current stage. */
internal enum class OcrNotice {

    /** The image was read and holds no text. */
    NoTextFound,

    /** The picked image could not be opened. */
    ImageUnreadable,

    /** The camera capture failed. */
    CaptureFailed,

    /** The handover to Text tools was shortened to fit the navigation argument. */
    TextTruncated,

    /** The selected engine could not run here, so the other one read the image instead. */
    EngineUnavailable,
}

/** Everything the user (or the system) can do on the OCR screen. */
internal sealed interface OcrIntent : UiIntent {

    /**
     * The camera permission request returned.
     *
     * See `PermissionState.resolve` for why `shouldShowRationale` is read at the call site.
     *
     * @property granted             Whether the permission is granted.
     * @property shouldShowRationale The system's rationale flag.
     */
    data class PermissionResult(val granted: Boolean, val shouldShowRationale: Boolean) : OcrIntent

    /** Open the app's system settings, to grant a permanently denied permission. */
    data object AppSettingsRequested : OcrIntent

    /**
     * An image was picked from the gallery.
     *
     * @property uri The picked image.
     */
    data class ImagePicked(val uri: Uri) : OcrIntent

    /**
     * The live viewfinder's detected text boxes changed.
     *
     * @property boxes The text boxes in view pixels.
     */
    data class HintBoxesChanged(val boxes: List<Rect>) : OcrIntent

    /**
     * A recognised block was selected or deselected.
     *
     * @property blockId The id of the block toggled.
     */
    data class BlockToggled(val blockId: String) : OcrIntent

    /** Select or deselect every block. */
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

    /**
     * A capture was removed from the set.
     *
     * @property captureId The id of the capture to remove.
     */
    data class CaptureRemoved(val captureId: String) : OcrIntent

    /**
     * The result text was edited.
     *
     * @property text The new text.
     */
    data class ResultEdited(val text: String) : OcrIntent

    /** Copy the result text. */
    data object CopyRequested : OcrIntent

    /** Share the result text. */
    data object ShareRequested : OcrIntent

    /** Hand the result text to the text tools. */
    data object SendToTextToolsRequested : OcrIntent

    /** Discard everything and start over. */
    data object Restarted : OcrIntent

    /** Dismiss the current inline notice. */
    data object NoticeDismissed : OcrIntent
}

/** One-shot events from the OCR screen. */
internal sealed interface OcrEffect : UiEffect {

    /** Open the app's system settings. */
    data object OpenAppSettings : OcrEffect

    /**
     * Put the result text on the clipboard.
     *
     * @property text The text to copy.
     */
    data class CopyText(val text: String) : OcrEffect

    /**
     * Share the result text.
     *
     * @property text The text to share.
     */
    data class ShareText(val text: String) : OcrEffect

    /**
     * Hand the extracted text to the text tools.
     *
     * Carries the text rather than a route so `:app` decides what "open text tools" means — this
     * module knows a route contract exists, not which screen serves it.
     */
    data class SendToTextTools(val text: String) : OcrEffect
}
