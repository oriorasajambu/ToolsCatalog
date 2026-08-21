package com.minion.scaffold.feature.qrscan.domain.compare

import com.minion.scaffold.core.emv.usecase.CompareEmvPayloadsUseCase
import com.minion.scaffold.feature.qrscan.domain.ScannedContent
import com.minion.scaffold.feature.qrscan.domain.format
import javax.inject.Inject

/**
 * Builds the comparison of two scanned codes, or refuses.
 *
 * The mirror of `DecodeScannedPayloadUseCase`: that one decides what a single code *is*, this one
 * decides what two of them amount to. Both are the place where format knowledge lives, so neither
 * the ViewModel nor the screen has to hold a list of which formats exist.
 *
 * Synchronous, like the decode it follows. The structural work is a walk over a few dozen segments;
 * the one genuinely expensive part of comparing — the character alignment — is deliberately not
 * here, because it is only needed if the user asks to see it.
 */
internal class CompareScannedContentUseCase @Inject constructor(
    private val compareEmvPayloads: CompareEmvPayloadsUseCase,
) {

    /**
     * Compares [candidate] against [baseline].
     *
     * @param baseline  The pinned first code.
     * @param candidate The code just scanned.
     * @return The comparison, or null when the two are different formats and there is nothing
     *         useful to align.
     */
    operator fun invoke(baseline: ScannedContent, candidate: ScannedContent): QrComparison? {
        if (baseline.format != candidate.format) return null

        val fields = when {
            baseline is ScannedContent.Payment && candidate is ScannedContent.Payment ->
                FieldComparison.Payment(compareEmvPayloads(baseline.report, candidate.report))

            else -> FieldComparison.Flat
        }

        return QrComparison(baseline = baseline, candidate = candidate, fields = fields)
    }
}
