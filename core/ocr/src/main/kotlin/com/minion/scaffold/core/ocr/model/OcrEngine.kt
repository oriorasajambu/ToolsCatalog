package com.minion.scaffold.core.ocr.model

/**
 * Which recognizer reads a captured image.
 *
 * Lives here rather than in `:feature:ocr` because three unrelated places need to name it — the
 * stored preference, the recognizer that dispatches on it, and the settings screen — and this is
 * the one module all three already depend on.
 *
 * Persisted by [name], never by ordinal: an ordinal silently remaps every stored preference the
 * moment someone reorders this enum, and nothing fails loudly when it does.
 */
enum class OcrEngine {

    /**
     * ML Kit's bundled Latin model. The default, and what every existing install is already using.
     *
     * Fast enough to run on a viewfinder frame, which is why it backs the live hint boxes
     * regardless of what is selected here.
     */
    MlKit,

    /**
     * PaddleOCR PP-OCRv5 on ONNX Runtime.
     *
     * Slower and heavier — three models, roughly 22MB, extracted to internal storage before first
     * use — in exchange for markedly better readings of dense or small text.
     */
    PaddleOcr,

    ;

    companion object {

        /** [MlKit], so an existing install's behaviour is unchanged until someone opts in. */
        val DEFAULT = MlKit

        /** The entry named [name], or [DEFAULT] when it names nothing — a stored value can outlive its entry. */
        fun ofName(name: String?): OcrEngine = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
