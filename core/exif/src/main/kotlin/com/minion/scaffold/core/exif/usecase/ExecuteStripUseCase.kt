package com.minion.scaffold.core.exif.usecase

import com.minion.scaffold.core.exif.model.StripOperation
import com.minion.scaffold.core.exif.model.StripPlan
import javax.inject.Inject
import java.io.OutputStream

/**
 * Carries out a plan.
 *
 * Deliberately trivial, and deliberately here rather than in the feature. Every judgement was made
 * when the plan was built; this walks a list and writes bytes, so there is nowhere for a decision to
 * hide. It is also the one place that touches the image data, which makes it the place worth being
 * able to read in a single glance.
 *
 * Takes an [OutputStream] rather than returning a `ByteArray` so the feature can write straight to a
 * file: a 20 MB photo already sits in memory once as the input, and holding the output alongside it
 * would double that for no reason.
 */
class ExecuteStripUseCase @Inject constructor() {

    /**
     * Writes the clean file described by [plan] to [destination].
     *
     * @param source      The original file the plan's copy ranges refer into.
     * @param plan        The plan to carry out.
     * @param destination The stream the clean file is written to; flushed, not closed.
     */
    operator fun invoke(source: ByteArray, plan: StripPlan, destination: OutputStream) {
        for (operation in plan.operations) {
            when (operation) {
                is StripOperation.Copy ->
                    destination.write(source, operation.start, operation.length)

                is StripOperation.Insert -> destination.write(operation.bytes)
            }
        }
        destination.flush()
    }

    /**
     * As [invoke], but collects the result in memory — for tests and verification.
     *
     * @param source The original file the plan's copy ranges refer into.
     * @param plan   The plan to carry out.
     * @return The clean file's bytes.
     */
    fun toByteArray(source: ByteArray, plan: StripPlan): ByteArray {
        val output = java.io.ByteArrayOutputStream(plan.outputSize)
        invoke(source, plan, output)
        return output.toByteArray()
    }
}
