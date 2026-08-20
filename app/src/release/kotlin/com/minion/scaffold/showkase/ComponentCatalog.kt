package com.minion.scaffold.showkase

import android.content.Context

/**
 * The release stub for the component catalog — see the debug variant of this file for why there
 * are two.
 *
 * [IS_AVAILABLE] is `false`, so the brand tile that would open the catalog is never made clickable
 * and [open] is never called. R8 removes both once the constant folds away.
 */
internal object ComponentCatalog {

    /** Whether a catalog was compiled into this variant at all. Never, in a release build. */
    const val IS_AVAILABLE: Boolean = false

    /** No catalog exists in a release build; nothing calls this. */
    fun open(context: Context) = Unit
}
