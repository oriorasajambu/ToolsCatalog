package com.minion.scaffold.feature.exifstrip.presentation

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.exif.model.PhotoMetadata
import com.minion.scaffold.core.exif.model.SegmentSummary
import com.minion.scaffold.core.exif.model.TrailingData

@Immutable
internal data class ExifStripState(
    val content: Content = Content.Empty,

    /** Whether a JPEG colour profile is retained. Not a privacy setting; see the repository. */
    val keepColourProfile: Boolean = true,

    val exporting: Boolean = false,

    val export: ExportState? = null,
) : UiState {

    @Immutable
    sealed interface Content {

        /** Nothing picked yet. */
        data object Empty : Content

        data object Loading : Content

        data class Loaded(
            val uri: Uri,
            val displayName: String?,
            val metadata: PhotoMetadata,

            /**
             * Set when this container cannot be stripped losslessly.
             *
             * Carries the format so the conversion offer can name it. HEIC is what modern phones
             * shoot, so someone meeting this deserves better than "unsupported file".
             */
            val convertibleFormat: String? = null,
        ) : Content {

            /**
             * Whether the filename itself leaks something.
             *
             * `IMG_20240115_143022.jpg` is a date in plain text. Worth saying, because the export
             * renames and the reason is not otherwise obvious.
             */
            val fileNameCarriesDate: Boolean
                get() = displayName?.let { DATE_IN_NAME.containsMatchIn(it) } == true
        }

        data class Failed(val reason: FailureReason) : Content
    }

    @Immutable
    data class ExportState(
        val uri: Uri,
        val fileName: String,
        val byteCount: Int,
        val originalByteCount: Int,
        val removed: List<SegmentSummary>,
        val retained: List<SegmentSummary>,
        val trailing: TrailingData?,

        /** True only on the HEIC conversion path. Surfaced wherever the result is. */
        val recompressed: Boolean,
    ) {
        val bytesSaved: Int get() = (originalByteCount - byteCount).coerceAtLeast(0)
    }

    enum class FailureReason {
        Unreadable,
        TooLarge,
        NotAnImage,

        /** The strip ran but the output still contained something, so it was not offered. */
        VerificationFailed,

        WriteFailed,
    }

    val canExport: Boolean
        get() = content is Content.Loaded && content.convertibleFormat == null && !exporting

    val canConvert: Boolean
        get() = content is Content.Loaded && content.convertibleFormat != null && !exporting

    companion object {
        /**
         * Eight consecutive digits, which is what a camera date looks like in a filename.
         *
         * Deliberately loose. A false positive costs one extra line of explanation; a false negative
         * means quietly shipping the date the tool just removed from the metadata.
         */
        private val DATE_IN_NAME = Regex("\\d{8}")
    }
}

internal sealed interface ExifStripIntent : UiIntent {

    data class PhotoPicked(val uri: Uri) : ExifStripIntent

    data object Cleared : ExifStripIntent

    data object ExportRequested : ExifStripIntent

    data object ConvertRequested : ExifStripIntent

    data object ShareRequested : ExifStripIntent

    /** The coordinates, handed to whatever map app the user chooses. */
    data class OpenInMapsRequested(val latitude: String, val longitude: String) : ExifStripIntent

    data class CopyRequested(val label: String, val value: String) : ExifStripIntent

    data class KeepColourProfileChanged(val keep: Boolean) : ExifStripIntent
}

internal sealed interface ExifStripEffect : UiEffect {

    data class Share(val uri: Uri) : ExifStripEffect

    /**
     * A `geo:` intent, which is the only thing in this feature that leaves the device.
     *
     * Deliberate and visible: the app never geocodes on the user's behalf, because turning
     * coordinates into a place name means sending the position from the photo they are trying to
     * sanitise to a third party. Handing it to a map app on an explicit tap makes the leak a choice
     * with a destination the user can see.
     */
    data class OpenInMaps(val latitude: String, val longitude: String) : ExifStripEffect

    data class Copy(val label: String, val value: String) : ExifStripEffect

    data class Notice(val notice: ExifStripNotice) : ExifStripEffect
}

internal enum class ExifStripNotice {
    Copied,
    NoMapApp,
    NothingToRemove,
}
