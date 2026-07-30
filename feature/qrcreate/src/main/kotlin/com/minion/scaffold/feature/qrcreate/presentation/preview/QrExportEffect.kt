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

    data class CopyText(val text: String) : QrExportEffect

    /** A file the receiving app may read, produced by the exporter. */
    data class ShareImage(val uri: Uri) : QrExportEffect

    /**
     * Something happened that leaves no trace on screen.
     *
     * Saving to the gallery is invisible without it: the image lands in another app entirely, and
     * without confirmation the button reads as broken.
     */
    data class ShowExportMessage(val outcome: ExportOutcome) : QrExportEffect
}

internal enum class ExportOutcome { SAVED_TO_GALLERY, EXPORT_FAILED }
