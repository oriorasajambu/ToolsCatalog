package com.minion.scaffold.feature.qrscan.presentation

import android.net.Uri
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.vcard.model.ContactCard
import com.minion.scaffold.feature.qrscan.domain.ScannedContent
import com.minion.scaffold.feature.qrscan.domain.compare.PayloadCharDiff
import com.minion.scaffold.feature.qrscan.domain.compare.QrComparison
import com.minion.scaffold.feature.qrscan.domain.export.PaymentSchemaSource

/**
 * What the scan screen renders.
 *
 * The mutually exclusive phases live in [ContentState] rather than as sibling booleans, so
 * "showing a report *and* an error" is unrepresentable instead of merely unintended.
 */
internal data class QrScanState(
    /** Whether the payload comes from the camera or manual entry. */
    val mode: InputMode = InputMode.Camera,
    /** The camera permission state. */
    val cameraPermission: PermissionState = PermissionState.Unknown,
    /** Whether the torch is on. */
    val torchEnabled: Boolean = false,
    /** The mutually exclusive decode phase. */
    val content: ContentState = ContentState.Idle,
    /** The text in the manual-entry field. */
    val manualPayload: String = "",
    /**
     * The pinned first code, for as long as a comparison is in progress.
     *
     * A sibling field rather than a payload carried by each [ContentState], because it outlives
     * three of them: the camera is re-armed ([ContentState.Idle]), a picked image may be searched
     * ([ContentState.Decoding]), and then the diff is on screen ([ContentState.Comparison]).
     * Threading it through all three would change the shape of two states that the non-comparing
     * paths construct and match on, for no gain here.
     */
    val baseline: ScannedContent? = null,
    /**
     * Which JSON schema an export would use.
     *
     * On the state rather than read by the sheet, because the share sheet has to *name* the active
     * contract before sending anything — and what it is allowed to promise about the contents
     * depends on whether the app wrote the template or the user did.
     */
    val schemaSource: PaymentSchemaSource = PaymentSchemaSource.BuiltIn,
    /** The imported schema's file name. Empty under the built-in. */
    val schemaLabel: String = "",
) : UiState {

    /** True once there is something worth copying or sharing. */
    val hasReport: Boolean get() = content is ContentState.Success

    /** True from pressing Compare until the comparison is cancelled. */
    val isComparing: Boolean get() = baseline != null

    /**
     * Whether the report on screen can be exported as a payment response.
     *
     * Payment codes only. The response contract describes a transaction, and there is no honest
     * JSON of that shape for a contact card — so the other three formats keep a share that goes
     * straight to the system sheet with nothing to choose between.
     */
    val canExportJson: Boolean
        get() = (content as? ContentState.Success)?.content is ScannedContent.Payment

    /**
     * Whether the camera should be looking for a code.
     *
     * False once a report is on screen: the analyzer is unbound rather than left running against
     * a result the user is already reading.
     */
    val isScanning: Boolean
        get() = mode == InputMode.Camera &&
            cameraPermission == PermissionState.Granted &&
            content is ContentState.Idle

    sealed interface ContentState {

        /** Nothing decoded yet. */
        data object Idle : ContentState

        /**
         * A picked image is being searched for a QR code.
         *
         * Only the gallery path reaches this. Parsing a payload already in hand is a single pass
         * over a few hundred characters and completes within the same frame; opening a photo and
         * running detection over it does not.
         */
        data object Decoding : ContentState

        /**
         * A code was decoded.
         *
         * @property content What the code turned out to be.
         */
        data class Success(val content: ScannedContent) : ContentState

        /**
         * Nothing could be made of the input.
         *
         * [payload] is the trimmed string the parse offsets index, and it is empty for the failures
         * that have no payload to speak of — no barcode in the image, an unreadable file. Carrying
         * it here rather than relying on [QrScanState.manualPayload] is what lets a *camera* or
         * gallery failure show the damaged payload at all; that field is only ever written by
         * typing.
         *
         * @property error   Why the input could not be read.
         * @property payload The trimmed payload the error's offsets index, or empty when there is none.
         */
        data class Failure(
            val error: QrScanError,
            val payload: String = "",
        ) : ContentState

        /**
         * Two codes, read against each other.
         *
         * Reachable only with a non-null [QrScanState.baseline]; the comparison carries both sides
         * itself, and the field on the state is what the "scan another" and "swap" actions pin
         * their next round to.
         *
         * @property comparison The aligned fields.
         * @property rawDiff    The character alignment, which is only computed if it is asked for.
         */
        data class Comparison(
            val comparison: QrComparison,
            val rawDiff: RawDiffState = RawDiffState.NotComputed,
        ) : ContentState
    }
}

/**
 * How far along the character-by-character alignment is.
 *
 * Separate from the comparison itself because it is the one part of this feature that is not
 * affordable on the main thread for a worst-case pair, and because most people never open the tab
 * that needs it. Starting as [NotComputed] rather than computing eagerly is what keeps a
 * comparison instant.
 */
internal sealed interface RawDiffState {

    /** Nobody has asked for it. */
    data object NotComputed : RawDiffState

    /** Somebody has, and the alignment is running. */
    data object Computing : RawDiffState

    /**
     * The alignment finished.
     *
     * @property diff Where the two payloads disagree.
     */
    data class Ready(val diff: PayloadCharDiff) : RawDiffState
}

/** Where a payload comes from. Both funnel into [QrScanIntent.PayloadSubmitted]. */
internal enum class InputMode {

    /** The camera viewfinder. */
    Camera,

    /** The manual paste/type field. */
    Manual,
}

/** Everything the user (or the system) can do on the scan screen. */
internal sealed interface QrScanIntent : UiIntent {

    /** Keystrokes in the manual payload field. */
    data class ManualPayloadChanged(val payload: String) : QrScanIntent

    /**
     * A payload to decode, from wherever it came from.
     *
     * One intent for every source. The camera analyzer and the gallery decoder produce the same
     * string the paste field does, so there is one decode path to reason about and to test.
     */
    data class PayloadSubmitted(val payload: String) : QrScanIntent

    /** An image chosen from the photo picker, to be searched for a QR code. */
    data class ImagePicked(val uri: Uri) : QrScanIntent

    /** Discard the current result **and** the payload, going back to an empty screen. */
    data object Cleared : QrScanIntent

    /**
     * Put the current result away but keep the payload.
     *
     * The back gesture and the top-bar arrow both land here rather than on [Cleared]. Once the
     * failure screen is somewhere you sit and repair a few hundred characters, a stray back-swipe
     * wiping it is data loss with no undo — and discarding is a thing worth having to ask for.
     */
    data object Dismissed : QrScanIntent

    /**
     * The outcome of a camera permission request.
     *
     * [shouldShowRationale] is the system's answer to "will the dialog appear again?", which is
     * the only way to tell a refusal that can be retried from one that cannot. It is read at the
     * call site because it needs the `Activity`, which has no business being in a ViewModel.
     */
    data class PermissionResult(
        val granted: Boolean,
        val shouldShowRationale: Boolean,
    ) : QrScanIntent

    /**
     * The input mode changed between camera and manual entry.
     *
     * @property mode The newly selected mode.
     */
    data class ModeChanged(val mode: InputMode) : QrScanIntent

    /** Toggle the torch. */
    data object TorchToggled : QrScanIntent

    /** Open the app's system settings, to grant a permanently denied permission. */
    data object AppSettingsRequested : QrScanIntent

    /** Take the decoded payload to the editor. */
    data object EditRequested : QrScanIntent

    /** Open the scanned link. Only meaningful for [ScannedContent.Web]. */
    data object OpenLinkRequested : QrScanIntent

    /** Hand the scanned card to the contacts app. Only meaningful for [ScannedContent.Contact]. */
    data object AddContactRequested : QrScanIntent

    /** Copy the whole decoded report. */
    data object CopyReportRequested : QrScanIntent

    /**
     * Copy one already-resolved piece of the report — a tag's value, a subtag, the checksum pair.
     *
     * Carries finished text rather than a tag reference because the caller has already rendered
     * it. Routing it through the ViewModel anyway keeps every clipboard write going through the
     * one effect handler instead of scattering `Clipboard` access across the report's composables.
     */
    data class CopyValueRequested(val text: String) : QrScanIntent

    /** Share the whole decoded report. */
    data object ShareReportRequested : QrScanIntent

    /** Share the scanned payment code as the response a payment backend would have returned. */
    data object ShareJsonRequested : QrScanIntent

    /**
     * Pin the code on screen and go looking for one to compare it against.
     *
     * The camera is re-armed rather than a second screen being pushed: a result is already a step
     * within this screen rather than a destination, and giving the comparison its own route would
     * mean the scanner existed twice on the back stack with only one of them holding the baseline.
     */
    data object CompareRequested : QrScanIntent

    /** Abandon the comparison and go back to the pinned code's own report. */
    data object CompareCancelled : QrScanIntent

    /** Keep the baseline, drop the code it was compared against, and look for another. */
    data object CompareRescanRequested : QrScanIntent

    /**
     * Read the two codes the other way round.
     *
     * Worth having because "only in A" and "only in B" are the wrong way round the moment the two
     * were scanned in the order they came to hand rather than the order they mean.
     */
    data object CompareSwapped : QrScanIntent

    /**
     * The character-by-character view was opened.
     *
     * Idempotent: the tab can be selected any number of times, and the alignment runs once.
     */
    data object RawDiffRequested : QrScanIntent

    /** Copy the whole comparison. */
    data object CopyComparisonRequested : QrScanIntent

    /** Share the whole comparison. */
    data object ShareComparisonRequested : QrScanIntent
}

/**
 * One-shot events.
 *
 * Copy and share carry the [ScannedContent] rather than a finished string. Rendering a report as
 * text needs string resources, and resolving those in the ViewModel would mean an `Application`
 * context in the ViewModel and text that does not follow a locale change. The screen formats it,
 * for the same reason it is the screen that turns an error into words.
 */
internal sealed interface QrScanEffect : UiEffect {

    data class CopyReport(val content: ScannedContent) : QrScanEffect

    /** Copy text the screen already rendered. */
    data class CopyText(val text: String) : QrScanEffect

    data class ShareReport(val content: ScannedContent) : QrScanEffect

    /**
     * Share a finished JSON document.
     *
     * Carries the **text**, where [ShareReport] and [CopyReport] carry the domain object. Not an
     * inconsistency: those defer to the screen because rendering a report needs string resources
     * and has to follow a locale change. A response document has no locale — its field names and
     * its enumerated values are protocol, and a device set to Bahasa must not rename them — so
     * there is nothing for the screen to resolve and the ViewModel builds it outright.
     */
    data class ShareJson(val json: String) : QrScanEffect

    /**
     * A JSON export could not be built from the active schema template.
     *
     * An effect rather than a state: the report is still on screen and still correct, and the only
     * thing that happened is that a document could not be made from it.
     */
    data class SchemaRefused(val reason: SchemaRefusal) : QrScanEffect

    /**
     * Hand this payload onward to be edited.
     *
     * Emitted both when the scanner was opened for editing — where it fires the moment a code
     * decodes — and when the report's edit action is pressed. Where it goes is the host's
     * business; this module does not know an editor exists.
     */
    data class EditPayload(val route: AppRoute) : QrScanEffect

    /**
     * Open a scanned link.
     *
     * Emitted only from an explicit press, never on decoding: a scanned code is untrusted input and
     * one-tap auto-opening is the standard QR phishing vector.
     */
    data class OpenLink(val url: String) : QrScanEffect

    /** Offer a scanned card to the contacts app, pre-filled. */
    data class AddContact(val card: ContactCard) : QrScanEffect

    /** Send the user to the app's system settings, the only place a hard denial can be undone. */
    data object OpenAppSettings : QrScanEffect

    data class CopyComparison(val comparison: QrComparison) : QrScanEffect

    data class ShareComparison(val comparison: QrComparison) : QrScanEffect

    /**
     * A second code was turned away.
     *
     * An effect rather than a state, because the camera is still running and the baseline is still
     * pinned — nothing about the screen changed, and a message that persisted through the next
     * three scans would be describing a moment that had passed.
     */
    data class CompareRejected(val reason: CompareRejection) : QrScanEffect
}
