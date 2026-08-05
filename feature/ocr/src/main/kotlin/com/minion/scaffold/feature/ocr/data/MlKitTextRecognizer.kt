package com.minion.scaffold.feature.ocr.data

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.minion.scaffold.core.camera.await
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import com.minion.scaffold.core.ocr.usecase.OrderBlocksUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Recognises text with ML Kit's bundled Latin model.
 *
 * Bundled rather than the Play-Services-delivered variant, matching the choice already recorded
 * for barcode scanning: the tool has to work on a device with no Play Services, and a first use
 * must not block on a model download.
 *
 * A recognizer per call, closed afterwards. `OcrAnalyzer` keeps one alive for its whole session
 * because it runs on every surviving frame; a one-shot recognition holding a native detector open
 * would just be a leak. Same split as `MlKitImageBarcodeDecoder`.
 */
internal class MlKitTextRecognizer @Inject constructor(
    private val orderBlocks: OrderBlocksUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TextRecognitionEngine {

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(ioDispatcher) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            // Rotation zero: the caller hands over a bitmap already uprighted by `ImageLoader`.
            // Passing the original EXIF rotation here as well would rotate it a second time.
            val image = InputImage.fromBitmap(bitmap, IMAGE_ALREADY_UPRIGHT)
            val text = recognizer.process(image).await().toRecognizedText(orderBlocks)

            if (text.isEmpty) OcrResult.NoText else OcrResult.Found(text)
        } catch (_: Exception) {
            // ML Kit reports every recognition failure as a task exception, and none of them are
            // distinguishable to a user: the picture could not be read.
            OcrResult.Unreadable
        } finally {
            recognizer.close()
        }
    }

    private companion object {
        const val IMAGE_ALREADY_UPRIGHT = 0
    }
}
