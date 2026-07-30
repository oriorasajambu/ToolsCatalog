package com.minion.scaffold.feature.qrcreate.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import com.minion.scaffold.core.designsystem.component.encodeQrBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Encodes the payload and writes it to the cache (for sharing) or to `MediaStore` (for keeping).
 *
 * Exports at [EXPORT_SIZE_PX] rather than reusing the on-screen bitmap: what looks fine at 200dp
 * is a blurry mess once it has been through a messaging app's recompression, and a QR that will
 * not scan is worse than no QR.
 *
 * The dispatcher is injected rather than `Dispatchers.IO` being called directly, so a test can
 * substitute a `TestDispatcher` and `advanceUntilIdle()` actually controls this work.
 */
internal class AndroidQrImageExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : QrImageExporter {

    override suspend fun writeShareableImage(payload: String): Uri? = withContext(ioDispatcher) {
        val bitmap = encodeQrBitmap(payload, EXPORT_SIZE_PX) ?: return@withContext null

        try {
            val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
            // One file, overwritten each time. Sharing repeatedly should not slowly fill the
            // cache with near-identical QR codes nobody will ever look at again.
            val file = File(directory, SHARE_FILE_NAME)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }

            FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
        } catch (_: IOException) {
            null
        }
    }

    override suspend fun saveToGallery(payload: String): Boolean = withContext(ioDispatcher) {
        val bitmap = encodeQrBitmap(payload, EXPORT_SIZE_PX) ?: return@withContext false

        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$FILE_PREFIX${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_PNG)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$GALLERY_DIRECTORY",
            )
            // Marks the row incomplete so nothing indexes or shows a half-written file. minSdk is
            // 29, so this and RELATIVE_PATH are always available — and because the write goes
            // through MediaStore, no storage permission is required at all.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pending)
            ?: return@withContext false

        try {
            val written = resolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
            } ?: false

            if (!written) {
                resolver.delete(uri, null, null)
                return@withContext false
            }

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            true
        } catch (_: IOException) {
            // Leaving a pending row behind would be an invisible, permanently unfinished entry in
            // the user's photo library.
            resolver.delete(uri, null, null)
            false
        }
    }

    private companion object {
        /** Large enough to survive a messaging app's recompression and still scan. */
        const val EXPORT_SIZE_PX = 1024

        /** PNG is lossless, so the quality argument is ignored — it is required regardless. */
        const val PNG_QUALITY = 100

        const val MIME_TYPE_PNG = "image/png"
        const val SHARE_DIRECTORY = "qr"
        const val SHARE_FILE_NAME = "qr-code.png"
        const val GALLERY_DIRECTORY = "Minion Tools"
        const val FILE_PREFIX = "qr-"

        /**
         * Must match the authority in this module's manifest.
         *
         * Feature-prefixed: two library modules each declaring `${applicationId}.file provider`
         * would collide at manifest merge, and the failure is a build error nobody expects.
         */
        const val AUTHORITY_SUFFIX = ".qrcreate.fileprovider"
    }
}
