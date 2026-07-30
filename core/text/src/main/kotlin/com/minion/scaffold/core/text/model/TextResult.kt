package com.minion.scaffold.core.text.model

/**
 * The outcome of a transform.
 *
 * Only the decode operations can fail — a Base64 string that isn't Base64, a JSON string that isn't
 * JSON. Encoding, hashing and case conversion always succeed, so they only ever return [Success].
 */
sealed interface TextResult {

    data class Success(val output: String) : TextResult

    data class Failure(val reason: TextError) : TextResult
}

/** Why a transform could not read its input. Carries no user-facing text. */
enum class TextError {
    NOT_VALID_BASE64,
    NOT_VALID_HEX,
    NOT_VALID_JSON,
    NOT_VALID_URL_ENCODING,
}
