package com.minion.scaffold.feature.qrcreate.presentation

import android.net.Uri
import com.minion.scaffold.feature.qrcreate.data.QrImageExporter
import kotlinx.coroutines.channels.Channel

/**
 * An exporter the test drives.
 *
 * Backed by channels rather than plain return values so a test can hold an export open and assert
 * on the window while it is running — which is where `exporting` lives and where a second tap has
 * to be refused. Completing before the coroutine reaches the call works too: a buffered channel
 * makes the ordering irrelevant.
 */
internal class FakeQrImageExporter : QrImageExporter {

    private val shareResults = Channel<Uri?>(Channel.UNLIMITED)
    private val saveResults = Channel<Boolean>(Channel.UNLIMITED)

    var shareCallCount: Int = 0
        private set

    var saveCallCount: Int = 0
        private set

    /** The payload most recently handed to an export, so a test can check what was encoded. */
    var lastPayload: String? = null
        private set

    override suspend fun writeShareableImage(payload: String): Uri? {
        shareCallCount++
        lastPayload = payload
        return shareResults.receive()
    }

    override suspend fun saveToGallery(payload: String): Boolean {
        saveCallCount++
        lastPayload = payload
        return saveResults.receive()
    }

    fun completeShare(uri: Uri?) {
        shareResults.trySend(uri)
    }

    fun completeSave(saved: Boolean) {
        saveResults.trySend(saved)
    }
}
