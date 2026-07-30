package com.minion.scaffold.feature.qrscan.presentation

/**
 * Payloads for this module's tests.
 *
 * Aliases [QrScanPreviewData], which already holds a real payload for the previews, rather than
 * carrying a second copy of the same 234 characters. `:core:emv` has its own `EmvSamples` for the
 * parser's tests, but a test source set is not published to consumers, so it cannot be reached
 * from here — and duplicating the constant would leave two payloads to keep in step.
 */
internal object ScanSamples {

    const val QRIS_DYNAMIC: String = QrScanPreviewData.SAMPLE_PAYLOAD

    /** Same length, so it still frames identically and only the checksum differs. */
    val QRIS_TAMPERED: String = QRIS_DYNAMIC.replace("PAK BOS QR 1", "PAK BOS QR 2")
}
