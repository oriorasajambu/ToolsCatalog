package com.minion.scaffold.core.emv.model

/**
 * The carrier every parsing step returns.
 *
 * Mirrors `AppResult` from `:core:common` in shape, but is parameterised over [QrParseError]
 * rather than `DomainError` — see [QrParseError] for why the shared type cannot be reused.
 *
 * [Failure] is `EmvParseResult<Nothing>`, so it satisfies any `EmvParseResult<T>` without a cast
 * and a failure can be returned straight through a function expecting a different success type.
 */
sealed interface EmvParseResult<out T> {

    /** Parsing produced [value]. */
    data class Success<T>(val value: T) : EmvParseResult<T>

    /** Parsing stopped at [error]. */
    data class Failure(val error: QrParseError) : EmvParseResult<Nothing>
}
