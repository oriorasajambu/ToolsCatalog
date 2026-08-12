package com.minion.scaffold.core.emv.usecase

import com.minion.scaffold.core.emv.model.CrcVerification
import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.PayloadTag
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.model.TagInterpretation
import com.minion.scaffold.core.emv.model.TlvNode
import com.minion.scaffold.core.emv.parser.EmvCrc16
import com.minion.scaffold.core.emv.parser.EmvTagCatalog
import com.minion.scaffold.core.emv.parser.EmvTlvParser
import javax.inject.Inject

/**
 * Breaks a payload into a flat, positioned list of its tags, for highlighting.
 *
 * A sibling to [ParseEmvPayloadUseCase]: same pure, synchronous, `@Inject`-only shape, but where
 * that produces a nested report for reading, this produces the flat [PayloadTag] list the create
 * screen paints colour bands from — every tag located by its payload-absolute span.
 *
 * Degrades to an empty list rather than surfacing a failure. Its only caller feeds it a payload
 * the app's own builder just produced, which always frames; the empty return is the honest answer
 * for the impossible case, and lets the UI fall back to plain text instead of showing a broken
 * breakdown.
 */
class EmvPayloadBreakdownUseCase @Inject constructor() {

    /**
     * The tags of [rawPayload], flattened and located, or an empty list if it does not frame.
     *
     * @param rawPayload The payload to break down, trimmed here as the parser expects.
     * @return Every tag in payload order — templates before their children — each with its span
     *         and, for top-level tags, its decoded [TagInterpretation].
     */
    operator fun invoke(rawPayload: String): List<PayloadTag> {
        val payload = rawPayload.trim()

        val segments = when (val parsed = EmvTlvParser.parse(payload)) {
            is EmvParseResult.Success -> parsed.value
            is EmvParseResult.Failure -> return emptyList()
        }

        return flattenPayloadTags(payload, segments)
    }
}

/**
 * The already-decoded report's tags, flattened and located for highlighting.
 *
 * Reuses the report's own segments and verified checksum rather than re-parsing, so a scanner
 * screen can colour its payload and segment cards from the exact structure it already displays,
 * with the same [PayloadTag] shape the create screen uses.
 *
 * @return Every tag in payload order — templates before their children — each with its span.
 */
fun QrInquiryReport.highlightTags(): List<PayloadTag> =
    flattenPayloadTags(payload, segments.map { it.node }, crc)

/**
 * Flattens already-parsed [segments] into located, interpreted [PayloadTag]s.
 *
 * The one place a [TlvNode] tree becomes the flat highlight model, shared by the create screen's
 * use case and the scanner's report so both colour the same tags the same way. [crc] is the
 * checksum when the caller has already verified it (the report has); when null, tag `63` is
 * recomputed here.
 */
internal fun flattenPayloadTags(
    payload: String,
    segments: List<TlvNode>,
    crc: CrcVerification? = null,
): List<PayloadTag> = EmvTlvParser.flatten(segments).map { flat ->
    PayloadTag(
        path = flat.path,
        tag = flat.node.tag,
        depth = flat.depth,
        isTemplate = flat.node.children.isNotEmpty() || EmvTagCatalog.isTemplate(flat.node.tag),
        rawValue = flat.node.rawValue,
        span = flat.span,
        interpretation = flat.interpretation(payload, crc),
    )
}

/**
 * The decoding for a tag, or [TagInterpretation.None] for anything not decoded.
 *
 * Only top-level tags are interpreted: inside a template the same two-character code means
 * something else entirely — `00` is a globally-unique identifier, not a payload-format indicator —
 * so running the catalog over a sub-tag would mislabel it.
 */
private fun EmvTlvParser.FlatNode.interpretation(
    payload: String,
    crc: CrcVerification?,
): TagInterpretation = when {
    depth > 0 -> TagInterpretation.None

    node.tag == EmvTagCatalog.TAG_CRC -> TagInterpretation.Checksum(
        crc ?: CrcVerification(
            expected = node.rawValue,
            actual = EmvCrc16.compute(payload.dropLast(EmvTagCatalog.CRC_VALUE_LENGTH)),
        ),
    )

    else -> EmvTagCatalog.interpret(node.tag, node.rawValue)
}
