package com.minion.scaffold.feature.qrcreate.data

import android.net.Uri

/**
 * Writes a payload out as a PNG, somewhere it can leave the app.
 *
 * An interface with one implementation, which earns its place the same way `ImageBarcodeDecoder`
 * does in the scan feature: it is the only thing between the ViewModel and `MediaStore`, and
 * without it the export paths could not be tested at all.
 *
 * Takes a payload rather than a bitmap so nothing format-specific reaches this layer — the Wi-Fi
 * format that comes later exports through exactly this interface, unchanged.
 */
internal interface QrImageExporter {

    /**
     * Writes the QR for [payload] to a shareable location.
     *
     * @param payload The payload to encode.
     * @return A file the share sheet can hand to another app, or `null` if it could not be written.
     */
    suspend fun writeShareableImage(payload: String): Uri?

    /**
     * Saves the QR for [payload] to the device's photo library.
     *
     * @param payload The payload to encode.
     * @return `true` if the image was saved.
     */
    suspend fun saveToGallery(payload: String): Boolean
}
