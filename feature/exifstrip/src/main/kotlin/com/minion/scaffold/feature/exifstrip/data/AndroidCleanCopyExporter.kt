package com.minion.scaffold.feature.exifstrip.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import com.minion.scaffold.core.exif.model.ImageContainer
import com.minion.scaffold.core.exif.model.PlanResult
import com.minion.scaffold.core.exif.model.StripFailure
import com.minion.scaffold.core.exif.usecase.ExecuteStripUseCase
import com.minion.scaffold.core.exif.usecase.PlanStripUseCase
import com.minion.scaffold.core.exif.usecase.VerificationResult
import com.minion.scaffold.core.exif.usecase.VerifyStripUseCase
import com.minion.scaffold.feature.exifstrip.domain.CleanCopyExporter
import com.minion.scaffold.feature.exifstrip.domain.ExportResult
import com.minion.scaffold.feature.exifstrip.domain.InspectedPhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Executes a strip plan into the app cache and hands back a shareable `Uri`.
 *
 * ## Never the photo library
 *
 * The clean copy goes to the app's own cache and out through a `FileProvider`. It never enters
 * `MediaStore`, which matters more than it looks: a photo in the gallery gets indexed, backed up to
 * whatever cloud gallery is signed in, and sits next to the original where the two can be confused.
 * The use case is "strip this in order to send it", and the share sheet is that path exactly.
 *
 * ## One working file
 *
 * A single file, overwritten on each export, exactly as `AndroidQrImageExporter` does. Bounded by
 * construction with no cleanup logic to get wrong — which for a directory holding photos the user
 * specifically wanted handled carefully is worth more than the flexibility of keeping several.
 *
 * ## Renamed
 *
 * `IMG_20240115_143022.jpg` is a timestamp in plain text, and a screenshot's name carries the app it
 * came from. Removing `DateTimeOriginal` while shipping the date in the filename would be a bit
 * pointless, so the output takes a neutral fixed name and the UI says the original carried a date.
 */
internal class AndroidCleanCopyExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val planStrip: PlanStripUseCase,
    private val executeStrip: ExecuteStripUseCase,
    private val verifyStrip: VerifyStripUseCase,
) : CleanCopyExporter {

    override suspend fun probe(photo: InspectedPhoto, keepIcc: Boolean): StripFailure? =
        withContext(ioDispatcher) {
            when (val planned = planStrip(photo.bytes, photo.orientation, keepIcc)) {
                is PlanResult.Failure -> planned.failure
                is PlanResult.Success -> null
            }
        }

    override suspend fun export(photo: InspectedPhoto, keepIcc: Boolean): ExportResult =
        withContext(ioDispatcher) {
            val planned = planStrip(photo.bytes, photo.orientation, keepIcc)
            val plan = when (planned) {
                is PlanResult.Failure -> return@withContext ExportResult.Rejected(planned.failure)
                is PlanResult.Success -> planned.plan
            }

            val output = executeStrip.toByteArray(photo.bytes, plan)

            // Re-read what was just produced. See VerifyStripUseCase for why this is not optional.
            when (val verification = verifyStrip(output, keepIcc)) {
                is VerificationResult.Dirty ->
                    return@withContext ExportResult.VerificationFailed(verification.remaining)

                is VerificationResult.Unreadable ->
                    return@withContext ExportResult.VerificationFailed(emptyList())

                is VerificationResult.Clean -> Unit
            }

            val name = outputName(plan.container.extension)
            val uri = write(name, output) ?: return@withContext ExportResult.WriteFailed

            ExportResult.Success(
                uri = uri,
                fileName = name,
                byteCount = output.size,
                originalByteCount = photo.bytes.size,
                removed = plan.removed,
                retained = plan.retained,
                trailing = plan.trailing,
                recompressed = false,
            )
        }

    /**
     * Decode, re-encode, and produce a JPEG carrying nothing.
     *
     * The one path that touches pixels. A `Bitmap` holds no metadata, so `compress` writes a file
     * with none — no marker walk required, and no guarantee of fidelity either. Reserved for
     * containers `:core:exif` cannot take apart, and labelled as recompressed everywhere it appears.
     */
    override suspend fun convertToCleanJpeg(photo: InspectedPhoto): ExportResult =
        withContext(ioDispatcher) {
            val bitmap = try {
                BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size)
            } catch (_: OutOfMemoryError) {
                null
            } ?: return@withContext ExportResult.WriteFailed

            val name = outputName(ImageContainer.Jpeg.extension)
            val file = workingFile(name)

            val written = try {
                directory().mkdirs()
                file.outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, CONVERSION_QUALITY, stream)
                }
            } catch (_: IOException) {
                false
            } finally {
                bitmap.recycle()
            }

            if (!written) return@withContext ExportResult.WriteFailed

            val uri = uriFor(file) ?: return@withContext ExportResult.WriteFailed

            ExportResult.Success(
                uri = uri,
                fileName = name,
                byteCount = file.length().toInt(),
                originalByteCount = photo.bytes.size,
                removed = emptyList(),
                retained = emptyList(),
                trailing = null,
                recompressed = true,
            )
        }

    private fun write(name: String, bytes: ByteArray): Uri? = try {
        directory().mkdirs()
        val file = workingFile(name)
        file.outputStream().use { it.write(bytes) }
        uriFor(file)
    } catch (_: IOException) {
        null
    }

    /**
     * Clears the working directory, then names the one file.
     *
     * Wiping on write rather than when the screen closes avoids racing the share sheet: a receiving
     * app may not have read the file yet when the user navigates back, and deleting it then produces
     * a share that silently delivers nothing.
     */
    private fun workingFile(name: String): File {
        val directory = directory()
        directory.listFiles()?.forEach { it.delete() }
        return File(directory, name)
    }

    private fun directory() = File(context.cacheDir, WORKING_DIRECTORY)

    private fun uriFor(file: File): Uri? = try {
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun outputName(extension: String) = "$OUTPUT_BASE_NAME.$extension"

    private companion object {

        const val WORKING_DIRECTORY = "clean"

        /**
         * Fixed and predictable rather than random.
         *
         * The recipient loses whatever the original name meant, which is the point — but a name like
         * `clean-4a91f0.jpg` looks like something went wrong, and people forward files they can read
         * the name of.
         */
        const val OUTPUT_BASE_NAME = "photo"

        /** High enough that the conversion is not the visible problem with an already-lossy path. */
        const val CONVERSION_QUALITY = 95

        /** Must match the authority in this module's manifest. */
        const val AUTHORITY_SUFFIX = ".exifstrip.fileprovider"
    }
}
