package com.minion.scaffold.core.url.model

/**
 * The outcome of writing a link payload.
 *
 * A single [reason] rather than a list, unlike the other formats: there is one field, so there is
 * one thing that can be wrong with it.
 */
sealed interface UrlBuildResult {

    data class Success(val payload: String) : UrlBuildResult

    data class Invalid(val reason: UrlViolationReason) : UrlBuildResult
}

/** Carries no user-facing text; the presentation layer maps it to a `@StringRes`. */
enum class UrlViolationReason {

    REQUIRED,

    /** No host, or whitespace inside — nothing will open it. */
    MALFORMED,

    /** An explicit scheme other than `http` or `https`, such as `mailto:`. */
    UNSUPPORTED_SCHEME,
}
