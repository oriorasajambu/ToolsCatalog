package com.minion.scaffold.feature.qrcreate.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * [stale] is what an edit after a Generate looks like. The code stays on screen — a QR that
 * silently vanishes mid-edit reads as a bug and tells the user nothing — but it is dimmed behind a
 * scrim carrying "generate again", and every export is disabled. The dimming is not decoration: an
 * out-of-date code left legible could be scanned off the screen by another phone, encoding values
 * the user is no longer looking at, which is the hazard that previously justified clearing it.
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
    stale: Boolean = false,
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

        Box(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            QrCodeImage(
                payload = payload,
                contentDescription = stringResource(R.string.qrcreate_qr_image),
                modifier = Modifier
                    .size(dimensionResource(R.dimen.qrcreate_qr_size))
                    .alpha(if (stale) STALE_QR_ALPHA else 1f),
            )

            if (stale) {
                val notice = stringResource(R.string.qrcreate_payload_stale)
                val a11y = stringResource(R.string.qrcreate_payload_stale_a11y)
                Text(
                    text = notice,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(
                            horizontal = dimensionResource(R.dimen.qrcreate_chip_padding_horizontal),
                            vertical = dimensionResource(R.dimen.qrcreate_chip_padding_vertical),
                        )
                        .semantics { contentDescription = a11y },
                )
            }
        }

        payloadContent(payload)

        // Every export is refused while the code is stale, not merely discouraged: these three are
        // the only ways a payload leaves this screen, and the one thing worse than a wrong QR on
        // screen is a wrong QR in someone's gallery.
        val exportable = !exporting && !stale
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            AppButton(
                text = stringResource(R.string.qrcreate_copy_payload),
                onClick = onCopy,
                enabled = exportable,
            )
            AppOutlinedButton(
                text = stringResource(R.string.qrcreate_share_image),
                onClick = onShare,
                enabled = exportable,
            )
        }
        AppOutlinedButton(
            text = stringResource(R.string.qrcreate_save_image),
            onClick = onSave,
            enabled = exportable,
        )
    }
}

/** How far the out-of-date code is faded. Low enough that a camera will not resolve it. */
private const val STALE_QR_ALPHA = 0.12f

/** The default payload rendering: the raw string in monospace, no structure. */
@Composable
private fun PlainPayloadText(payload: String) {
    Text(
        text = payload,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}
