package com.minion.scaffold.feature.exifstrip.domain

import android.net.Uri
import com.minion.scaffold.core.exif.model.SegmentSummary
import com.minion.scaffold.core.exif.model.StripFailure
import com.minion.scaffold.core.exif.model.TrailingData

/** Writes the cleaned copy and hands back something shareable. */
internal interface CleanCopyExporter {

    /**
     * Whether this file can be stripped at all, without writing anything.
     *
     * Answered while the user is still reading what was found, so an unstrippable container is
     * reported there rather than after they press a button that then declines to do anything.
     * Returns `null` when the file is fine.
     */
    suspend fun probe(photo: InspectedPhoto, keepIcc: Boolean): StripFailure?

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
