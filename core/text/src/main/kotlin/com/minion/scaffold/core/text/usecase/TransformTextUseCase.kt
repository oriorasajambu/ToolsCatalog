package com.minion.scaffold.core.text.usecase

import com.minion.scaffold.core.text.format.Base64Codec
import com.minion.scaffold.core.text.format.CaseConverter
import com.minion.scaffold.core.text.format.HexCodec
import com.minion.scaffold.core.text.format.HtmlEntityCodec
import com.minion.scaffold.core.text.format.Hashing
import com.minion.scaffold.core.text.format.JsonFormatter
import com.minion.scaffold.core.text.format.UrlCodec
import com.minion.scaffold.core.text.model.TextError
import com.minion.scaffold.core.text.model.TextOperation
import com.minion.scaffold.core.text.model.TextResult
import java.util.Locale
import javax.inject.Inject

/**
 * Runs one [TextOperation] over an input string.
 *
 * The single place that knows which operations can fail. Encode, hash and case conversion always
 * succeed; the four decoders return null on bad input, which becomes a typed [TextError] here so the
 * screen never has to reason about which operation might fail.
 */
class TransformTextUseCase @Inject constructor() {

    /**
     * Runs [operation] over [input].
     *
     * @param operation The transform to apply.
     * @param input     The text to transform.
     * @return [TextResult.Success] with the output, or [TextResult.Failure] with a typed
     *         [TextError] for the decoders that can reject their input.
     */
    operator fun invoke(operation: TextOperation, input: String): TextResult = when (operation) {
        TextOperation.BASE64_ENCODE -> success(Base64Codec.encode(input))
        TextOperation.BASE64_DECODE -> Base64Codec.decode(input).orError(TextError.NOT_VALID_BASE64)
        TextOperation.HEX_ENCODE -> success(HexCodec.encode(input))
        TextOperation.HEX_DECODE -> HexCodec.decode(input).orError(TextError.NOT_VALID_HEX)
        TextOperation.URL_ENCODE -> success(UrlCodec.encode(input))
        TextOperation.URL_DECODE ->
            UrlCodec.decode(input).orError(TextError.NOT_VALID_URL_ENCODING)

        TextOperation.HTML_ENCODE -> success(HtmlEntityCodec.encode(input))
        TextOperation.HTML_DECODE -> success(HtmlEntityCodec.decode(input))
        TextOperation.JSON_PRETTIFY -> JsonFormatter.prettify(input).orError(TextError.NOT_VALID_JSON)
        TextOperation.JSON_MINIFY -> JsonFormatter.minify(input).orError(TextError.NOT_VALID_JSON)
        TextOperation.MD5 -> success(Hashing.md5(input))
        TextOperation.SHA1 -> success(Hashing.sha1(input))
        TextOperation.SHA256 -> success(Hashing.sha256(input))
        TextOperation.UPPERCASE -> success(input.uppercase(Locale.ROOT))
        TextOperation.LOWERCASE -> success(input.lowercase(Locale.ROOT))
        TextOperation.CAMEL_CASE -> success(CaseConverter.toCamel(input))
        TextOperation.SNAKE_CASE -> success(CaseConverter.toSnake(input))
        TextOperation.KEBAB_CASE -> success(CaseConverter.toKebab(input))
    }

    private fun success(output: String): TextResult = TextResult.Success(output)

    private fun String?.orError(reason: TextError): TextResult =
        this?.let { TextResult.Success(it) } ?: TextResult.Failure(reason)
}
