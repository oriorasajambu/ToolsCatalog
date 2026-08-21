package com.minion.scaffold.feature.qrscan.presentation

/**
 * Why a JSON export did not happen.
 *
 * Every case here means the *template* is at fault, never the scanned code — a code that simply
 * lacks a field renders as null and is not a failure at all. Kept apart from [QrScanError], which
 * is the reason a screen has nothing to show: the report is still on screen throughout, and the
 * only thing that happened is that a document could not be built from it.
 */
internal sealed interface SchemaRefusal {

    /**
     * The template names a placeholder this app cannot resolve.
     *
     * Only reachable for a template imported before an app update removed a name, since importing
     * one now would have rejected it.
     *
     * @property token The offending placeholder, as written.
     */
    data class Unknown(val token: String) : SchemaRefusal

    /** The template was stored under an older template format and needs importing again. */
    data object Outdated : SchemaRefusal

    /** The stored template will not parse. Unreachable for anything that went through validation. */
    data object Unusable : SchemaRefusal
}
