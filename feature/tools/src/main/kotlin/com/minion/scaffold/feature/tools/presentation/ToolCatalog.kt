package com.minion.scaffold.feature.tools.presentation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.navigation.GenerateRoute
import com.minion.scaffold.core.navigation.QrCreateRoute
import com.minion.scaffold.core.navigation.QrScanRoute
import com.minion.scaffold.core.navigation.ScanPurpose
import com.minion.scaffold.core.navigation.TextToolsRoute
import com.minion.scaffold.core.navigation.UrlCreateRoute
import com.minion.scaffold.core.navigation.VCardCreateRoute
import com.minion.scaffold.core.navigation.WifiCreateRoute
import com.minion.scaffold.feature.tools.R

/**
 * One entry in the tool catalog.
 *
 * Carries an [AppRoute] rather than a tool id, so adding a tool is a single entry here plus a
 * route in `:core:navigation` — no `when` anywhere has to learn about it.
 *
 * Titles are `@StringRes`, not `String`: this list is built once at class-init time, and a
 * `String` resolved then would not follow a locale change.
 */
internal data class Tool(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val route: AppRoute,
)

/** Every tool the app offers, in the order the home screen lists them. */
internal object ToolCatalog {

    val entries: List<Tool> = listOf(
        Tool(
            id = "qr-scan",
            titleRes = R.string.tools_qr_scan_title,
            descriptionRes = R.string.tools_qr_scan_description,
            icon = Icons.Filled.QrCodeScanner,
            route = QrScanRoute(ScanPurpose.Inspect),
        ),
        Tool(
            id = "qr-create",
            titleRes = R.string.tools_qr_create_title,
            descriptionRes = R.string.tools_qr_create_description,
            icon = Icons.Filled.QrCode2,
            route = QrCreateRoute(),
        ),
        // The same scanner as the first entry, pointed at the editor instead of the report. No
        // screen of its own — a second copy would drift the moment either was touched.
        Tool(
            id = "wifi-create",
            titleRes = R.string.tools_wifi_create_title,
            descriptionRes = R.string.tools_wifi_create_description,
            icon = Icons.Filled.Wifi,
            route = WifiCreateRoute(),
        ),
        Tool(
            id = "url-create",
            titleRes = R.string.tools_url_create_title,
            descriptionRes = R.string.tools_url_create_description,
            icon = Icons.Filled.Link,
            route = UrlCreateRoute(),
        ),
        Tool(
            id = "vcard-create",
            titleRes = R.string.tools_vcard_create_title,
            descriptionRes = R.string.tools_vcard_create_description,
            icon = Icons.Filled.ContactPage,
            route = VCardCreateRoute(),
        ),
        Tool(
            id = "text-tools",
            titleRes = R.string.tools_text_title,
            descriptionRes = R.string.tools_text_description,
            icon = Icons.Filled.TextFields,
            route = TextToolsRoute,
        ),
        Tool(
            id = "generate",
            titleRes = R.string.tools_generate_title,
            descriptionRes = R.string.tools_generate_description,
            icon = Icons.Filled.Casino,
            route = GenerateRoute,
        ),
        Tool(
            id = "qr-edit",
            titleRes = R.string.tools_qr_edit_title,
            descriptionRes = R.string.tools_qr_edit_description,
            icon = Icons.Filled.EditNote,
            route = QrScanRoute(ScanPurpose.Edit),
        ),
    )
}
