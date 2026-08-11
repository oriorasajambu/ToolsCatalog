package com.minion.scaffold.feature.exifstrip.domain

import android.net.Uri
import com.minion.scaffold.core.exif.model.PhotoMetadata

/**
 * Reads a picked photo: its bytes, its metadata, and enough about it to describe it.
 *
 * An interface so the ViewModel is testable — `ExifInterface`, `ContentResolver` and `Uri` are all
 * Android — the same seam, and the same reason, as `:feature:level`'s `GravitySource`.
 */
internal interface PhotoInspector {

    suspend fun inspect(uri: Uri): InspectionResult
}

internal sealed interface InspectionResult {

    data class Success(val photo: InspectedPhoto) : InspectionResult

    /** The file could not be opened or read at all. */
    data object Unreadable : InspectionResult

    /**
     * The file is larger than this tool will hold in memory.
     *
     * The strip works over the whole file as one array, so a ceiling is a real constraint rather
     * than defensiveness. Refusing with a clear message beats an `OutOfMemoryError` on a mid-range
     * phone, which surfaces as the app simply vanishing.
     */
    data class TooLarge(val byteCount: Long) : InspectionResult
}

internal data class InspectedPhoto(
    val uri: Uri,
    val bytes: ByteArray,

    /** As reported by the picker. Shown because it frequently *is* a timestamp. */
    val displayName: String?,

    val metadata: PhotoMetadata,

    /**
     * The EXIF orientation, read here and passed into the pure planner.
     *
     * Read with `ExifInterface` rather than re-derived in `:core:exif`, because the platform already
     * handles every format and quirk correctly and a second implementation would be one more thing
     * to keep in step for no gain.
     */
    val orientation: Int,
) {
    // ByteArray in a data class compares by reference, which would make two reads of the same photo
    // unequal. Nothing depends on that today; spelling it out stops it becoming a puzzle later.
    override fun equals(other: Any?): Boolean =
        this === other || (other is InspectedPhoto && uri == other.uri)

    override fun hashCode(): Int = uri.hashCode()
}
