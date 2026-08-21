package com.minion.scaffold.feature.qrscan.presentation.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.designsystem.component.AppOutlinedButton
import com.minion.scaffold.feature.qrscan.R

/**
 * What a reader can do once they have finished reading a code.
 *
 * Compare sits at the end of the report rather than in the top bar. That bar already carries a back
 * arrow, edit, copy and share; a fifth glyph either crowds them or hides behind an overflow, and an
 * unlabelled compare icon is not one anybody recognises. Here it is a labelled button, in the
 * thumb's reach, exactly where the question "is this the same as the other one?" occurs.
 *
 * @param onCompare      Pins this code and goes looking for one to read it against.
 * @param primaryAction  The action specific to the format — opening a link, adding a contact —
 *   rendered above. Absent for the formats that have none.
 */
@Composable
internal fun ReportFooter(
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
    primaryAction: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            dimensionResource(R.dimen.qrscan_spacing_tight),
        ),
    ) {
        primaryAction?.invoke()

        AppOutlinedButton(
            text = stringResource(R.string.qrscan_compare),
            onClick = onCompare,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
