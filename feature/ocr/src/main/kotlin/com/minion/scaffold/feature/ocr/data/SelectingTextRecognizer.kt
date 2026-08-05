package com.minion.scaffold.feature.ocr.data

import android.graphics.Bitmap
import com.minion.scaffold.core.ocr.model.OcrEngine
import com.minion.scaffold.feature.ocr.data.paddle.PaddleTextRecognizer
import com.minion.scaffold.feature.ocr.domain.OcrPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Routes a recognition to whichever engine the user selected, falling back to ML Kit when the
 * selected one cannot run.
 *
 * PaddleOCR has failure modes ML Kit does not: ONNX Runtime's native library is missing on an
 * unexpected ABI, a clone without git-lfs leaves the models as pointer files, extraction fails on a
 * full disk, or building three sessions runs the device out of memory. None of those are the user's
 * problem and none of them should cost them their text — so the recognition still happens, on the
 * other engine.
 *
 * What it must not do is happen *silently*. The returned [Recognition] names the engine that
 * actually ran, and the ViewModel raises a notice when that is not the one selected. A setting that
 * says PaddleOCR while ML Kit is doing the work would be the hardest kind of bug for a user to
 * diagnose: everything appears to function, and only the results are quietly different.
 *
 * The preference is read per recognition rather than cached, so a change made in settings takes
 * effect on the very next capture without this class having to observe anything.
 */
@Singleton
internal class SelectingTextRecognizer @Inject constructor(
    private val mlKit: MlKitTextRecognizer,
    // A Provider, so selecting ML Kit never constructs the PaddleOCR recognizer — and therefore
    // never touches ONNX Runtime — on a device that would fail to load it anyway.
    private val paddle: Provider<PaddleTextRecognizer>,
    private val preferences: OcrPreferencesRepository,
) : TextRecognizer {

    /** Held so it can be released; only ever the PaddleOCR one, as ML Kit holds nothing. */
    private var activePaddle: PaddleTextRecognizer? = null

    override suspend fun recognize(bitmap: Bitmap): Recognition {
        val preferred = preferences.engine.first()

        if (preferred == OcrEngine.PaddleOcr) {
            runCatching { paddleEngine().recognize(bitmap) }
                .onSuccess { return Recognition(it, OcrEngine.PaddleOcr) }
                .onFailure { failure ->
                    // Cancellation is not a failure and must not become a fallback: a screen closed
                    // mid-recognition would otherwise be caught here and re-run the whole thing on
                    // ML Kit. `safeCall` in :core:network rethrows it for the same reason.
                    if (failure is CancellationException) throw failure

                    // Otherwise deliberately broad. Beyond ordinary exceptions this has to survive
                    // UnsatisfiedLinkError from a missing native library and OutOfMemoryError from
                    // building the sessions — both Errors rather than Exceptions, and both entirely
                    // recoverable here, because the other engine is sitting right there.
                    activePaddle = null
                }
        }

        return Recognition(mlKit.recognize(bitmap), OcrEngine.MlKit)
    }

    override fun release() {
        activePaddle?.release()
        activePaddle = null
    }

    private fun paddleEngine(): PaddleTextRecognizer =
        activePaddle ?: paddle.get().also { activePaddle = it }
}
