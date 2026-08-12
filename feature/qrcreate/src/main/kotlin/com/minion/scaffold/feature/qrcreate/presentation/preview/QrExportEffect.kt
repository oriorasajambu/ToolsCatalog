package com.minion.scaffold.feature.qrcreate.presentation.preview

import android.net.Uri
import com.minion.scaffold.core.common.mvi.UiEffect

/**
 * What a generated code can do once it exists.
 *
 * Shared by every authoring screen in this module. Copying a payload, sharing a PNG and saving to
 * the gallery are the same three actions whatever the payload means, so one type and one handler
 * rather than a set per format.
 */
internal sealed interface QrExportEffect : UiEffect {

    /**
     * Put the payload on the clipboard.
     *
     * @property text The payload text to copy.
     */
    data class CopyText(val text: String) : QrExportEffect

    /**
     * Hand a generated PNG to the share sheet.
     *
     * @property uri A file the receiving app may read, produced by the exporter.
     */
    data class ShareImage(val uri: Uri) : QrExportEffect

    /**
     * Something happened that leaves no trace on screen.
     *
     * Saving to the gallery is invisible without it: the image lands in another app entirely, and
     * without confirmation the button reads as broken.
     *
     * @property outcome Whether the save succeeded.
     */
    data class ShowExportMessage(val outcome: ExportOutcome) : QrExportEffect
}

/** The result of an export action, for the confirmation message. */
internal enum class ExportOutcome {

    /** The QR was saved to the photo library. */
    SAVED_TO_GALLERY,

    /** The export could not be written. */
    EXPORT_FAILED,
}
