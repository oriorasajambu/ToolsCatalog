package com.minion.scaffold.feature.ocr.data.paddle

import android.content.Context
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.feature.ocr.data.paddle.vendor.ModelFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unpacks the PP-OCRv5 models from the APK onto internal storage.
 *
 * ONNX Runtime opens a session from a **file path**, and an asset inside an APK is not one — so the
 * four files have to exist on disk before anything can run. This is the local replacement for
 * upstream's `ModelManager`, which downloads them over HTTP instead.
 *
 * **No checksums.** Upstream verifies SHA-256 because its files arrive from a network; ours ship
 * inside a signed APK, where integrity is already guaranteed by the package signature. Re-verifying
 * would be ~40 lines re-checking what the platform has checked.
 *
 * The version marker means a shipped model change is picked up on upgrade: the constant moves, the
 * marker no longer matches, and everything is written again over the stale copies.
 */
@Singleton
internal class PaddleModelAssets @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Serialises extraction. Two captures recognised in quick succession after an upgrade would
     * otherwise both find the marker stale and write the same 22MB over each other.
     */
    private val mutex = Mutex()

    suspend fun ensureExtracted(): AppResult<ModelFiles> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching { extract() }
                .fold(
                    onSuccess = { AppResult.Success(it) },
                    // Realistically a full disk. There is nothing the app can do about it and
                    // nothing specific to tell the user, so it becomes the generic failure and the
                    // caller falls back to ML Kit.
                    onFailure = { AppResult.Failure(DomainError.Unknown) },
                )
        }
    }

    private fun extract(): ModelFiles {
        val directory = File(context.filesDir, MODEL_DIRECTORY).apply { mkdirs() }
        val marker = File(directory, VERSION_MARKER)
        val current = marker.takeIf { it.exists() }?.runCatching { readText().trim() }?.getOrNull()

        if (current != MODEL_VERSION) {
            for (asset in ASSETS) {
                copyAsset(asset, File(directory, asset))
            }
            // Written last, so an extraction interrupted halfway leaves the marker absent and the
            // next attempt redoes it rather than trusting half a model set.
            marker.writeText(MODEL_VERSION)
        }

        return ModelFiles(
            version = MODEL_VERSION,
            baseDir = directory,
            detectionModel = File(directory, DETECTION),
            recognitionModel = File(directory, RECOGNITION),
            classificationModel = File(directory, CLASSIFICATION),
            dictionaryFile = File(directory, DICTIONARY),
        )
    }

    private fun copyAsset(name: String, target: File) {
        // Via a temporary file: a copy killed partway through must not leave a truncated model that
        // looks complete to the next run.
        val temporary = File(target.parentFile, "$name.tmp")

        context.assets.open("$ASSET_DIRECTORY/$name").use { input ->
            temporary.outputStream().use(input::copyTo)
        }

        if (target.exists()) target.delete()
        check(temporary.renameTo(target)) { "Could not move $name into place" }
    }

    private companion object {

        const val ASSET_DIRECTORY = "ocr/ppocrv5"
        const val MODEL_DIRECTORY = "ocr/ppocrv5"

        /** Bump whenever the shipped models change, so an upgrade overwrites the extracted set. */
        const val MODEL_VERSION = "pp-ocrv5-202410"
        const val VERSION_MARKER = ".model_version"

        const val DETECTION = "det.onnx"
        const val RECOGNITION = "rec.onnx"
        const val CLASSIFICATION = "cls.onnx"
        const val DICTIONARY = "ppocrv5_dict.txt"

        val ASSETS = listOf(DETECTION, RECOGNITION, CLASSIFICATION, DICTIONARY)
    }
}
