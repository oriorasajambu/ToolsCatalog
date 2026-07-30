package com.minion.scaffold.core.emv.parser

import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.core.emv.model.TlvNode

/**
 * Splits an EMV payload into its tag-length-value segments.
 *
 * Framing only — this object assigns no meaning to any tag beyond knowing which ones may nest.
 * What a value *is* belongs to [EmvTagCatalog].
 */
internal object EmvTlvParser {

    private const val TAG_LENGTH = 2
    private const val LENGTH_LENGTH = 2
    private const val HEADER_LENGTH = TAG_LENGTH + LENGTH_LENGTH

    /**
     * Reads [payload] into a flat list of segments, each with its sub-segments where the tag is a
     * template.
     *
     * Fails on framing problems only. A structurally valid payload that is missing mandatory tags
     * still parses — those are the caller's checks, because a payload with no tag `63` is still
     * worth showing to whoever is trying to work out why it is broken.
     */
    fun parse(payload: String): EmvParseResult<List<TlvNode>> {
        if (payload.isBlank()) return EmvParseResult.Failure(QrParseError.EmptyPayload)

        // An EMV payload opens with a numeric tag. Anything else — a URL, a WiFi block, plain
        // text — is a different kind of barcode rather than a damaged one, and saying so lets the
        // UI tell the user to scan a different code instead of implying this one is corrupt.
        if (payload.length < TAG_LENGTH || !payload.take(TAG_LENGTH).all(Char::isDigit)) {
            return EmvParseResult.Failure(QrParseError.NotAnEmvPayload)
        }

        return readSegments(data = payload, allowNesting = true)
    }

    /**
     * Reads [data] to exhaustion.
     *
     * Exact consumption is structural rather than checked: the loop advances by exactly the
     * declared length each time and a length that would run past the end is rejected, so
     * returning [EmvParseResult.Success] already means every character was accounted for. That is
     * what makes the result trustworthy as a nesting test in [readChildren].
     *
     * Offsets in a failure are relative to [data]. That is the whole payload for the top-level
     * call, and a template's value for a nested one — but a nested failure is never returned to
     * the caller, so every offset that escapes this object is a payload offset.
     */
    private fun readSegments(
        data: String,
        allowNesting: Boolean,
    ): EmvParseResult<List<TlvNode>> {
        val segments = mutableListOf<TlvNode>()
        var cursor = 0

        while (cursor < data.length) {
            if (data.length - cursor < HEADER_LENGTH) {
                return EmvParseResult.Failure(QrParseError.MalformedTlv(cursor))
            }

            val tag = data.substring(cursor, cursor + TAG_LENGTH)
            val declaredLength = data.substring(cursor + TAG_LENGTH, cursor + HEADER_LENGTH)

            if (!tag.all(Char::isDigit) || !declaredLength.all(Char::isDigit)) {
                return EmvParseResult.Failure(QrParseError.MalformedTlv(cursor))
            }

            // Decimal, not hexadecimal. A hex reading of "15" is 21, which puts the cursor six
            // characters past where the next tag starts and turns the rest of the payload into
            // convincing nonsense.
            val length = declaredLength.toInt()
            val valueStart = cursor + HEADER_LENGTH
            val available = data.length - valueStart

            if (length > available) {
                return EmvParseResult.Failure(
                    QrParseError.LengthOverrun(
                        tag = tag,
                        declaredLength = length,
                        available = available,
                    ),
                )
            }

            val rawValue = data.substring(valueStart, valueStart + length)
            segments += TlvNode(
                tag = tag,
                length = length,
                rawValue = rawValue,
                children = if (allowNesting && EmvTagCatalog.isTemplate(tag)) {
                    readChildren(rawValue)
                } else {
                    emptyList()
                },
            )

            cursor = valueStart + length
        }

        return EmvParseResult.Success(segments)
    }

    /**
     * Attempts to read [value] as nested segments, returning empty on anything less than a clean
     * parse.
     *
     * A template tag is an invitation to nest, not a guarantee. Tag `26` in a live QRIS payload
     * holds four subtags; the same range also carries plain merchant identifiers that happen to
     * begin with digits, and forcing those through a sub-parse yields garbage subtags with
     * nonsense lengths. Requiring the sub-parse to consume the value exactly is what separates
     * the two, and it costs nothing: a plain identifier essentially never frames cleanly by
     * accident.
     *
     * One level deep, deliberately. Every template the specification defines holds plain values,
     * so recursing further can only invent structure that is not there.
     */
    private fun readChildren(value: String): List<TlvNode> {
        if (value.length < HEADER_LENGTH) return emptyList()

        return when (val result = readSegments(value, allowNesting = false)) {
            is EmvParseResult.Success -> result.value
            is EmvParseResult.Failure -> emptyList()
        }
    }
}
