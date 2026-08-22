package com.minion.scaffold.core.emv.usecase

import com.minion.scaffold.core.emv.model.CrcDiff
import com.minion.scaffold.core.emv.model.DiffStatus
import com.minion.scaffold.core.emv.model.EmvComparison
import com.minion.scaffold.core.emv.model.EmvSegment
import com.minion.scaffold.core.emv.model.EmvSegmentDiff
import com.minion.scaffold.core.emv.model.EmvSubtagDiff
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.model.TlvNode
import com.minion.scaffold.core.emv.parser.EmvTagCatalog
import javax.inject.Inject

/**
 * Lines two decoded payloads up field by field.
 *
 * Lives here rather than in the feature because the alignment is EMV knowledge, not presentation:
 * it needs the template range and the identifier subtag from [EmvTagCatalog], which is `internal`
 * to this module. A copy of "merchant accounts occupy tags 26 to 51" inside `:feature:qrscan`
 * would be a second source of truth for the payload's own shape, which is the drift the module
 * split exists to prevent.
 *
 * Synchronous and pure, like every other use case here — the work is a walk over a few dozen
 * segments.
 */
class CompareEmvPayloadsUseCase @Inject constructor() {

    /**
     * Compares [candidate] against [baseline].
     *
     * Order is not significance: a payload whose tags were re-encoded in a different sequence
     * reports every field as unchanged, because acquirers reorder freely and flagging that as a
     * difference would be a false alarm on a functionally identical code.
     *
     * @param baseline  The first code scanned — the one being compared *against*.
     * @param candidate The second code scanned.
     * @return Every field from either payload, aligned.
     */
    operator fun invoke(
        baseline: QrInquiryReport,
        candidate: QrInquiryReport,
    ): EmvComparison {
        // Tag 63 never appears as a segment row. It is derived from every other field, so it is
        // reported on its own terms in CrcDiff rather than counted as a difference.
        val baselineSegments = baseline.segments.filterNot { it.node.tag == EmvTagCatalog.TAG_CRC }
        val candidateSegments = candidate.segments.filterNot { it.node.tag == EmvTagCatalog.TAG_CRC }

        val matched = IntArray(baselineSegments.size) { UNMATCHED }
        val taken = BooleanArray(candidateSegments.size)

        // The identifier first, and only for merchant accounts: an account is the one thing in an
        // EMV payload that legitimately changes tag between two encodings of the same merchant, so
        // matching it on the scheme it names is what stops "26 removed, 27 added" on a pair that
        // differs in nothing.
        matchOn(baselineSegments, candidateSegments, matched, taken, accountsOnly = true) {
            it.globallyUniqueIdentifier
        }

        // Then everything, accounts included, by the slot it occupies. Runs after the identifier
        // pass so two accounts that swapped slots read as moved rather than as two rewrites, and
        // before the raw-value pass so an account that stayed put is never stolen by one elsewhere.
        matchOn(baselineSegments, candidateSegments, matched, taken, accountsOnly = false) {
            it.node.tag
        }

        // A last pass for an account that moved without a usable identifier — an unframed template
        // has no subtags to read one from. Restricted to accounts on purpose: matching *any* two
        // segments on their value alone would pair a country code with a merchant city that
        // happened to read the same, and then call it a move.
        matchOn(baselineSegments, candidateSegments, matched, taken, accountsOnly = true) {
            it.node.rawValue
        }

        return EmvComparison(
            segments = buildSegments(baselineSegments, candidateSegments, matched, taken),
            crc = CrcDiff(baseline = baseline.crc, candidate = candidate.crc),
        )
    }

    /**
     * Pairs up whatever is still unmatched, using [key] as the identity.
     *
     * Occurrence order breaks ties. Two segments carrying the same tag is not valid EMV, but this
     * tool is pointed at payloads that are not valid EMV, and pairing the first with the first is
     * the only answer that does not depend on which one a map happened to keep.
     *
     * A null [key] means "not eligible in this pass" — an unframed template has no identifier to
     * offer, and skipping it here leaves it to the passes that follow.
     */
    // Threads the two parallel match arrays plus the pass's own predicate. Bundling the arrays
    // would hide that both are mutated in step, which is the whole mechanism of the matcher.
    @Suppress("LongParameterList")
    private fun matchOn(
        baseline: List<EmvSegment>,
        candidate: List<EmvSegment>,
        matched: IntArray,
        taken: BooleanArray,
        accountsOnly: Boolean,
        key: (EmvSegment) -> String?,
    ) {
        val pool = HashMap<String, ArrayDeque<Int>>()
        candidate.forEachIndexed { index, segment ->
            if (taken[index]) return@forEachIndexed
            if (accountsOnly && !segment.isMerchantAccount) return@forEachIndexed
            val identity = key(segment) ?: return@forEachIndexed
            pool.getOrPut(identity) { ArrayDeque() }.addLast(index)
        }

        baseline.forEachIndexed { index, segment ->
            if (matched[index] != UNMATCHED) return@forEachIndexed
            if (accountsOnly && !segment.isMerchantAccount) return@forEachIndexed
            val identity = key(segment) ?: return@forEachIndexed
            val queue = pool[identity] ?: return@forEachIndexed
            val found = queue.removeFirstOrNull() ?: return@forEachIndexed
            matched[index] = found
            taken[found] = true
        }
    }

    /** The baseline's rows in its own order, then whatever the candidate has that it does not. */
    private fun buildSegments(
        baseline: List<EmvSegment>,
        candidate: List<EmvSegment>,
        matched: IntArray,
        taken: BooleanArray,
    ): List<EmvSegmentDiff> = buildList {
        baseline.forEachIndexed { index, segment ->
            val counterpart = matched[index].takeIf { it != UNMATCHED }?.let(candidate::get)
            add(
                if (counterpart == null) {
                    EmvSegmentDiff(
                        tag = segment.node.tag,
                        status = DiffStatus.ONLY_IN_BASELINE,
                        baseline = segment,
                        candidate = null,
                    )
                } else {
                    pairDiff(segment, counterpart)
                },
            )
        }

        candidate.forEachIndexed { index, segment ->
            if (taken[index]) return@forEachIndexed
            add(
                EmvSegmentDiff(
                    tag = segment.node.tag,
                    status = DiffStatus.ONLY_IN_CANDIDATE,
                    baseline = null,
                    candidate = segment,
                ),
            )
        }
    }

    /**
     * Two segments that were matched, read against each other.
     *
     * The row is titled with the *candidate's* tag — where the field is now — and carries the
     * baseline's as [EmvSegmentDiff.movedFromTag] when the two disagree. Reading "27, moved from
     * 26" against the code in front of you is the way round that helps; the reverse is not.
     */
    private fun pairDiff(baseline: EmvSegment, candidate: EmvSegment): EmvSegmentDiff =
        EmvSegmentDiff(
            tag = candidate.node.tag,
            status = if (baseline.node.rawValue == candidate.node.rawValue) {
                DiffStatus.SAME
            } else {
                DiffStatus.CHANGED
            },
            baseline = baseline,
            candidate = candidate,
            subtags = alignSubtags(baseline.node.children, candidate.node.children),
            movedFromTag = baseline.node.tag.takeIf { it != candidate.node.tag },
        )

    /**
     * A template's children, aligned by tag and then by occurrence.
     *
     * Templates nest one level only, so a child never has children of its own and this does not
     * recurse.
     */
    private fun alignSubtags(
        baseline: List<TlvNode>,
        candidate: List<TlvNode>,
    ): List<EmvSubtagDiff> {
        if (baseline.isEmpty() && candidate.isEmpty()) return emptyList()

        val pool = HashMap<String, ArrayDeque<Int>>()
        candidate.forEachIndexed { index, node ->
            pool.getOrPut(node.tag) { ArrayDeque() }.addLast(index)
        }
        val taken = BooleanArray(candidate.size)

        return buildList {
            for (node in baseline) {
                val index = pool[node.tag]?.removeFirstOrNull()
                if (index == null) {
                    add(
                        EmvSubtagDiff(
                            tag = node.tag,
                            status = DiffStatus.ONLY_IN_BASELINE,
                            baselineValue = node.rawValue,
                            candidateValue = null,
                        ),
                    )
                } else {
                    taken[index] = true
                    val counterpart = candidate[index]
                    add(
                        EmvSubtagDiff(
                            tag = node.tag,
                            status = if (node.rawValue == counterpart.rawValue) {
                                DiffStatus.SAME
                            } else {
                                DiffStatus.CHANGED
                            },
                            baselineValue = node.rawValue,
                            candidateValue = counterpart.rawValue,
                        ),
                    )
                }
            }

            candidate.forEachIndexed { index, node ->
                if (taken[index]) return@forEachIndexed
                add(
                    EmvSubtagDiff(
                        tag = node.tag,
                        status = DiffStatus.ONLY_IN_CANDIDATE,
                        baselineValue = null,
                        candidateValue = node.rawValue,
                    ),
                )
            }
        }
    }

    private val EmvSegment.isMerchantAccount: Boolean
        get() = node.tag.toIntOrNull()?.let { it in EmvTagCatalog.MERCHANT_ACCOUNT_TAGS } == true

    /**
     * The scheme this account names, upper-cased, or null when it does not say.
     *
     * An unframed template has no subtags to read, which is exactly the case the raw-value pass
     * exists to catch.
     */
    private val EmvSegment.globallyUniqueIdentifier: String?
        get() = node.children
            .firstOrNull { it.tag == EmvTagCatalog.SUBTAG_GLOBALLY_UNIQUE_IDENTIFIER }
            ?.rawValue
            ?.uppercase()

    private companion object {
        const val UNMATCHED = -1
    }
}
