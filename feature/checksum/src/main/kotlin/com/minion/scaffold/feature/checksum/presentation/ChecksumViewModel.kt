package com.minion.scaffold.feature.checksum.presentation

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.text.model.TextResult
import com.minion.scaffold.core.text.usecase.TransformTextUseCase
import com.minion.scaffold.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The checksum screen's ViewModel.
 *
 * [transformText] is `:core:text`'s existing use case, taken as a constructor parameter and nothing
 * more: it carries an `@Inject` constructor and no dependencies of its own, so Hilt builds it here
 * without this feature declaring a single binding. Reusing it also means the digest this screen
 * shows and the digest the text tool shows come from the same code path — there is no second MD5 in
 * the app to drift from the first.
 *
 * @param transformText Runs one of `:core:text`'s transforms over a string.
 */
@HiltViewModel
internal class ChecksumViewModel @Inject constructor(
    private val transformText: TransformTextUseCase,
) : MviViewModel<ChecksumState, ChecksumIntent, ChecksumEffect>(ChecksumState()) {

    override fun onIntent(intent: ChecksumIntent) {
        when (intent) {
            is ChecksumIntent.InputChanged -> reduce { copy(input = intent.value).recomputed() }

            is ChecksumIntent.AlgorithmChanged -> reduce {
                copy(algorithm = intent.algorithm).recomputed()
            }

            is ChecksumIntent.ExpectedChanged -> reduce {
                copy(expected = intent.value).recomputed()
            }

            ChecksumIntent.CopyDigestRequested -> currentState.digest
                .takeIf { it.isNotEmpty() }
                ?.let { digest ->
                    viewModelScope.launch { emitEffect(ChecksumEffect.CopyDigest(digest)) }
                }
        }
    }

    /**
     * Recomputes the digest and the verdict for the current input, algorithm and expected value.
     *
     * Runs on the state itself rather than in the intent handler, so a keystroke and everything it
     * implies land as one atomic update — the screen never renders a new input beside the previous
     * input's digest, or a stale MATCH beside a digest that has already changed underneath it.
     */
    private fun ChecksumState.recomputed(): ChecksumState {
        val computed = computeDigest()
        return copy(digest = computed, verdict = verdictFor(computed, expected))
    }

    /**
     * The digest of this state's input under its algorithm, or empty when there is nothing to hash.
     *
     * An empty input is left blank rather than shown as the well-defined digest of the empty
     * string. `d41d8cd98f00b204e9800998ecf8427e` is a correct answer to a question nobody asked, and
     * on a screen whose job is comparison it reads as a result rather than as an empty field.
     */
    private fun ChecksumState.computeDigest(): String {
        if (input.isEmpty()) return ""

        return when (val result = transformText(algorithm, input)) {
            is TextResult.Success -> result.output
            // Unreachable while [CHECKSUM_ALGORITHMS] holds only hashes: hashing accepts anything,
            // and only the four decoders can reject their input. Handled rather than thrown so that
            // adding a decoder to that list degrades to a blank digest instead of crashing.
            is TextResult.Failure -> ""
        }
    }
}

/**
 * How a computed digest compares to what the user pasted in.
 *
 * Trimmed and case-insensitive, because a digest is copied from wherever it was published and
 * arrives with whatever that source did to it: `sha256sum` prints lowercase, a release page often
 * prints uppercase, and a copy out of a table brings a trailing newline. Rejecting those as a
 * mismatch would be technically true and useless — the bytes are what is being compared, and none
 * of them changed.
 *
 * @param digest   The digest this screen computed.
 * @param expected What the user pasted in, as typed.
 * @return [Verdict.NOT_COMPARED] while either side is empty, otherwise whether they are the same.
 */
private fun verdictFor(digest: String, expected: String): Verdict {
    val candidate = expected.trim()
    if (digest.isEmpty() || candidate.isEmpty()) return Verdict.NOT_COMPARED

    return if (candidate.equals(digest, ignoreCase = true)) Verdict.MATCH else Verdict.MISMATCH
}
