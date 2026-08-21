package com.minion.scaffold.feature.qrscan.presentation.report

import android.content.res.Resources
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.vcard.model.ContactCard
import com.minion.scaffold.core.vcard.model.VCardProperty
import com.minion.scaffold.feature.qrscan.R

/**
 * A scanned contact card.
 *
 * **Add to contacts** hands the details to the system contacts app pre-filled rather than writing
 * anything — which is why this needs no permission, and why the user gets a last look before it
 * lands in their address book.
 */
@Composable
internal fun ContactReportView(
    card: ContactCard,
    onCopy: (String) -> Unit,
    onAddContact: () -> Unit,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val resources = LocalResources.current

    ReportRowList(
        heading = resources.getString(R.string.qrscan_contact_heading),
        rows = card.rows(resources),
        onCopy = onCopy,
        modifier = modifier,
        contentPadding = contentPadding,
        footer = {
            ReportFooter(onCompare = onCompare) {
                AppButton(
                    text = stringResource(R.string.qrscan_contact_add),
                    onClick = onAddContact,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/**
 * Only the fields the card actually carries, then everything else it carries too.
 *
 * A row reading "Organisation: (blank)" tells the reader nothing except that this app has a slot for
 * one — and a card scanned from a business card usually leaves several of these empty.
 */
internal fun ContactCard.rows(resources: Resources): List<ReportRow> = buildList {
    add(
        ReportRow(
            label = resources.getString(R.string.qrscan_contact_name),
            value = formattedName,
            monospace = false,
        ),
    )
    addOptional(resources.getString(R.string.qrscan_contact_organization), organization, false)
    addOptional(resources.getString(R.string.qrscan_contact_title), title, false)
    addOptional(resources.getString(R.string.qrscan_contact_phone), phone, true)
    addOptional(resources.getString(R.string.qrscan_contact_email), email, true)

    // Shown rather than counted. These are details the card genuinely holds — an address, a
    // birthday, a second phone number — and a reader who can see the code has every reason to
    // expect to see them.
    addAll(passthrough.map { it.toRow(resources) })
}

/**
 * A carried-through property as a readable row.
 *
 * Named where the property is one people recognise, and by its raw vCard name where it is not: a row
 * labelled `X-ANDROID-CUSTOM` is jargon, but inventing a friendly name for a property this app does
 * not understand would be worse — it would claim to know what the value means.
 *
 * Structured values are joined with commas and their empty components dropped, which is what turns
 * `;;1 Long Road;Bekasi;;17151;ID` into a postal address.
 */
private fun VCardProperty.toRow(resources: Resources): ReportRow {
    val readable = components.filter(String::isNotBlank).joinToString(separator = ", ")

    return ReportRow(
        label = labelRes()?.let(resources::getString) ?: name,
        // Truncated for display only — copying still yields the whole thing. A `PHOTO` carries
        // kilobytes of base64, and a row that long buries every other detail on the screen.
        value = readable.take(MAX_DISPLAY_LENGTH) +
            if (readable.length > MAX_DISPLAY_LENGTH) "…" else "",
        monospace = false,
    )
}

/** The properties common enough to be worth naming. Anything else keeps its vCard name. */
private fun VCardProperty.labelRes(): Int? = when (name) {
    "ADR" -> R.string.qrscan_contact_address
    "BDAY" -> R.string.qrscan_contact_birthday
    "NOTE" -> R.string.qrscan_contact_note
    "URL" -> R.string.qrscan_contact_website
    "NICKNAME" -> R.string.qrscan_contact_nickname
    "ROLE" -> R.string.qrscan_contact_role
    "TEL" -> R.string.qrscan_contact_phone
    "EMAIL" -> R.string.qrscan_contact_email
    "PHOTO", "LOGO" -> R.string.qrscan_contact_image
    else -> null
}

private fun MutableList<ReportRow>.addOptional(
    label: String,
    value: String,
    monospace: Boolean,
) {
    if (value.isNotBlank()) {
        add(ReportRow(label = label, value = value, monospace = monospace))
    }
}

private const val MAX_DISPLAY_LENGTH = 120

internal fun ContactCard.toPlainText(resources: Resources): String =
    rows(resources).toPlainText(resources.getString(R.string.qrscan_contact_heading))
