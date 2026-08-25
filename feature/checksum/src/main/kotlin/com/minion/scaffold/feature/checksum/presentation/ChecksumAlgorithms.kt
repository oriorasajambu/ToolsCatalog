package com.minion.scaffold.feature.checksum.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.minion.scaffold.core.text.model.TextOperation
import com.minion.scaffold.feature.checksum.R

/**
 * One entry in the algorithm picker: an operation `:core:text` already implements, and the name to
 * show for it.
 *
 * The label rides with the entry rather than being resolved by a `when` over [TextOperation],
 * because such a `when` has to be exhaustive over all eighteen transforms to say anything about
 * three — and the fifteen it would have no name for could only be an `else` that throws. Pairing
 * them here makes the list the single place an algorithm is declared: one line adds it to the
 * picker with its name, and one that is missing a name cannot be added at all.
 *
 * A `@StringRes`, not a resolved `String`: this list is built once at class-init time, so a string
 * resolved then would not follow a locale change.
 *
 * @property operation The transform that computes this digest.
 * @property labelRes  The string resource naming it in the picker.
 */
@Immutable
internal data class ChecksumAlgorithm(
    val operation: TextOperation,
    @param:StringRes val labelRes: Int,
)

/**
 * The digests this screen offers, in the order the picker lists them.
 *
 * The hash third of [TextOperation], written out rather than filtered by its category, so that a
 * transform added to `:core:text`'s `HASH` group cannot appear here without someone deciding it
 * belongs and naming it. Weakest first: the list reads as an ordering, and MD5 is where a published
 * file checksum usually starts.
 */
internal val CHECKSUM_ALGORITHMS: List<ChecksumAlgorithm> = listOf(
    ChecksumAlgorithm(TextOperation.MD5, R.string.checksum_algorithm_md5),
    ChecksumAlgorithm(TextOperation.SHA1, R.string.checksum_algorithm_sha1),
    ChecksumAlgorithm(TextOperation.SHA256, R.string.checksum_algorithm_sha256),
)
