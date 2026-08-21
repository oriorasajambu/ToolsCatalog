package com.minion.scaffold.feature.qrscan.presentation

import com.minion.scaffold.feature.qrscan.domain.ScannedFormat

/**
 * Why a second code was turned away mid-comparison.
 *
 * Separate from [QrScanError], which is the reason a screen has *nothing to show*. These are the
 * reasons a screen kept showing what it already had: the baseline is still pinned, the camera is
 * still running, and the only thing that happened is a message. Folding the two together would put
 * cases into `QrScanError`'s `when` that can never produce a failure screen.
 */
internal sealed interface CompareRejection {

    /**
     * The second code is a different kind of thing from the first.
     *
     * @property expected The pinned code's format.
     * @property found    The format of the code just scanned.
     */
    data class FormatMismatch(
        val expected: ScannedFormat,
        val found: ScannedFormat,
    ) : CompareRejection

    /**
     * The second code could not be read at all — damaged, or a format this app does not handle.
     *
     * Deliberately one case rather than the four [QrScanError] distinguishes. Those distinctions
     * exist to help someone repair a payload, and repairing is not what is happening here: the
     * next move is to point the camera at a different code, which is the same next move either way.
     */
    data object Unreadable : CompareRejection
}
