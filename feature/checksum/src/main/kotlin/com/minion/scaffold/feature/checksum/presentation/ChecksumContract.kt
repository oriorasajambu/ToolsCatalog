package com.minion.scaffold.feature.checksum.presentation

import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.text.model.TextOperation

/**
 * What the checksum screen renders.
 *
 * The digest re-runs as the input or the algorithm changes, and the verdict re-runs with it — there
 * is no Verify button, because hashing a pasted string is instant and watching the verdict flip as
 * you correct a typo is the whole point of the tool.
 *
 * [algorithm] is a [TextOperation] rather than an enum of this feature's own. The operations this
 * screen offers are exactly `:core:text`'s three hashes, and a parallel enum would exist only to be
 * mapped back onto them — which is the mapping `TransformTextUseCase` already is.
 *
 * @property input     The text being hashed.
 * @property algorithm The digest currently selected — one of [CHECKSUM_ALGORITHMS].
 * @property digest    The digest of [input] under [algorithm], or empty when there is no input.
 * @property expected  The digest the user was given and is checking against.
 * @property verdict   How [digest] and [expected] compare.
 */
internal data class ChecksumState(
    val input: String = "",
    val algorithm: TextOperation = TextOperation.SHA256,
    val digest: String = "",
    val expected: String = "",
    val verdict: Verdict = Verdict.NOT_COMPARED,
) : UiState

/**
 * How the computed digest compares to the one the user pasted in.
 *
 * Three states rather than a `Boolean`, because "not compared yet" is not "does not match": a
 * screen with nothing pasted into the expected field would otherwise open showing a mismatch, and a
 * verifier that cries wolf before it has been given anything to verify teaches the user to ignore
 * it.
 */
internal enum class Verdict {

    /** Nothing to compare — the input or the expected digest is still empty. */
    NOT_COMPARED,

    /** The digests are the same, ignoring case and surrounding whitespace. */
    MATCH,

    /** The digests differ. */
    MISMATCH,
}

/** Everything the user can do on the checksum screen. */
internal sealed interface ChecksumIntent : UiIntent {

    /**
     * The input text changed.
     *
     * @property value The new input.
     */
    data class InputChanged(val value: String) : ChecksumIntent

    /**
     * A different digest algorithm was selected.
     *
     * @property algorithm The newly selected algorithm.
     */
    data class AlgorithmChanged(val algorithm: TextOperation) : ChecksumIntent

    /**
     * The expected digest changed.
     *
     * @property value The new expected digest.
     */
    data class ExpectedChanged(val value: String) : ChecksumIntent

    /** The user asked to copy the computed digest. */
    data object CopyDigestRequested : ChecksumIntent
}

/** One-shot events from the checksum screen. */
internal sealed interface ChecksumEffect : UiEffect {

    /**
     * Put the digest on the clipboard.
     *
     * @property digest The digest to copy.
     */
    data class CopyDigest(val digest: String) : ChecksumEffect
}
