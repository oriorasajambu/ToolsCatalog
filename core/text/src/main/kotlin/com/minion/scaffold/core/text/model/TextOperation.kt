package com.minion.scaffold.core.text.model

/**
 * Every transform the text tool offers, grouped so the picker can label its sections.
 *
 * An enum rather than a sealed hierarchy of function objects: the operation is a *choice* the user
 * makes, it has to survive being the selected item in a dropdown, and the transform it maps to is
 * `TransformTextUseCase`'s business, not the model's.
 */
enum class TextOperation(val category: TextOperationCategory) {

    BASE64_ENCODE(TextOperationCategory.ENCODING),
    BASE64_DECODE(TextOperationCategory.ENCODING),
    HEX_ENCODE(TextOperationCategory.ENCODING),
    HEX_DECODE(TextOperationCategory.ENCODING),
    URL_ENCODE(TextOperationCategory.WEB),
    URL_DECODE(TextOperationCategory.WEB),
    HTML_ENCODE(TextOperationCategory.WEB),
    HTML_DECODE(TextOperationCategory.WEB),
    JSON_PRETTIFY(TextOperationCategory.WEB),
    JSON_MINIFY(TextOperationCategory.WEB),
    MD5(TextOperationCategory.HASH),
    SHA1(TextOperationCategory.HASH),
    SHA256(TextOperationCategory.HASH),
    UPPERCASE(TextOperationCategory.CASE),
    LOWERCASE(TextOperationCategory.CASE),
    CAMEL_CASE(TextOperationCategory.CASE),
    SNAKE_CASE(TextOperationCategory.CASE),
    KEBAB_CASE(TextOperationCategory.CASE),
}

enum class TextOperationCategory { ENCODING, WEB, HASH, CASE }
