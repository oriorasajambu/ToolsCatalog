package com.minion.scaffold.feature.qrscan.presentation.compare

import com.minion.scaffold.core.emv.model.DiffStatus
import com.minion.scaffold.feature.qrscan.presentation.report.ReportRow

/**
 * One labelled fact, read across two codes.
 *
 * @property label          The row's label, already resolved — the same one the single-code report
 *   shows for that field.
 * @property status         How the two sides relate.
 * @property baselineValue  The first code's value, absent when only the second has this row.
 * @property candidateValue The second code's value, absent when only the first has this row.
 * @property monospace      Carried through from the report, so an SSID keeps the typeface it had.
 */
internal data class RowDiff(
    val label: String,
    val status: DiffStatus,
    val baselineValue: String?,
    val candidateValue: String?,
    val monospace: Boolean = true,
)

/**
 * Lines up two lists of report rows.
 *
 * Takes the rows the report views already built rather than re-deriving the fields, which is what
 * keeps a comparison saying exactly what the two reports behind it said. It is also what makes the
 * absences work: a contact card omits a blank organisation, so a card that has one and a card that
 * does not produce lists of different lengths, and the row simply appears on one side.
 *
 * Aligned by label and then by occurrence — the same key `ReportRowList` uses for its own list, and
 * the reason two `TEL` lines on a vCard pair up first-with-first instead of collapsing into one.
 */
internal fun diffRows(
    baseline: List<ReportRow>,
    candidate: List<ReportRow>,
): List<RowDiff> {
    val pool = HashMap<String, ArrayDeque<Int>>()
    candidate.forEachIndexed { index, row ->
        pool.getOrPut(row.label) { ArrayDeque() }.addLast(index)
    }
    val taken = BooleanArray(candidate.size)

    return buildList {
        for (row in baseline) {
            val index = pool[row.label]?.removeFirstOrNull()
            if (index == null) {
                add(
                    RowDiff(
                        label = row.label,
                        status = DiffStatus.ONLY_IN_BASELINE,
                        baselineValue = row.value,
                        candidateValue = null,
                        monospace = row.monospace,
                    ),
                )
            } else {
                taken[index] = true
                val counterpart = candidate[index]
                add(
                    RowDiff(
                        label = row.label,
                        status = if (row.value == counterpart.value) {
                            DiffStatus.SAME
                        } else {
                            DiffStatus.CHANGED
                        },
                        baselineValue = row.value,
                        candidateValue = counterpart.value,
                        monospace = row.monospace,
                    ),
                )
            }
        }

        candidate.forEachIndexed { index, row ->
            if (taken[index]) return@forEachIndexed
            add(
                RowDiff(
                    label = row.label,
                    status = DiffStatus.ONLY_IN_CANDIDATE,
                    baselineValue = null,
                    candidateValue = row.value,
                    monospace = row.monospace,
                ),
            )
        }
    }
}

/** How many of these rows are a difference. */
internal fun List<RowDiff>.changedCount(): Int = count { it.status != DiffStatus.SAME }
