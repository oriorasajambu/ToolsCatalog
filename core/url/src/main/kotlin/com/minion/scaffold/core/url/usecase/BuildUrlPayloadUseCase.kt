package com.minion.scaffold.core.url.usecase

import com.minion.scaffold.core.url.format.UrlFormat
import com.minion.scaffold.core.url.model.UrlBuildResult
import com.minion.scaffold.core.url.model.UrlViolationReason
import javax.inject.Inject

/**
 * Writes a link payload — which, for a URL QR, is just the URL itself.
 *
 * Normalises a missing scheme to `https://`, because someone typing `example.com` into a field
 * labelled *Link* has said what they mean. [ParseUrlPayloadUseCase] deliberately does **not** do
 * the same: guessing that typed text is a link is helpful, while guessing that *scanned* text is
 * one would have the scanner claim anything containing a dot.
 */
class BuildUrlPayloadUseCase @Inject constructor() {

    operator fun invoke(input: String): UrlBuildResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return UrlBuildResult.Invalid(UrlViolationReason.REQUIRED)
        }

        val scheme = UrlFormat.schemeOf(trimmed)
        if (scheme != null && scheme !in UrlFormat.SUPPORTED_SCHEMES) {
            return UrlBuildResult.Invalid(UrlViolationReason.UNSUPPORTED_SCHEME)
        }

        val payload = when (scheme) {
            null -> "${UrlFormat.DEFAULT_SCHEME}${UrlFormat.SCHEME_SEPARATOR}$trimmed"
            else -> trimmed
        }

        // Whitespace anywhere is fatal in a way a stray unicode character is not: a space ends the
        // URL for most readers, so the code would open something shorter than what was typed.
        if (payload.any(Char::isWhitespace)) {
            return UrlBuildResult.Invalid(UrlViolationReason.MALFORMED)
        }

        if (UrlFormat.hostOf(payload).isBlank()) {
            return UrlBuildResult.Invalid(UrlViolationReason.MALFORMED)
        }

        return UrlBuildResult.Success(payload)
    }
}
