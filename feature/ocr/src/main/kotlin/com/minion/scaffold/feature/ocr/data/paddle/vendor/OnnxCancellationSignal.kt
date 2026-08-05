/*
 * Vendored from ente-io/mobile_ocr @ 37aee4c4ff77c59a4ab46e272e31a53a035f628e
 * https://github.com/ente-io/mobile_ocr
 *
 * MIT License — Copyright (c) 2025 Laurens Priem. Full text in LICENSE beside this file.
 *
 * Changes from upstream: package renamed, and the `TextRecognizer` class renamed to
 * `PaddleRecognitionModel` to free that name for this feature's own recognizer seam. Otherwise
 * unmodified — see README.md for why it is kept that way.
 */
package com.minion.scaffold.feature.ocr.data.paddle.vendor

import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.CancellationException

class OnnxCancellationSignal {
    private val lock = Any()
    private val activeRuns = mutableSetOf<OrtSession.RunOptions>()

    @Volatile
    private var cancelled = false

    val isCancelled: Boolean
        get() = cancelled

    fun cancel() {
        synchronized(lock) {
            if (cancelled) {
                return
            }
            cancelled = true
            activeRuns.forEach { options ->
                runCatching { options.setTerminate(true) }
            }
        }
    }

    fun ensureActive() {
        if (cancelled) {
            throw CancellationException("OCR request was cancelled")
        }
    }

    fun run(
        session: OrtSession,
        inputs: Map<String, OnnxTensorLike>
    ): OrtSession.Result {
        ensureActive()
        val options = OrtSession.RunOptions()
        synchronized(lock) {
            if (cancelled) {
                options.close()
                throw CancellationException("OCR request was cancelled")
            }
            activeRuns.add(options)
        }

        try {
            val result = session.run(inputs, options)
            if (cancelled) {
                result.close()
                throw CancellationException("OCR request was cancelled")
            }
            return result
        } catch (error: OrtException) {
            if (cancelled) {
                throw CancellationException("OCR request was cancelled").also {
                    it.initCause(error)
                }
            }
            throw error
        } finally {
            synchronized(lock) {
                activeRuns.remove(options)
                options.close()
            }
        }
    }
}

internal fun runOnnx(
    session: OrtSession,
    inputs: Map<String, OnnxTensorLike>,
    cancellationSignal: OnnxCancellationSignal?
): OrtSession.Result {
    return cancellationSignal?.run(session, inputs) ?: session.run(inputs)
}
