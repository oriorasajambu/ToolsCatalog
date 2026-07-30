package com.minion.scaffold.feature.qrscan.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Decodes a picked image with the same ML Kit detector the camera uses.
 *
 * The dispatcher is injected rather than `Dispatchers.IO` being called directly, so a test can
 * substitute a `TestDispatcher` and `advanceUntilIdle()` actually controls this work.
 */
internal class MlKitImageBarcodeDecoder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ImageBarcodeDecoder {

    override suspend fun decode(uri: Uri): ImageDecodeResult = withContext(ioDispatcher) {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (_: IOException) {
            return@withContext ImageDecodeResult.Unreadable
        }

        // A scanner per call, closed afterward. The camera keeps one alive for its whole session
        // because it runs on every frame; a one-shot decode holding a native detector open would
        // just be a leak.
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )

        try {
            val payload = scanner.process(image).await()
                .firstNotNullOfOrNull(Barcode::getRawValue)

            payload?.let(ImageDecodeResult::Found) ?: ImageDecodeResult.NoBarcode
        } catch (_: Exception) {
            // ML Kit reports every detection failure as a task exception, and none of them are
            // distinguishable to a user: the picture could not be read.
            ImageDecodeResult.Unreadable
        } finally {
            scanner.close()
        }
    }
}

/**
 * Awaits a Play Services [Task] without pulling in `kotlinx-coroutines-play-services`.
 *
 * One suspension point is not worth another dependency, and the wrapper is the whole of what that
 * library would provide here. Cancellable, so a canceled screen does not leave the continuation
 * hanging.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancelQuietly() }
}

private fun CancellableContinuation<*>.cancelQuietly() {
    if (isActive) cancel()
}
