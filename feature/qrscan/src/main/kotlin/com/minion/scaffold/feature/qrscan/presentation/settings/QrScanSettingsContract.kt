package com.minion.scaffold.feature.qrscan.presentation.settings

import android.net.Uri
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.feature.qrscan.domain.export.PlaceholderName
import com.minion.scaffold.feature.qrscan.domain.export.PaymentSchemaSource

/**
 * What the schema settings screen renders.
 *
 * @property source      Which schema is active.
 * @property label       The imported file's name. Empty under the built-in.
 * @property outdated    True when the stored template predates the current template format.
 * @property placeholders The reference, resolved against the scanned code when there is one.
 * @property importError Why the last import was refused, or null.
 */
internal data class QrScanSettingsState(
    val source: PaymentSchemaSource = PaymentSchemaSource.BuiltIn,
    val label: String = "",
    val outdated: Boolean = false,
    val placeholders: List<PlaceholderRow> = emptyList(),
    val importError: SchemaImportError? = null,
) : UiState {

    /** True when there is an imported template to discard. */
    val canReset: Boolean get() = source == PaymentSchemaSource.Custom
}

/**
 * One line of the placeholder reference.
 *
 * @property token       What a template writes, e.g. `{{merchant_pan}}` without its braces.
 * @property description What the value is.
 * @property value       What it is worth for the code the screen was opened with, or null when it
 *   was opened without one. An empty string means the code carries nothing there, which is a
 *   different statement from not having asked.
 */
internal data class PlaceholderRow(
    val token: String,
    val description: String,
    val value: String? = null,
) {
    constructor(name: PlaceholderName, value: String? = null) :
        this(token = name.token, description = name.description, value = value)
}

/** Why an imported template was refused. */
internal sealed interface SchemaImportError {

    /** The file does not parse as JSON. */
    data object NotJson : SchemaImportError

    /**
     * The template names values this app does not have.
     *
     * All of them at once — fixing a typo only to be told about the next one is a bad way to spend
     * an afternoon with a 1.5 KB document.
     *
     * @property tokens The offending placeholders.
     */
    data class UnknownPlaceholders(val tokens: List<String>) : SchemaImportError

    /** The file could not be opened or read. */
    data object Unreadable : SchemaImportError
}

/** Everything the user can do on the schema settings screen. */
internal sealed interface QrScanSettingsIntent : UiIntent {

    /** A file chosen from the document picker, to be validated and stored. */
    data class SchemaPicked(val uri: Uri) : QrScanSettingsIntent

    /** Send the active template out, as the starting point for editing it elsewhere. */
    data object ExportRequested : QrScanSettingsIntent

    /** Discard the imported template and return to the built-in. */
    data object ResetRequested : QrScanSettingsIntent

    /** Put the last import failure away. */
    data object ErrorDismissed : QrScanSettingsIntent

    /** Copy one placeholder token, so it can go straight into a template. */
    data class CopyTokenRequested(val token: String) : QrScanSettingsIntent
}

/** One-shot events from the schema settings screen. */
internal sealed interface QrScanSettingsEffect : UiEffect {

    /**
     * Share the active template.
     *
     * Carries the finished text for the reason the JSON export does: a template is protocol and has
     * no locale, so there is nothing for the screen to resolve.
     */
    data class ShareSchema(val text: String) : QrScanSettingsEffect

    /** Copy a placeholder token. */
    data class CopyToken(val token: String) : QrScanSettingsEffect

    /** The imported template replaced the active one. */
    data object SchemaImported : QrScanSettingsEffect

    /** The built-in template is active again. */
    data object SchemaReset : QrScanSettingsEffect
}
