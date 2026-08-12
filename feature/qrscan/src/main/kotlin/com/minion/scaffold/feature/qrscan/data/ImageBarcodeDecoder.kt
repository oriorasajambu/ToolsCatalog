package com.minion.scaffold.feature.qrscan.data

import android.net.Uri

/**
 * Reads a QR payload out of an image the user picked.
 *
 * An interface with one implementation, which earns its place by being the only thing standing
 * between the ViewModel and ML Kit: without it the gallery path could not be tested at all, since
 * the real decoder needs a `Context`, a real `Uri` and a real image file.
 */
internal interface ImageBarcodeDecoder {

    /**
     * Reads a QR payload out of the image at [uri].
     *
     * @param uri The picked image.
     * @return [ImageDecodeResult.Found] with the payload, or a reason none was read.
     */
    suspend fun decode(uri: Uri): ImageDecodeResult
}

/**
 * What came back from an image.
 *
 * "No QR in this picture" and "this file could not be opened" are separate because the user's next
 * move differs — pick a different photo, versus something is wrong with the file or the
 * permission granted to it.
 */
internal sealed interface ImageDecodeResult {

    /**
     * A QR code was found.
     *
     * @property payload The decoded payload.
     */
    data class Found(val payload: String) : ImageDecodeResult

    /** The image opened fine and simply has no QR code in it. */
    data object NoBarcode : ImageDecodeResult

    /** The image could not be opened or decoded at all. */
    data object Unreadable : ImageDecodeResult
}
