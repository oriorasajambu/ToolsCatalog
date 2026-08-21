package com.minion.scaffold.feature.qrscan.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** A picked document, read. */
internal sealed interface SchemaDocument {

    /**
     * @property text  The file's contents.
     * @property label The file's display name, for naming the schema on screen.
     */
    data class Read(val text: String, val label: String) : SchemaDocument

    /** The document could not be opened or decoded. */
    data object Unreadable : SchemaDocument
}

/**
 * Reads a template a user picked from storage.
 *
 * An interface so the settings ViewModel can be tested without a `ContentResolver` — the same
 * reason [ImageBarcodeDecoder] is one.
 */
internal interface SchemaDocumentReader {

    /**
     * Reads [uri] as text.
     *
     * @param uri The document the picker returned.
     * @return Its contents and name, or that it could not be read.
     */
    suspend fun read(uri: Uri): SchemaDocument
}

/**
 * Reads through the platform's document provider.
 *
 * Storage Access Framework, so nothing here needs a storage permission: the picker grants access to
 * the one document the user chose and nothing else.
 */
internal class AndroidSchemaDocumentReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SchemaDocumentReader {

    override suspend fun read(uri: Uri): SchemaDocument = withContext(ioDispatcher) {
        try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@withContext SchemaDocument.Unreadable

            SchemaDocument.Read(text = text, label = displayName(uri))
        } catch (_: IOException) {
            SchemaDocument.Unreadable
        } catch (_: SecurityException) {
            // A stale URI from a previous session, whose permission grant did not survive.
            SchemaDocument.Unreadable
        }
    }

    /**
     * The file's own name, falling back to the last path segment.
     *
     * A provider is not obliged to supply a display name, and the fallback is what the user sees
     * beside "Custom schema" — so it is worth having rather than showing nothing.
     */
    private fun displayName(uri: Uri): String {
        val queried = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }

        return queried ?: uri.lastPathSegment.orEmpty()
    }
}
