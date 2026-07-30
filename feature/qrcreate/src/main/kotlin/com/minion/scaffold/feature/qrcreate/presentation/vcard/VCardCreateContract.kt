package com.minion.scaffold.feature.qrcreate.presentation.vcard

import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.vcard.model.ContactCard
import com.minion.scaffold.core.vcard.model.VCardProperty
import com.minion.scaffold.core.vcard.model.VCardField
import com.minion.scaffold.core.vcard.model.VCardViolation
import com.minion.scaffold.core.vcard.model.VCardViolationReason

internal data class VCardCreateState(
    val form: VCardFormState = VCardFormState(),
    val violations: List<VCardViolation> = emptyList(),
    val payload: String? = null,
    val exporting: Boolean = false,
    val editing: Boolean = false,
    val prefillFailed: Boolean = false,
) : UiState {

    fun reasonFor(field: VCardField): VCardViolationReason? =
        violations.firstOrNull { it.field == field }?.reason
}

/**
 * The form's raw contents.
 *
 * [displayNameEdited] exists because `FN` is mandatory but nobody wants to type their own name
 * twice: while it is false, the display name follows the given and family fields. Once the user
 * types into it directly — to write `Dr Jane Smith PhD`, say — it stops being overwritten.
 *
 * [passthrough] is invisible but not inert. It holds the properties a scanned card carried that this
 * form has no field for, and it goes back out on generate.
 */
internal data class VCardFormState(
    val displayName: String = "",
    val displayNameEdited: Boolean = false,
    val givenName: String = "",
    val familyName: String = "",
    val organization: String = "",
    val title: String = "",
    val phone: String = "",
    val email: String = "",
    val passthrough: List<VCardProperty> = emptyList(),
)

internal fun VCardFormState.toCard(): ContactCard = ContactCard(
    formattedName = displayName.trim(),
    givenName = givenName.trim(),
    familyName = familyName.trim(),
    organization = organization.trim(),
    title = title.trim(),
    phone = phone.trim(),
    email = email.trim(),
    passthrough = passthrough,
)

/**
 * A scanned card as the form holds it.
 *
 * [VCardFormState.displayNameEdited] starts true: the card already has an `FN`, and re-deriving it
 * from the name components would rewrite someone's chosen display name the first time either was
 * touched.
 */
internal fun ContactCard.toFormState(): VCardFormState = VCardFormState(
    displayName = formattedName,
    displayNameEdited = true,
    givenName = givenName,
    familyName = familyName,
    organization = organization,
    title = title,
    phone = phone,
    email = email,
    passthrough = passthrough,
)

internal sealed interface VCardCreateIntent : UiIntent {

    /** Which text field changed. Keyed by the field so clearing its violation is automatic. */
    data class FieldChanged(val field: VCardFormField, val value: String) : VCardCreateIntent

    data object GenerateRequested : VCardCreateIntent

    data object CopyPayloadRequested : VCardCreateIntent

    data object ShareImageRequested : VCardCreateIntent

    data object SaveImageRequested : VCardCreateIntent
}

/**
 * The form's fields.
 *
 * More of them than [VCardField], which names only what can be *invalid* — given name and
 * organisation have no rules to break, so the domain has no case for them.
 */
internal enum class VCardFormField(val domainField: VCardField?) {
    DISPLAY_NAME(VCardField.DISPLAY_NAME),
    GIVEN_NAME(null),
    FAMILY_NAME(null),
    ORGANIZATION(null),
    TITLE(null),
    PHONE(VCardField.PHONE),
    EMAIL(VCardField.EMAIL),
}
