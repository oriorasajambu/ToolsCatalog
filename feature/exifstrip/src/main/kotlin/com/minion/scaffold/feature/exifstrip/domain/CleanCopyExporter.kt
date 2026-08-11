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
     */
    suspend fun probe(photo: InspectedPhoto, keepIcc: Boolean): ProbeResult

    suspend fun export(photo: InspectedPhoto, keepIcc: Boolean): ExportResult

    /**
     * The HEIC path: decode and re-encode as a JPEG with nothing attached.
     *
     * Separate from [export] rather than a fallback inside it, because it breaks the guarantee that
     * every other output is pixel-identical to its input. An exception that is invisible is not an
     * exception, it is a lie with a special case.
     */
    suspend fun convertToCleanJpeg(photo: InspectedPhoto): ExportResult
}

/** What a dry run found. */
internal data class ProbeResult(
    val failure: StripFailure?,
    /** Blocks the strip would remove — the container's own account of what is in it. */
    val removable: List<SegmentSummary>,
    val trailing: TrailingData?,
)

internal sealed interface ExportResult {

    /**
     * A file ready to share, and the evidence that it is clean.
     *
     * [verified] is not decoration. The whole value of the operation is a claim about what the file
     * no longer contains, and a claim nobody checked is an assertion — so the export re-reads its own
     * output and reports what it found.
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

    /** The file could not be planned. Carries the reason so the UI can offer a conversion. */
    data class Rejected(val failure: StripFailure) : ExportResult

    /**
     * The export ran, and the result still contains something.
     *
     * Blocking rather than a warning: the file is not offered for sharing. A parser mis-step on an
     * unusual layout is exactly the failure this feature cannot afford to paper over, because the
     * user would be handed a photo with its GPS intact under a heading saying it was clean.
     */
    data class VerificationFailed(val remaining: List<SegmentSummary>) : ExportResult

    /** Writing failed — no space, or the cache was pulled out from under us. */
    data object WriteFailed : ExportResult
}
