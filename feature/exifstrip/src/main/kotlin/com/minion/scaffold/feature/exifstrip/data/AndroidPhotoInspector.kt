package com.minion.scaffold.feature.exifstrip.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import com.minion.scaffold.feature.exifstrip.domain.InspectedPhoto
import com.minion.scaffold.feature.exifstrip.domain.InspectionResult
import com.minion.scaffold.feature.exifstrip.domain.PhotoInspector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Reads a picked photo into memory, along with its metadata.
 *
 * ## The whole file, in one array
 *
 * `:core:exif` plans over a `ByteArray`, so the file is held whole. A 50 MP JPEG is around 20 MB,
 * which is fine; a 200 MB panorama or a mislabelled video is not. The ceiling exists because an
 * `OutOfMemoryError` does not surface as an error message, it surfaces as the app disappearing —
 * and a privacy tool that vanishes when handed an awkward file teaches the user nothing.
 *
 * The alternative, a seekable abstraction over the stream, would let the pure module work without
 * holding the file. It would also make every parser bounds-check into an IO call and every test
 * build a fake stream, for a saving that only matters on files this tool declines anyway.
 *
 * ## Read twice, deliberately
 *
 * The bytes and the `ExifInterface` come from two separate opens of the same `Uri`. Handing
 * `ExifInterface` a stream and then trying to reuse it does not work — it consumes what it needs and
 * leaves the position wherever it landed — and reconstructing a stream from the array to hand back
 * would be the same read done less obviously.
 */
internal class AndroidPhotoInspector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val tagReader: ExifTagReader,
) : PhotoInspector {

    override suspend fun inspect(uri: Uri): InspectionResult = withContext(ioDispatcher) {
        val declaredSize = sizeOf(uri)
        if (declaredSize != null && declaredSize > MAX_BYTES) {
            return@withContext InspectionResult.TooLarge(declaredSize)
        }

        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            // The picker's grant can expire — most often when the screen is restored after process
            // death and the Uri outlived its permission.
            null
        } ?: return@withContext InspectionResult.Unreadable

        // Checked again against what was actually read: the provider's reported size is a hint, and
        // some providers report nothing at all.
        if (bytes.size > MAX_BYTES) {
            return@withContext InspectionResult.TooLarge(bytes.size.toLong())
        }

        val exif = try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        } catch (_: IOException) {
            null
        }

        InspectionResult.Success(
            InspectedPhoto(
                uri = uri,
                bytes = bytes,
                displayName = displayNameOf(uri),
                // A file with no readable Exif is not a failure — a screenshot PNG legitimately has
                // none — so this degrades to an empty report rather than an error.
                metadata = exif?.let(tagReader::read) ?: EMPTY_METADATA,
                orientation = exif?.let(tagReader::orientationOf) ?: ExifInterface.ORIENTATION_NORMAL,
            ),
        )
    }

    private fun sizeOf(uri: Uri): Long? = queryColumn(uri, OpenableColumns.SIZE) { cursor, index ->
        if (cursor.isNull(index)) null else cursor.getLong(index)
    }

    /**
     * The name the provider reports.
     *
     * Worth having because it is frequently the leak nobody thinks about:
     * `IMG_20240115_143022.jpg` carries the date and time in plain text, and a screenshot name
     * carries the app it came from. Stripping `DateTimeOriginal` while shipping the timestamp in the
     * filename would be a bit pointless, so the export renames and the UI says why.
     */
    private fun displayNameOf(uri: Uri): String? =
        queryColumn(uri, OpenableColumns.DISPLAY_NAME) { cursor, index ->
            if (cursor.isNull(index)) null else cursor.getString(index)
        }

    private fun <T> queryColumn(uri: Uri, column: String, read: (android.database.Cursor, Int) -> T?): T? =
        try {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(column)
                if (index < 0) null else read(cursor, index)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            // Providers are free to reject a projection they do not support.
            null
        }

    private companion object {

        /**
         * The largest file this tool will take in.
         *
         * Generous against real photos — a 100 MP raw-ish JPEG lands well under it — and firm enough
         * that holding two copies during the write cannot exhaust a modest heap.
         */
        const val MAX_BYTES = 64L * 1024 * 1024

        val EMPTY_METADATA = com.minion.scaffold.core.exif.model.PhotoMetadata(
            bands = emptyList(),
            other = emptyList(),
            thumbnail = null,
            coordinates = null,
        )
    }
}
