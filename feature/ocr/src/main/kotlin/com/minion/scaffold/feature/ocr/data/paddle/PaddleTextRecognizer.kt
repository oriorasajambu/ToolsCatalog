package com.minion.scaffold.feature.ocr.data.paddle

import android.content.Context
import android.graphics.Bitmap
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.ocr.model.BoundingBox
import com.minion.scaffold.core.ocr.model.RecognizedLine
import com.minion.scaffold.core.ocr.model.RecognizedText
import com.minion.scaffold.core.ocr.usecase.GroupLinesIntoBlocksUseCase
import com.minion.scaffold.core.ocr.usecase.OrderBlocksUseCase
import com.minion.scaffold.feature.ocr.data.OcrResult
import com.minion.scaffold.feature.ocr.data.TextRecognitionEngine
import com.minion.scaffold.feature.ocr.data.paddle.vendor.OcrProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Reads text with PaddleOCR PP-OCRv5, running on ONNX Runtime.
 *
 * The pipeline itself is vendored — see `vendor/README.md`. What lives here is the adaptation:
 * getting the models onto disk, owning the three ONNX sessions, and reshaping line-granularity
 * output into the blocks the rest of the app selects at.
 *
 * **Not a `@Singleton`.** The sessions hold roughly 22MB of weights plus ORT's arena, and are built
 * to live only as long as the OCR screen — `SelectingTextRecognizer` owns one of these and releases
 * it when the ViewModel clears. Holding it app-wide would pin that memory on a device that is
 * probably also running the camera.
 *
 * Engine unavailability is thrown rather than returned. `UnsatisfiedLinkError` on an unexpected
 * ABI, a missing model (a clone without git-lfs leaves pointer files where weights should be), or
 * an `OutOfMemoryError` building the sessions are all "this engine cannot run here" rather than
 * "this image could not be read", and the caller has to tell them apart to fall back honestly.
 */
internal class PaddleTextRecognizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelAssets: PaddleModelAssets,
    private val groupLines: GroupLinesIntoBlocksUseCase,
    private val orderBlocks: OrderBlocksUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TextRecognitionEngine {

    /** Guards lazy creation: two captures in flight must not build two sets of sessions. */
    private val mutex = Mutex()

    private var processor: OcrProcessor? = null

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(ioDispatcher) {
        val processor = obtainProcessor()

        // Ente's own thresholds: keep lines scoring 0.8 or better. Retried at 0.5 only when that
        // leaves nothing at all, so a dim or awkward photo yields shaky text the user can correct
        // rather than a bare "no text found" — the blocks come back marked low-confidence either
        // way, and correcting is cheaper than reshooting.
        val result = processor.processImage(bitmap, includeAllConfidenceScores = false)
            .takeIf { it.texts.isNotEmpty() }
            ?: processor.processImage(bitmap, includeAllConfidenceScores = true)

        val lines = result.toRecognizedLines()
        if (lines.isEmpty()) OcrResult.NoText else OcrResult.Found(lines.toRecognizedText())
    }

    /** Drops the ONNX sessions. Safe to call more than once, and safe to recognise again after. */
    fun release() {
        processor?.close()
        processor = null
    }

    private suspend fun obtainProcessor(): OcrProcessor = mutex.withLock {
        processor ?: buildProcessor().also { processor = it }
    }

    private suspend fun buildProcessor(): OcrProcessor {
        val models = when (val extraction = modelAssets.ensureExtracted()) {
            is AppResult.Success -> extraction.data
            is AppResult.Failure -> error("PP-OCRv5 models could not be extracted")
        }

        return OcrProcessor(context = context, modelFiles = models)
    }

    /**
     * Ente reports one entry per detected *line*, as an oriented quadrilateral.
     *
     * The quad collapses to its axis-aligned bounds here: everything downstream — row grouping, the
     * tap targets on the overlay — works in rectangles, and carrying an orientation only to discard
     * it later would spread the compromise across three modules instead of holding it in one place.
     */
    private fun com.minion.scaffold.feature.ocr.data.paddle.vendor.OcrResult.toRecognizedLines():
        List<RecognizedLine> = boxes.mapIndexedNotNull { index, box ->
        val text = texts.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@mapIndexedNotNull null
        val bounds = box.boundingRect()

        RecognizedLine(
            text = text,
            box = BoundingBox(
                left = bounds.left.roundToInt(),
                top = bounds.top.roundToInt(),
                right = bounds.right.roundToInt(),
                bottom = bounds.bottom.roundToInt(),
            ),
            // Always reported, unlike ML Kit's — this is the CTC score for the whole line.
            confidence = scores.getOrNull(index),
        )
    }

    private fun List<RecognizedLine>.toRecognizedText(): RecognizedText =
        RecognizedText(blocks = orderBlocks(groupLines(this)))
}
