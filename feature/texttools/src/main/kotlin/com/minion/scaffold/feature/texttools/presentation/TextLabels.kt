package com.minion.scaffold.feature.texttools.presentation

import android.content.res.Resources
import com.minion.scaffold.core.text.model.TextError
import com.minion.scaffold.core.text.model.TextOperation
import com.minion.scaffold.feature.texttools.R

/**
 * Names for the operations and their failures.
 *
 * `:core:text` carries no user-facing text — every operation is an enum constant and every failure
 * a typed reason, mapped to a `@StringRes` here, the same way every feature in this app turns a
 * domain enum into words.
 *
 * @receiver The operation to name.
 * @param resources The resources to resolve the label string from.
 * @return The operation's localized label.
 */
// One arm per TextOperation and nothing else. Outside ignoreSingleWhenExpression only because
// the when is an argument to getString rather than the whole body.
@Suppress("CyclomaticComplexMethod")
internal fun TextOperation.label(resources: Resources): String = resources.getString(
    when (this) {
        TextOperation.BASE64_ENCODE -> R.string.texttools_op_base64_encode
        TextOperation.BASE64_DECODE -> R.string.texttools_op_base64_decode
        TextOperation.HEX_ENCODE -> R.string.texttools_op_hex_encode
        TextOperation.HEX_DECODE -> R.string.texttools_op_hex_decode
        TextOperation.URL_ENCODE -> R.string.texttools_op_url_encode
        TextOperation.URL_DECODE -> R.string.texttools_op_url_decode
        TextOperation.HTML_ENCODE -> R.string.texttools_op_html_encode
        TextOperation.HTML_DECODE -> R.string.texttools_op_html_decode
        TextOperation.JSON_PRETTIFY -> R.string.texttools_op_json_prettify
        TextOperation.JSON_MINIFY -> R.string.texttools_op_json_minify
        TextOperation.MD5 -> R.string.texttools_op_md5
        TextOperation.SHA1 -> R.string.texttools_op_sha1
        TextOperation.SHA256 -> R.string.texttools_op_sha256
        TextOperation.UPPERCASE -> R.string.texttools_op_uppercase
        TextOperation.LOWERCASE -> R.string.texttools_op_lowercase
        TextOperation.CAMEL_CASE -> R.string.texttools_op_camel
        TextOperation.SNAKE_CASE -> R.string.texttools_op_snake
        TextOperation.KEBAB_CASE -> R.string.texttools_op_kebab
    },
)

/**
 * The user-facing message for a decode failure.
 *
 * @receiver The failure to describe.
 * @param resources The resources to resolve the message string from.
 * @return The failure's localized message.
 */
internal fun TextError.describe(resources: Resources): String = resources.getString(
    when (this) {
        TextError.NOT_VALID_BASE64 -> R.string.texttools_error_base64
        TextError.NOT_VALID_HEX -> R.string.texttools_error_hex
        TextError.NOT_VALID_JSON -> R.string.texttools_error_json
        TextError.NOT_VALID_URL_ENCODING -> R.string.texttools_error_url
    },
)
