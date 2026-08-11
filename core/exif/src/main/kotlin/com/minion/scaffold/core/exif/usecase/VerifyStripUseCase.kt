package com.minion.scaffold.core.exif.usecase

import com.minion.scaffold.core.exif.model.MetadataKind
import com.minion.scaffold.core.exif.model.PlanResult
import com.minion.scaffold.core.exif.model.SegmentSummary
import com.minion.scaffold.core.exif.model.StripFailure
import javax.inject.Inject

/**
 * What is left in a file this tool produced.
 *
 * The point of the feature is a guarantee, and a guarantee nobody checks is an assertion. Re-reading
 * the output costs one more pass over bytes already in memory, and it turns "the export did not
 * throw" into "here is what the file now contains".
 *
 * It also catches the failure that actually matters. The walkers are written against the formats as
 * specified; real files are written by cameras and editors that interpret those formats loosely. If
 * an unusual layout made the marker walk mis-step and skip a segment it should have removed, nothing
 * else in the pipeline would notice — the plan would build, the write would succeed, and the user
 * would be handed a photo with its GPS intact under a heading saying it was clean.
 */
class VerifyStripUseCase @Inject constructor(
    private val planStrip: PlanStripUseCase,
) {

    /**
     * @param keepIcc must match what the export used, or a deliberately retained colour profile
     *   would be reported as a survivor.
     */
    operator fun invoke(bytes: ByteArray, keepIcc: Boolean): VerificationResult {
        // Planning a strip of the output *is* the inspection: anything the planner would still want
        // to remove is, by definition, metadata that is still in there.
        val result = planStrip(bytes, orientation = ORIENTATION_IRRELEVANT, keepIcc = keepIcc)

        return when (result) {
            is PlanResult.Failure -> VerificationResult.Unreadable(result.failure)

            is PlanResult.Success -> {
                val survivors = result.plan.removed.filterNot { it.kind == MetadataKind.Unknown &&
                    it.byteCount == 0 }

                if (survivors.isEmpty() && result.plan.trailing == null) {
                    VerificationResult.Clean(result.plan.retained)
                } else {
                    VerificationResult.Dirty(survivors, result.plan.trailing?.byteCount ?: 0)
                }
            }
        }
    }

    private companion object {
        /** Verification never rewrites anything, so the orientation it would have written is moot. */
        const val ORIENTATION_IRRELEVANT = 1
    }
}

sealed interface VerificationResult {

    /** Nothing identifying remains. [retained] names what was kept on purpose. */
    data class Clean(val retained: List<SegmentSummary>) : VerificationResult

    /**
     * Something survived.
     *
     * A blocking failure rather than a warning: the export must not be offered for sharing, because
     * the entire value of the operation was the claim that it worked.
     */
    data class Dirty(val remaining: List<SegmentSummary>, val trailingBytes: Int) :
        VerificationResult

    /** The output could not be parsed at all, which means the strip produced a broken file. */
    data class Unreadable(val failure: StripFailure) : VerificationResult
}
