package com.minion.scaffold.feature.tools.presentation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.navigation.GenerateRoute
import com.minion.scaffold.core.navigation.OcrRoute
import com.minion.scaffold.core.navigation.QrCreateRoute
import com.minion.scaffold.core.navigation.QrScanRoute
import com.minion.scaffold.core.navigation.ScanPurpose
import com.minion.scaffold.core.navigation.TextToolsRoute
import com.minion.scaffold.core.navigation.UrlCreateRoute
import com.minion.scaffold.core.navigation.VCardCreateRoute
import com.minion.scaffold.core.navigation.WeatherRoute
import com.minion.scaffold.core.navigation.WifiCreateRoute
import com.minion.scaffold.feature.tools.R

/**
 * One entry in the tool catalog.
 *
 * Carries an [AppRoute] rather than a tool id, so adding a tool is a single entry here plus a route
 * in `:core:navigation` — no `when` anywhere has to learn about it. [category] is what lets the home
 * screen group the tools into its hero, its Creation list and its Utilities grid without hard-coding
 * ids.
 *
 * Titles are `@StringRes`, not `String`: this list is built once at class-init time, and a `String`
 * resolved then would not follow a locale change.
 */
internal data class Tool(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val route: AppRoute,
    val category: ToolCategory,
)

/**
 * The section a tool belongs to on the home screen.
 *
 * [Reader] tools consume a code; [Create] tools write one; [Utility] tools do neither. The home
 * promotes the primary reader to a hero card, lists the creators, and grids the utilities — the
 * Midnight Pro catalog layout.
 */
internal enum class ToolCategory { Reader, Create, Utility }

/** Every tool the app offers. */
internal object ToolCatalog {

    val entries: List<Tool> = listOf(
        Tool(
            id = "qr-scan",
            titleRes = R.string.tools_qr_scan_title,
            descriptionRes = R.string.tools_qr_scan_description,
            icon = Icons.Filled.QrCodeScanner,
            route = QrScanRoute(ScanPurpose.Inspect),
            category = ToolCategory.Reader,
        ),
        Tool(
            id = "qr-edit",
            titleRes = R.string.tools_qr_edit_title,
            descriptionRes = R.string.tools_qr_edit_description,
            icon = Icons.Filled.EditNote,
            route = QrScanRoute(ScanPurpose.Edit),
            category = ToolCategory.Reader,
        ),
        Tool(
            id = "qr-create",
            titleRes = R.string.tools_qr_create_title,
            descriptionRes = R.string.tools_qr_create_description,
            icon = Icons.Filled.QrCode2,
            route = QrCreateRoute(),
            category = ToolCategory.Create,
        ),
        Tool(
            id = "wifi-create",
            titleRes = R.string.tools_wifi_create_title,
            descriptionRes = R.string.tools_wifi_create_description,
            icon = Icons.Filled.Wifi,
            route = WifiCreateRoute(),
            category = ToolCategory.Create,
        ),
        Tool(
            id = "url-create",
            titleRes = R.string.tools_url_create_title,
            descriptionRes = R.string.tools_url_create_description,
            icon = Icons.Filled.Link,
            route = UrlCreateRoute(),
            category = ToolCategory.Create,
        ),
        Tool(
            id = "vcard-create",
            titleRes = R.string.tools_vcard_create_title,
            descriptionRes = R.string.tools_vcard_create_description,
            icon = Icons.Filled.ContactPage,
            route = VCardCreateRoute(),
            category = ToolCategory.Create,
        ),
        Tool(
            id = "text-tools",
            titleRes = R.string.tools_text_title,
            descriptionRes = R.string.tools_text_description,
            icon = Icons.Filled.TextFields,
            route = TextToolsRoute(),
            category = ToolCategory.Utility,
        ),
        Tool(
            id = "generate",
            titleRes = R.string.tools_generate_title,
            descriptionRes = R.string.tools_generate_description,
            icon = Icons.Filled.Casino,
            route = GenerateRoute,
            category = ToolCategory.Utility,
        ),
        Tool(
            id = "weather",
            titleRes = R.string.tools_weather_title,
            descriptionRes = R.string.tools_weather_description,
            icon = Icons.Filled.WbSunny,
            route = WeatherRoute,
            category = ToolCategory.Utility,
        ),
        Tool(
            id = "ocr",
            titleRes = R.string.tools_ocr_title,
            descriptionRes = R.string.tools_ocr_description,
            icon = Icons.Filled.DocumentScanner,
            route = OcrRoute,
            category = ToolCategory.Utility,
        ),
    )

    /** The scan tool, promoted to the home's hero card. */
    val hero: Tool = entries.first { it.id == HERO_ID }

    /** Reader tools other than the hero — the edit entry, shown as a slim card beneath it. */
    val secondaryReaders: List<Tool> =
        entries.filter { it.category == ToolCategory.Reader && it.id != HERO_ID }

    val creators: List<Tool> = entries.filter { it.category == ToolCategory.Create }

    val utilities: List<Tool> = entries.filter { it.category == ToolCategory.Utility }

    private const val HERO_ID = "qr-scan"
}
