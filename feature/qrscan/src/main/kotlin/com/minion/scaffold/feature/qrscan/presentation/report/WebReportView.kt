package com.minion.scaffold.feature.qrscan.presentation.report

import android.content.res.Resources
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.feature.qrscan.R

/**
 * A scanned web link.
 *
 * The address is shown in full, in monospace, **above** the button that opens it. A scanned code is
 * untrusted input and a shortened or lookalike host is the whole of how QR phishing works, so the
 * one thing this screen must never do is offer to open something the user has not been shown.
 */
@Composable
internal fun WebReportView(
    url: String,
    onCopy: (String) -> Unit,
    onOpenLink: () -> Unit,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val resources = LocalResources.current

    ReportRowList(
        heading = resources.getString(R.string.qrscan_web_heading),
        rows = webLinkRows(url, resources),
        onCopy = onCopy,
        modifier = modifier,
        contentPadding = contentPadding,
        footer = {
            ReportFooter(onCompare = onCompare) {
                AppButton(
                    text = stringResource(R.string.qrscan_web_open),
                    onClick = onOpenLink,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/**
 * Named rather than an extension on `String`, because `internal` puts it in reach of the whole
 * module and `String.rows()` is far too broad a claim on a type this feature does not own.
 */
internal fun webLinkRows(url: String, resources: Resources): List<ReportRow> =
    listOf(ReportRow(resources.getString(R.string.qrscan_web_url), url))

internal fun webLinkPlainText(url: String, resources: Resources): String =
    webLinkRows(url, resources).toPlainText(resources.getString(R.string.qrscan_web_heading))
