package com.minion.scaffold.core.url.model

/**
 * The outcome of writing a link payload.
 *
 * A single [reason] rather than a list, unlike the other formats: there is one field, so there is
 * one thing that can be wrong with it.
 */
sealed interface UrlBuildResult {

    /**
     * The input was a usable link and produced a payload.
     *
     * @property payload The normalised URL, with a scheme guaranteed present.
     */
    data class Success(val payload: String) : UrlBuildResult

    /**
     * The input was rejected.
     *
     * @property reason Why the input could not be written as a link.
     */
    data class Invalid(val reason: UrlViolationReason) : UrlBuildResult
}

/** Why a link input was rejected. Carries no user-facing text; mapped to a `@StringRes`. */
enum class UrlViolationReason {

    /** Left blank. */
    REQUIRED,

    /** No host, or whitespace inside — nothing will open it. */
    MALFORMED,

    /** An explicit scheme other than `http` or `https`, such as `mailto:`. */
    UNSUPPORTED_SCHEME,
}
