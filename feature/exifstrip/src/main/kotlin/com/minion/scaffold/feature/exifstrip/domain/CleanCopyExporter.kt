package com.minion.scaffold.feature.exifstrip.domain

import android.net.Uri
import com.minion.scaffold.core.exif.model.SegmentSummary
import com.minion.scaffold.core.exif.model.StripFailure
import com.minion.scaffold.core.exif.model.TrailingData

/** Writes the cleaned copy and hands back something shareable. */
internal interface CleanCopyExporter {

    /**
     * What the container holds, and whether it can be stripped, without writing anything.
     *
     * Runs at inspection time for two reasons. An unstrippable container is reported while the user
     * is still reading what was found, rather than after they press a button that declines to do
     * anything. And — the more important one — **`ExifInterface` cannot see everything a file
     * carries.** It reads Exif; it does not report a PNG text chunk, a JPEG comment, an XMP packet
     * or a vendor block. Relying on it alone told a user "no metadata found in this photo" about a
     * screenshot whose comment chunk read "taken at home" followed by coordinates. Found on a
     * device, and the worst failure this tool has: a false reassurance is worse than no tool.
     *
     * @param photo   The inspected photo to dry-run against.
     * @param keepIcc Whether a JPEG colour profile would be retained.
     * @return What the strip would remove, and any reason it cannot run.
     */
    suspend fun probe(photo: InspectedPhoto, keepIcc: Boolean): ProbeResult

    /**
     * Writes the cleaned copy and returns something shareable.
     *
     * @param photo   The inspected photo to clean.
     * @param keepIcc Whether to retain a JPEG colour profile.
     * @return The export outcome, including the verification of the written file.
     */
    suspend fun export(photo: InspectedPhoto, keepIcc: Boolean): ExportResult

    /**
     * The HEIC path: decode and re-encode as a JPEG with nothing attached.
     *
     * Separate from [export] rather than a fallback inside it, because it breaks the guarantee that
     * every other output is pixel-identical to its input. An exception that is invisible is not an
     * exception, it is a lie with a special case.
     *
     * @param photo The inspected photo to decode and re-encode.
     * @return The export outcome for the re-encoded JPEG.
     */
    suspend fun convertToCleanJpeg(photo: InspectedPhoto): ExportResult
}

/**
 * What a dry run found.
 *
 * @property failure   Why the strip cannot run, or `null` when it can.
 * @property removable Blocks the strip would remove — the container's own account of what is in it.
 * @property trailing  Trailing data found after the image, or `null`.
 */
internal data class ProbeResult(
    val failure: StripFailure?,
    val removable: List<SegmentSummary>,
    val trailing: TrailingData?,
)

/** The outcome of writing a cleaned copy. */
internal sealed interface ExportResult {

    /**
     * A file ready to share, and the evidence that it is clean.
     *
     * [verified] is not decoration. The whole value of the operation is a claim about what the file
     * no longer contains, and a claim nobody checked is an assertion — so the export re-reads its own
     * output and reports what it found.
     *
     * @property uri               A shareable URI for the cleaned file.
     * @property fileName          The cleaned file's display name.
     * @property byteCount         The cleaned file's size in bytes.
     * @property originalByteCount The source file's size in bytes.
     * @property removed           The metadata blocks that were removed.
     * @property retained          The metadata blocks kept on purpose.
     * @property trailing          Trailing data that was dropped, or `null`.
     * @property recompressed      Whether the image had to be re-encoded (the HEIC path).
     */
    data class Success(
        val uri: Uri,
        val fileName: String,
        val byteCount: Int,
        val originalByteCount: Int,
        val removed: List<SegmentSummary>,
        val retained: List<SegmentSummary>,
        val trailing: TrailingData?,
        val recompressed: Boolean,
    ) : ExportResult

    /**
     * The file could not be planned. Carries the reason so the UI can offer a conversion.
     *
     * @property failure Why the file could not be stripped.
     */
    data class Rejected(val failure: StripFailure) : ExportResult

    /**
     * The export ran, and the result still contains something.
     *
     * Blocking rather than a warning: the file is not offered for sharing. A parser mis-step on an
     * unusual layout is exactly the failure this feature cannot afford to paper over, because the
     * user would be handed a photo with its GPS intact under a heading saying it was clean.
     *
     * @property remaining The metadata blocks that survived the strip.
     */
    data class VerificationFailed(val remaining: List<SegmentSummary>) : ExportResult

    /** Writing failed — no space, or the cache was pulled out from under us. */
    data object WriteFailed : ExportResult
}
