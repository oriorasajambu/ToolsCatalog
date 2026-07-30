package com.minion.scaffold.feature.qrscan.presentation

import android.net.Uri
import com.minion.scaffold.feature.qrscan.data.ImageBarcodeDecoder
import com.minion.scaffold.feature.qrscan.data.ImageDecodeResult
import kotlinx.coroutines.channels.Channel

/**
 * A decoder whose completion the test controls.
 *
 * Hand-written rather than a MockK stub because these tests care about the *gap* between picking
 * an image and the result arriving — that is where the `Decoding` state lives, and where a second
 * pick has to be refused. A stub that returns immediately has no gap to assert against.
 *
 * Backed by a buffered channel rather than a `CompletableDeferred`, so [complete] works whether
 * the coroutine has reached [decode] yet or not. With a deferred, a test that completes before
 * advancing the scheduler leaves the decode awaiting a result that was delivered to nobody — and
 * the symptom is a state stuck on `Decoding`, which reads like a production bug rather than a
 * test-harness one.
 */
internal class FakeImageBarcodeDecoder : ImageBarcodeDecoder {

    private val results = Channel<ImageDecodeResult>(Channel.UNLIMITED)

    var callCount: Int = 0
        private set

    override suspend fun decode(uri: Uri): ImageDecodeResult {
        callCount++
        return results.receive()
    }

    /** Hands the next [decode] call its result, queueing it if the call has not happened yet. */
    fun complete(result: ImageDecodeResult) {
        results.trySend(result)
    }
}
