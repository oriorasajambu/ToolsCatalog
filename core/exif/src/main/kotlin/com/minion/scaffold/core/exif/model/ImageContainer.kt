package com.minion.scaffold.core.exif.model

/**
 * A container this module can take apart byte by byte.
 *
 * Deliberately short. HEIC is absent because its metadata lives in ISO base-media boxes and removing
 * it means re-muxing a container — the feature offers a re-encode to JPEG instead, as an explicit
 * choice rather than a silent fallback, so that "every file this produces is pixel-identical to its
 * input" stays true of everything listed here.
 */
enum class ImageContainer(val mimeType: String, val extension: String) {
    Jpeg("image/jpeg", "jpg"),
    Png("image/png", "png"),
    WebP("image/webp", "webp"),
    ;

    companion object {

        /**
         * Identifies a container from its leading bytes.
         *
         * From the content, never from the file name or the `Uri`'s reported MIME type. A picker can
         * hand over a `.jpg` that is really a HEIC, and a stripper that trusted the label would walk
         * a JPEG parser over it and produce something between a corrupt file and a confident lie.
         */
        fun detect(bytes: ByteArray): ImageContainer? = when {
            bytes.startsWith(JPEG_MAGIC) -> Jpeg
            bytes.startsWith(PNG_MAGIC) -> Png
            bytes.startsWith(RIFF_MAGIC) && bytes.startsWith(WEBP_MAGIC, offset = 8) -> WebP
            else -> null
        }

        /**
         * A best-effort name for something unrecognised, for the "cannot strip this" message.
         *
         * Naming the format is the difference between a dead end and an explanation — HEIC in
         * particular is what modern phones shoot, so a user meeting it deserves better than
         * "unsupported file".
         */
        fun describeUnsupported(bytes: ByteArray): String = when {
            bytes.size >= FTYP_END && bytes.startsWith(FTYP_MARKER, offset = FTYP_OFFSET) ->
                String(bytes, FTYP_BRAND_OFFSET, FTYP_BRAND_LENGTH, Charsets.US_ASCII).trim()

            bytes.startsWith(GIF_MAGIC) -> "GIF"
            bytes.startsWith(BMP_MAGIC) -> "BMP"
            else -> "unknown"
        }

        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_MAGIC = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        private val RIFF_MAGIC = "RIFF".toByteArray(Charsets.US_ASCII)
        private val WEBP_MAGIC = "WEBP".toByteArray(Charsets.US_ASCII)
        private val GIF_MAGIC = "GIF8".toByteArray(Charsets.US_ASCII)
        private val BMP_MAGIC = "BM".toByteArray(Charsets.US_ASCII)

        /** ISO base media: a 4-byte size, then `ftyp`, then a 4-character brand — `heic`, `avif`. */
        private val FTYP_MARKER = "ftyp".toByteArray(Charsets.US_ASCII)
        private const val FTYP_OFFSET = 4
        private const val FTYP_BRAND_OFFSET = 8
        private const val FTYP_BRAND_LENGTH = 4
        private const val FTYP_END = FTYP_BRAND_OFFSET + FTYP_BRAND_LENGTH
    }
}

/** Whether [this] contains [prefix] starting at [offset]. Bounds-checked, never throws. */
internal fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0): Boolean {
    if (offset < 0 || offset + prefix.size > size) return false
    for (index in prefix.indices) {
        if (this[offset + index] != prefix[index]) return false
    }
    return true
}
