package com.minion.scaffold.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.minion.scaffold.core.designsystem.R

/**
 * The generic form atoms every authoring screen is built from — a titled section, a validated text
 * field, a masked field, and a filterable picker.
 *
 * They live in the design system rather than any feature because they render no domain model: a
 * `FormField` knows a `String` and an `errorMessage: String?`, nothing about currencies or contacts.
 * Two features needed them, which is the point at which a shared atom moves here — the same
 * threshold the core logic modules were extracted at.
 */

/** A titled card grouping related fields. Every authoring screen is a stack of these. */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = dimensionResource(R.dimen.ds_form_spacing)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.ds_form_spacing_tight),
            ),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/**
 * A text field that shows its own validation failure underneath it.
 *
 * `supportingText` rather than a separate error row, so the message is attached to the field by the
 * component's own semantics — a screen reader announces the two together. The message is a finished
 * `String`, not a typed reason, so each feature maps its own violations before the widget sees them.
 */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        enabled = enabled,
        isError = errorMessage != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = errorMessage?.let { { Text(text = it) } },
    )
}

/**
 * A [FormField] that hides what is typed, with a control to show it.
 *
 * Masked by default because a password is usually entered somewhere other people can see the
 * screen, and revealable because it is also often copied off a label and worth checking before it
 * is committed.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        isError = errorMessage != null,
        singleLine = true,
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        supportingText = errorMessage?.let { { Text(text = it) } },
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (revealed) R.string.ds_password_hide else R.string.ds_password_show,
                    ),
                )
            }
        },
    )
}

/**
 * A dropdown backed by a list, with a filter for lists too long to scan.
 *
 * Generic over the option type: the currency picker, the merchant-category picker and the
 * text-operation picker differ only in what they render, and all three feed it a plain list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PickerField(
    label: String,
    selectedLabel: String?,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // Only filtered once the menu is open. Building this eagerly would run a full pass over the
    // option list — up to a couple hundred merchant categories — on the screen's very first frame,
    // for a menu the user has not opened, which is exactly the kind of first-frame work that makes
    // a heavy authoring screen miss its navigation enter animation.
    val visible = remember(expanded, options, query) {
        if (!expanded) {
            emptyList()
        } else {
            options
                .filter { query.isBlank() || optionLabel(it).contains(query, ignoreCase = true) }
                .take(MAX_VISIBLE_OPTIONS)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            // Reset the filter on every open, so returning to the picker does not show the previous
            // search still narrowing the list for reasons the user has forgotten.
            if (it) query = ""
            expanded = it
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = if (expanded) query else selectedLabel.orEmpty(),
            onValueChange = { query = it },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
            label = { Text(text = label) },
            placeholder = {
                Text(
                    text = stringResource(
                        if (expanded) R.string.ds_picker_search else R.string.ds_picker_unselected,
                    ),
                )
            },
            readOnly = !expanded,
            singleLine = true,
            isError = errorMessage != null,
            supportingText = errorMessage?.let { { Text(text = it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = dimensionResource(R.dimen.ds_picker_max_height)),
        ) {
            for (option in visible) {
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * The menu is a plain column, not a lazy list, so every option it holds is composed at once.
 * Capping it keeps opening a long picker cheap; the filter is how the rest are reached.
 */
private const val MAX_VISIBLE_OPTIONS = 40
