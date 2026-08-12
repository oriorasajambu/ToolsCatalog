package com.minion.scaffold.feature.qrcreate.presentation.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.AppOutlinedButton
import com.minion.scaffold.core.designsystem.component.QrCodeImage
import com.minion.scaffold.feature.qrcreate.R
import com.minion.scaffold.core.designsystem.component.FormSection

/**
 * The generated code and what can be done with it.
 *
 * Takes a payload and three lambdas rather than a screen's state, which is what makes it work for
 * any format: a QR does not care what its bytes mean, and neither does copying, sharing nor saving
 * one.
 *
 * @param emptyHint what to say before anything has been generated. The only format-specific words
 *   here, because it has to name the fields the user still needs to fill in.
 * @param payloadContent how to render the generated payload string. Defaults to plain monospace
 *   text; the EMV screen supplies a coloured tag breakdown here, which is what keeps that
 *   format-specific view out of this otherwise format-agnostic section.
 */
@Composable
internal fun QrResultSection(
    payload: String?,
    exporting: Boolean,
    emptyHint: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    payloadContent: @Composable (payload: String) -> Unit = { PlainPayloadText(it) },
) {
    val spacing = dimensionResource(R.dimen.qrcreate_spacing)

    FormSection(
        title = stringResource(R.string.qrcreate_section_result),
        modifier = modifier,
    ) {
        if (payload == null) {
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@FormSection
        }

        QrCodeImage(
            payload = payload,
            contentDescription = stringResource(R.string.qrcreate_qr_image),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(dimensionResource(R.dimen.qrcreate_qr_size)),
        )

        payloadContent(payload)

        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            AppButton(
                text = stringResource(R.string.qrcreate_copy_payload),
                onClick = onCopy,
            )
            AppOutlinedButton(
                text = stringResource(R.string.qrcreate_share_image),
                onClick = onShare,
                enabled = !exporting,
            )
        }
        AppOutlinedButton(
            text = stringResource(R.string.qrcreate_save_image),
            onClick = onSave,
            enabled = !exporting,
        )
    }
}

/** The default payload rendering: the raw string in monospace, no structure. */
@Composable
private fun PlainPayloadText(payload: String) {
    Text(
        text = payload,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}
