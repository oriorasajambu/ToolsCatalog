package com.minion.scaffold.feature.qrscan.domain.compare

import javax.inject.Inject

/**
 * A run of characters that exists in one payload and not the other.
 *
 * Its own type rather than `:core:emv`'s `PayloadSpan`: this path has to work for a vCard and a
 * URL as well, and borrowing an EMV type for a format-agnostic diff would misfile the dependency.
 *
 * @property start        The first character's index in its own payload.
 * @property endExclusive One past the last character's index.
 */
internal data class DiffSpan(val start: Int, val endExclusive: Int)

/**
 * Where two payloads disagree, character by character.
 *
 * @property baselineSpans Runs present in the first payload and absent from the second.
 * @property candidateSpans Runs present in the second payload and absent from the first.
 * @property truncated     True when the pair was too large to align and the whole differing middle
 *   is reported as one replacement instead.
 */
internal data class PayloadCharDiff(
    val baselineSpans: List<DiffSpan>,
    val candidateSpans: List<DiffSpan>,
    val truncated: Boolean,
) {

    /** True when the two payloads are the same string. */
    val identical: Boolean
        get() = baselineSpans.isEmpty() && candidateSpans.isEmpty()
}

/**
 * Aligns two payloads character by character.
 *
 * A longest-common-subsequence alignment rather than comparing position against position, because
 * the common EMV case is a value whose *length* changed — an amount going from `15000.00` to
 * `150000.00` shifts every character after it. Position-wise comparison lights the entire remainder
 * of the payload red, which is worse than useless: it hides the one edit inside three hundred
 * false ones.
 *
 * The shared head and tail are trimmed before any table is allocated. Two encodings of the same
 * merchant differ in the middle and agree either side of it, so a three-hundred-character pair
 * routinely collapses to a table of a few dozen cells. That trim is what makes the quadratic
 * algorithm affordable at all.
 */
internal class DiffPayloadCharactersUseCase @Inject constructor() {

    /**
     * Diffs [candidate] against [baseline].
     *
     * @param baseline  The first payload.
     * @param candidate The second payload.
     * @return The differing runs on each side.
     */
    operator fun invoke(baseline: String, candidate: String): PayloadCharDiff {
        val prefix = commonPrefixLength(baseline, candidate)
        val suffix = commonSuffixLength(baseline, candidate, prefix)

        val baselineMiddle = baseline.substring(prefix, baseline.length - suffix)
        val candidateMiddle = candidate.substring(prefix, candidate.length - suffix)

        if (baselineMiddle.isEmpty() && candidateMiddle.isEmpty()) {
            return PayloadCharDiff(emptyList(), emptyList(), truncated = false)
        }

        // A QR code can hold nearly three thousand characters. Two of them sharing no head or tail
        // would want a table of millions of cells, so past a bound the honest answer is "these do
        // not line up" rather than an allocation the device has to survive.
        if (baselineMiddle.length.toLong() * candidateMiddle.length > MAX_CELLS) {
            return PayloadCharDiff(
                baselineSpans = wholeMiddle(prefix, baselineMiddle),
                candidateSpans = wholeMiddle(prefix, candidateMiddle),
                truncated = true,
            )
        }

        return align(baselineMiddle, candidateMiddle, prefix)
    }

    /**
     * The standard subsequence table, filled from the end so the walk that reads it runs forwards.
     *
     * Forwards matters: the runs come out in ascending order, which is what lets a collector merge
     * consecutive indices into a span without sorting afterwards.
     */
    private fun align(baseline: String, candidate: String, offset: Int): PayloadCharDiff {
        val rows = baseline.length
        val columns = candidate.length
        val stride = columns + 1
        val table = IntArray((rows + 1) * stride)

        for (row in rows - 1 downTo 0) {
            for (column in columns - 1 downTo 0) {
                table[row * stride + column] = if (baseline[row] == candidate[column]) {
                    table[(row + 1) * stride + column + 1] + 1
                } else {
                    maxOf(
                        table[(row + 1) * stride + column],
                        table[row * stride + column + 1],
                    )
                }
            }
        }

        val removed = SpanCollector(offset)
        val added = SpanCollector(offset)

        var row = 0
        var column = 0
        while (row < rows && column < columns) {
            when {
                baseline[row] == candidate[column] -> {
                    row++
                    column++
                }
                // Ties go to the baseline so a replacement reads as a removal followed by an
                // addition rather than the other way round — the order the two grids are stacked in.
                table[(row + 1) * stride + column] >= table[row * stride + column + 1] ->
                    removed.add(row++)

                else -> added.add(column++)
            }
        }
        while (row < rows) removed.add(row++)
        while (column < columns) added.add(column++)

        return PayloadCharDiff(
            baselineSpans = removed.finish(),
            candidateSpans = added.finish(),
            truncated = false,
        )
    }

    private fun commonPrefixLength(baseline: String, candidate: String): Int {
        val limit = minOf(baseline.length, candidate.length)
        var count = 0
        while (count < limit && baseline[count] == candidate[count]) count++
        return count
    }

    /** Never allowed to overlap the prefix, or the two would claim the same characters. */
    private fun commonSuffixLength(baseline: String, candidate: String, prefix: Int): Int {
        val limit = minOf(baseline.length, candidate.length) - prefix
        var count = 0
        while (
            count < limit &&
            baseline[baseline.length - 1 - count] == candidate[candidate.length - 1 - count]
        ) {
            count++
        }
        return count
    }

    private fun wholeMiddle(offset: Int, middle: String): List<DiffSpan> =
        if (middle.isEmpty()) emptyList() else listOf(DiffSpan(offset, offset + middle.length))

    /** Gathers ascending indices into runs, so thirty adjacent characters are one span. */
    private class SpanCollector(private val offset: Int) {

        private val spans = mutableListOf<DiffSpan>()
        private var start = NONE
        private var endExclusive = NONE

        fun add(index: Int) {
            when {
                start == NONE -> open(index)
                index == endExclusive -> endExclusive = index + 1
                else -> {
                    flush()
                    open(index)
                }
            }
        }

        fun finish(): List<DiffSpan> {
            flush()
            return spans
        }

        private fun open(index: Int) {
            start = index
            endExclusive = index + 1
        }

        private fun flush() {
            if (start == NONE) return
            spans += DiffSpan(start + offset, endExclusive + offset)
            start = NONE
        }

        private companion object {
            const val NONE = -1
        }
    }

    private companion object {

        /**
         * The largest table worth building, at four bytes a cell — about nine megabytes, held only
         * for the length of the alignment and only on a background thread.
         */
        const val MAX_CELLS = 2_250_000L
    }
}
