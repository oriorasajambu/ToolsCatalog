package com.minion.scaffold.feature.exifstrip.presentation

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.exif.model.PhotoMetadata
import com.minion.scaffold.core.exif.model.SegmentSummary
import com.minion.scaffold.core.exif.model.TrailingData

/**
 * What the EXIF stripper screen renders.
 *
 * @property content           The current picked-photo content.
 * @property keepColourProfile Whether a JPEG colour profile is retained. Not a privacy setting;
 *   see the repository.
 * @property exporting         Whether an export is in progress.
 * @property export            The finished export result to show, or `null`.
 */
@Immutable
internal data class ExifStripState(
    val content: Content = Content.Empty,
    val keepColourProfile: Boolean = true,
    val exporting: Boolean = false,
    val export: ExportState? = null,
) : UiState {

    /** The picked-photo content the screen shows. */
    @Immutable
    sealed interface Content {

        /** Nothing picked yet. */
        data object Empty : Content

        /** A photo is being inspected. */
        data object Loading : Content

        /**
         * A photo was inspected and is ready to strip.
         *
         * @property uri         The photo's content URI.
         * @property displayName The picker's display name for the photo.
         * @property metadata    What was found in the photo.
         */
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

            /**
             * Blocks the container carries that `ExifInterface` does not report.
             *
             * PNG text chunks, JPEG comments, XMP packets, vendor blocks. Without these the screen
             * once told someone "no metadata found" about a screenshot carrying a comment that read
             * "taken at home" and a pair of coordinates — the reader sees Exif and nothing else, so
             * the container's own account has to be shown alongside it.
             */
            val containerBlocks: List<SegmentSummary> = emptyList(),

            val trailing: TrailingData? = null,
        ) : Content {

            /** Whether anything at all was found, by either route. */
            val carriesAnything: Boolean
                get() = metadata.hasAnything || containerBlocks.isNotEmpty() || trailing != null
        }

        /**
         * The photo could not be inspected or stripped.
         *
         * @property reason Why it failed.
         */
        data class Failed(val reason: FailureReason) : Content
    }

    /**
     * A finished export, shown after a successful strip.
     *
     * @property uri               A shareable URI for the cleaned file.
     * @property fileName          The cleaned file's display name.
     * @property byteCount         The cleaned file's size in bytes.
     * @property originalByteCount The source file's size in bytes.
     * @property removed           The metadata blocks that were removed.
     * @property retained          The metadata blocks kept on purpose.
     * @property trailing          Trailing data that was dropped, or `null`.
     * @property recompressed      True only on the HEIC conversion path. Surfaced wherever the
     *   result is.
     */
    @Immutable
    data class ExportState(
        val uri: Uri,
        val fileName: String,
        val byteCount: Int,
        val originalByteCount: Int,
        val removed: List<SegmentSummary>,
        val retained: List<SegmentSummary>,
        val trailing: TrailingData?,
        val recompressed: Boolean,
    ) {
        /** How many bytes smaller the cleaned file is, never negative. */
        val bytesSaved: Int get() = (originalByteCount - byteCount).coerceAtLeast(0)
    }

    /** Why inspecting or stripping a photo failed. */
    enum class FailureReason {

        /** The file could not be opened or read. */
        Unreadable,

        /** The file is larger than the tool will hold in memory. */
        TooLarge,

        /** The file is not an image the tool can strip. */
        NotAnImage,

        /** The strip ran but the output still contained something, so it was not offered. */
        VerificationFailed,

        /** Writing the cleaned copy failed. */
        WriteFailed,
    }

    /** Whether the current photo can be stripped losslessly. */
    val canExport: Boolean
        get() = content is Content.Loaded && content.convertibleFormat == null && !exporting

    /** Whether the current photo needs the HEIC re-encode path instead. */
    val canConvert: Boolean
        get() = content is Content.Loaded && content.convertibleFormat != null && !exporting

}

/** Everything the user can do on the stripper screen. */
internal sealed interface ExifStripIntent : UiIntent {

    /**
     * A photo was picked.
     *
     * @property uri The picked photo.
     */
    data class PhotoPicked(val uri: Uri) : ExifStripIntent

    /** Clear the current photo. */
    data object Cleared : ExifStripIntent

    /** Strip the current photo losslessly. */
    data object ExportRequested : ExifStripIntent

    /** Re-encode the current photo (the HEIC path). */
    data object ConvertRequested : ExifStripIntent

    /** Share the cleaned file. */
    data object ShareRequested : ExifStripIntent

    /**
     * Open the photo's coordinates in a map app.
     *
     * @property latitude  The latitude, handed to whatever map app the user chooses.
     * @property longitude The longitude.
     */
    data class OpenInMapsRequested(val latitude: String, val longitude: String) : ExifStripIntent

    /**
     * Copy a metadata entry.
     *
     * @property label The entry's label.
     * @property value The entry's value.
     */
    data class CopyRequested(val label: String, val value: String) : ExifStripIntent

    /**
     * The keep-colour-profile toggle changed.
     *
     * @property keep Whether to retain a JPEG colour profile.
     */
    data class KeepColourProfileChanged(val keep: Boolean) : ExifStripIntent
}

/** One-shot events from the stripper screen. */
internal sealed interface ExifStripEffect : UiEffect {

    /**
     * Hand the cleaned file to a share target.
     *
     * @property uri The shareable URI.
     */
    data class Share(val uri: Uri) : ExifStripEffect

    /**
     * A `geo:` intent, which is the only thing in this feature that leaves the device.
     *
     * Deliberate and visible: the app never geocodes on the user's behalf, because turning
     * coordinates into a place name means sending the position from the photo they are trying to
     * sanitise to a third party. Handing it to a map app on an explicit tap makes the leak a choice
     * with a destination the user can see.
     *
     * @property latitude  The latitude to open.
     * @property longitude The longitude to open.
     */
    data class OpenInMaps(val latitude: String, val longitude: String) : ExifStripEffect

    /**
     * Put a metadata entry on the clipboard.
     *
     * @property label The entry's label.
     * @property value The entry's value.
     */
    data class Copy(val label: String, val value: String) : ExifStripEffect

    /**
     * Show a transient message.
     *
     * @property notice What to tell the user.
     */
    data class Notice(val notice: ExifStripNotice) : ExifStripEffect
}

/** The transient messages the stripper shows as a snackbar. */
internal enum class ExifStripNotice {

    /** A metadata entry was copied. */
    Copied,

    /** No app could handle the `geo:` intent. */
    NoMapApp,

    /** The photo had no metadata to remove. */
    NothingToRemove,
}
