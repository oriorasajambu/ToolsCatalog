package com.minion.scaffold.core.emv.usecase

import com.minion.scaffold.core.emv.model.CrcVerification
import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.EmvSegment
import com.minion.scaffold.core.emv.model.PayloadSpan
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.core.emv.model.TagInterpretation
import com.minion.scaffold.core.emv.model.TlvNode
import com.minion.scaffold.core.emv.parser.EmvCrc16
import com.minion.scaffold.core.emv.parser.EmvTagCatalog
import com.minion.scaffold.core.emv.parser.EmvTlvParser
import javax.inject.Inject

/**
 * Turns a scanned or pasted payload into a [QrInquiryReport].
 *
 * No repository and no Hilt `@Module`: parsing is a pure function of its input, so there is
 * nothing to bind and Hilt constructs this from the `@Inject` constructor alone. It is a class
 * rather than a top-level function so the ViewModel can be tested against a fake if the decoding
 * rules ever grow a dependency.
 *
 * Synchronous, deliberately. The work is a single pass over a string a few hundred characters
 * long; dispatching it to `Dispatchers.Default` would cost more in context switching than the
 * parse itself.
 */
class ParseEmvPayloadUseCase @Inject constructor() {

    operator fun invoke(rawPayload: String): EmvParseResult<QrInquiryReport> {
        // Scanners and clipboards both add surrounding whitespace; a trailing newline would
        // otherwise be included in the checksummed range and fail every payload.
        val payload = rawPayload.trim()

        return when (val parsed = EmvTlvParser.parse(payload)) {
            is EmvParseResult.Failure -> parsed
            is EmvParseResult.Success -> buildReport(payload, parsed.value)
        }
    }

    private fun buildReport(
        payload: String,
        segments: List<TlvNode>,
    ): EmvParseResult<QrInquiryReport> {
        // Spans come from the parser's own framing arithmetic rather than being recomputed here.
        // A successful parse of a non-blank payload always yields at least one segment, so both
        // branches below have something concrete to point at.
        val first = segments.firstOrNull()
        if (first == null || first.tag != EmvTagCatalog.TAG_PAYLOAD_FORMAT_INDICATOR) {
            return EmvParseResult.Failure(
                QrParseError.MissingPayloadFormatIndicator(
                    span = if (first == null) {
                        PayloadSpan.at(0)
                    } else {
                        EmvTlvParser.spanOf(segments, 0)
                    },
                    foundTag = first?.tag.orEmpty(),
                ),
            )
        }

        val crcSegment = segments.lastOrNull()
        if (crcSegment == null ||
            crcSegment.tag != EmvTagCatalog.TAG_CRC ||
            crcSegment.length != EmvTagCatalog.CRC_VALUE_LENGTH
        ) {
            // The *last segment*, not a caret at the tail. "This payload ends on tag 58" is
            // something a reader can act on; a zero-width mark past the final character is not.
            return EmvParseResult.Failure(
                QrParseError.MissingCrc(
                    span = if (crcSegment == null) {
                        PayloadSpan.at(payload.length)
                    } else {
                        EmvTlvParser.spanOf(segments, segments.lastIndex)
                    },
                    foundTag = crcSegment?.tag.orEmpty(),
                    foundLength = crcSegment?.length ?: 0,
                ),
            )
        }

        // The checksum covers everything up to and including its own `6304` header. Since tag 63
        // is the final segment and holds exactly four characters, that range is the payload minus
        // its last four — which keeps this independent of how the segments were framed.
        val verification = CrcVerification(
            expected = crcSegment.rawValue,
            actual = EmvCrc16.compute(payload.dropLast(EmvTagCatalog.CRC_VALUE_LENGTH)),
        )

        val report = QrInquiryReport(
            payload = payload,
            segments = segments.map { segment -> segment.toReportSegment(verification) },
            crc = verification,
        )
        return EmvParseResult.Success(report)
    }

    private fun TlvNode.toReportSegment(verification: CrcVerification): EmvSegment = EmvSegment(
        node = this,
        interpretation = if (tag == EmvTagCatalog.TAG_CRC) {
            TagInterpretation.Checksum(verification)
        } else {
            EmvTagCatalog.interpret(tag, rawValue)
        },
    )
}
