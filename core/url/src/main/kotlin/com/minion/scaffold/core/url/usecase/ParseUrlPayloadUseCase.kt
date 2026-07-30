package com.minion.scaffold.core.url.usecase

import com.minion.scaffold.core.url.format.UrlFormat
import javax.inject.Inject

/**
 * Reads a link payload, or reports that this is not one.
 *
 * Null is not a failure — a payment code is not a broken link. The same reason `QrScanError` wraps
 * `QrParseError` rather than extending it.
 *
 * Requires an explicit `http://` or `https://`. A bare `example.com` is not accepted, however
 * plausible it looks: at scan time it is indistinguishable from arbitrary text, and accepting it
 * would have this format swallow every payload with a dot in it before the others were tried.
 */
class ParseUrlPayloadUseCase @Inject constructor() {

    operator fun invoke(payload: String): String? {
        val trimmed = payload.trim()

        val hasSupportedScheme = UrlFormat.SUPPORTED_SCHEMES.any { scheme ->
            trimmed.startsWith("$scheme${UrlFormat.SCHEME_SEPARATOR}", ignoreCase = true)
        }
        if (!hasSupportedScheme) return null

        // `https://` on its own is a scheme with nothing to open.
        if (UrlFormat.hostOf(trimmed).isBlank()) return null

        return trimmed
    }
}
