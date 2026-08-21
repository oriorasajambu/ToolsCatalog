package com.minion.scaffold.feature.qrscan.domain.export

import kotlinx.coroutines.flow.Flow

/**
 * The template a JSON export is rendered from.
 *
 * @property text     The template document.
 * @property source   Where it came from, which decides what the share sheet is allowed to promise.
 * @property label    The imported file's name, for naming it on screen. Empty for the built-in.
 * @property outdated True when it was stored under an older template format and can no longer be
 *   trusted to mean what it did. Always false for the built-in, which ships with the app.
 */
internal data class PaymentSchema(
    val text: String,
    val source: PaymentSchemaSource,
    val label: String = "",
    val outdated: Boolean = false,
)

/** Whether the active schema is the one that ships with the app. */
internal enum class PaymentSchemaSource {

    /** `assets/default_payment_schema.json`. Its sample values are known, so they can be named. */
    BuiltIn,

    /** Imported by the user. The app knows nothing about what it contains. */
    Custom,
}

/**
 * The active schema template.
 *
 * A [Flow] rather than a one-shot read because two things watch it: the settings screen that
 * replaces it, and the share sheet behind it on the back stack, which names the active schema and
 * has to stop promising sample values the moment a custom one is imported.
 */
internal interface PaymentSchemaRepository {

    /** Defaults to the built-in template. */
    val activeSchema: Flow<PaymentSchema>

    /**
     * Stores a validated template as the active schema.
     *
     * @param text  The template document, already through [ValidateSchemaUseCase].
     * @param label The file's display name.
     */
    suspend fun store(text: String, label: String)

    /** Returns to the built-in template, discarding whatever was imported. */
    suspend fun reset()

    /** The built-in template, for exporting as a starting point and for resetting to. */
    suspend fun builtIn(): String
}

/**
 * The template format's own version.
 *
 * Stored beside a template rather than inside it: a version key within the document would mean the
 * template is not quite the thing it produces, and something would have to remember to strip it.
 *
 * Raise this when the *syntax* changes — delimiters, the `tag:` prefix, path separators. Adding a
 * name to the vocabulary is not a syntax change and does not need it; removing one is caught by
 * rendering instead.
 */
internal const val SCHEMA_FORMAT_VERSION = 1
