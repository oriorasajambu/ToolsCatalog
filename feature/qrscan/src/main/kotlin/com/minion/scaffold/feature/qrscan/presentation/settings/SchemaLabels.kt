package com.minion.scaffold.feature.qrscan.presentation.settings

import android.content.res.Resources
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.presentation.SchemaRefusal

/**
 * Why an export did not happen, in words.
 *
 * Takes a [Resources] rather than being `@Composable`, for the reason `EmvLabels` gives — and every
 * message says what to do next rather than only what went wrong, because in all three cases the
 * scanned code is fine and it is the template that needs attention.
 */
internal fun SchemaRefusal.describe(resources: Resources): String = when (this) {
    is SchemaRefusal.Unknown ->
        resources.getString(R.string.qrscan_schema_refused_unknown, token)

    SchemaRefusal.Outdated ->
        resources.getString(R.string.qrscan_schema_refused_outdated)

    SchemaRefusal.Unusable ->
        resources.getString(R.string.qrscan_schema_refused_unusable)
}
