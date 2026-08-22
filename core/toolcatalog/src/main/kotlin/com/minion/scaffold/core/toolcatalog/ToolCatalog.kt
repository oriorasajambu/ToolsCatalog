package com.minion.scaffold.core.toolcatalog

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.navigation.ExifStripRoute
import com.minion.scaffold.core.navigation.GenerateRoute
import com.minion.scaffold.core.navigation.LevelRoute
import com.minion.scaffold.core.navigation.OcrRoute
import com.minion.scaffold.core.navigation.QrCreateRoute
import com.minion.scaffold.core.navigation.QrScanRoute
import com.minion.scaffold.core.navigation.SpeedometerRoute
import com.minion.scaffold.core.navigation.SoundMeterRoute
import com.minion.scaffold.core.navigation.ScanPurpose
import com.minion.scaffold.core.navigation.TextToolsRoute
import com.minion.scaffold.core.navigation.UrlCreateRoute
import com.minion.scaffold.core.navigation.VCardCreateRoute
import com.minion.scaffold.core.navigation.WeatherRoute
import com.minion.scaffold.core.navigation.WifiCreateRoute

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
 *
 * `@Immutable` because [route] is typed as the `AppRoute` interface, which Compose cannot infer
 * stability for. Without the annotation every `ToolDescriptor` reads as unstable and the whole
 * catalog recomposes whenever anything above it does — the promise is real: nothing here is ever
 * mutated.
 *
 * **Two icon fields, deliberately.** [icon] is what Compose draws on the tools screen;
 * [widgetIconRes] is the same glyph as a vector drawable, because Glance renders a `RemoteViews`
 * tree and cannot take an `ImageVector`. The drawables are generated from the same Material
 * source as the `Icons.Filled.*` values, and each one records that in its own comment. Nothing
 * enforces that the pair stays in step, which is the cost of the arrangement — see SPEC.md §14.
 *
 * @property id             A stable identifier for the tool.
 * @property titleRes       The string resource for the tool's title.
 * @property descriptionRes The string resource for the tool's description.
 * @property icon           The tool's icon, for Compose surfaces.
 * @property widgetIconRes  The same glyph as a drawable, for Glance surfaces.
 * @property route          The route that opens the tool.
 * @property category       Which home-screen section the tool belongs to.
 */
@Immutable
data class ToolDescriptor(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val icon: ImageVector,
    @param:DrawableRes val widgetIconRes: Int,
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
enum class ToolCategory {

    /** A tool that consumes a code — promoted to the hero card. */
    Reader,

    /** A tool that writes a code — listed in the Create section. */
    Create,

    /** A tool that does neither — shown in the Utilities grid. */
    Utility,
}

/**
 * Every tool the app offers, as it ships in the binary.
 *
 * This is the full list, not the visible one — nothing here consults the remote switches. What the
 * user is actually offered is the filtered view each surface builds: the home screen's
 * `ToolsState.tools`, and the widget's reconciled pinned list. Both filter by whatever
 * `FeatureFlagRepository` last reported.
 *
 * [ToolDescriptor.id] is doing double duty as of the remote-switch work: it is the key the
 * Firebase console hides a tool by (`feature_<id>_enabled`, hyphens turned to underscores).
 * Renaming an id is therefore a breaking change to a published configuration, not a local rename.
 */
object ToolCatalog {

    val entries: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            id = "qr-scan",
            titleRes = R.string.tools_qr_scan_title,
            descriptionRes = R.string.tools_qr_scan_description,
            icon = Icons.Filled.QrCodeScanner,
            widgetIconRes = R.drawable.ic_tool_qr_scan,
            route = QrScanRoute(ScanPurpose.Inspect),
            category = ToolCategory.Reader,
        ),
        ToolDescriptor(
            id = "qr-edit",
            titleRes = R.string.tools_qr_edit_title,
            descriptionRes = R.string.tools_qr_edit_description,
            icon = Icons.Filled.EditNote,
            widgetIconRes = R.drawable.ic_tool_qr_edit,
            route = QrScanRoute(ScanPurpose.Edit),
            category = ToolCategory.Reader,
        ),
        ToolDescriptor(
            id = "qr-create",
            titleRes = R.string.tools_qr_create_title,
            descriptionRes = R.string.tools_qr_create_description,
            icon = Icons.Filled.QrCode2,
            widgetIconRes = R.drawable.ic_tool_qr_create,
            route = QrCreateRoute(),
            category = ToolCategory.Create,
        ),
        ToolDescriptor(
            id = "wifi-create",
            titleRes = R.string.tools_wifi_create_title,
            descriptionRes = R.string.tools_wifi_create_description,
            icon = Icons.Filled.Wifi,
            widgetIconRes = R.drawable.ic_tool_wifi_create,
            route = WifiCreateRoute(),
            category = ToolCategory.Create,
        ),
        ToolDescriptor(
            id = "url-create",
            titleRes = R.string.tools_url_create_title,
            descriptionRes = R.string.tools_url_create_description,
            icon = Icons.Filled.Link,
            widgetIconRes = R.drawable.ic_tool_url_create,
            route = UrlCreateRoute(),
            category = ToolCategory.Create,
        ),
        ToolDescriptor(
            id = "vcard-create",
            titleRes = R.string.tools_vcard_create_title,
            descriptionRes = R.string.tools_vcard_create_description,
            icon = Icons.Filled.ContactPage,
            widgetIconRes = R.drawable.ic_tool_vcard_create,
            route = VCardCreateRoute(),
            category = ToolCategory.Create,
        ),
        ToolDescriptor(
            id = "text-tools",
            titleRes = R.string.tools_text_title,
            descriptionRes = R.string.tools_text_description,
            icon = Icons.Filled.TextFields,
            widgetIconRes = R.drawable.ic_tool_text_tools,
            route = TextToolsRoute(),
            category = ToolCategory.Utility,
        ),
        ToolDescriptor(
            id = "generate",
            titleRes = R.string.tools_generate_title,
            descriptionRes = R.string.tools_generate_description,
            icon = Icons.Filled.Casino,
            widgetIconRes = R.drawable.ic_tool_generate,
            route = GenerateRoute,
            category = ToolCategory.Utility,
        ),
        ToolDescriptor(
            id = "weather",
            titleRes = R.string.tools_weather_title,
            descriptionRes = R.string.tools_weather_description,
            icon = Icons.Filled.WbSunny,
            widgetIconRes = R.drawable.ic_tool_weather,
            route = WeatherRoute,
            category = ToolCategory.Utility,
        ),
        ToolDescriptor(
            id = "level",
            titleRes = R.string.tools_level_title,
            descriptionRes = R.string.tools_level_description,
            icon = Icons.Filled.Architecture,
            widgetIconRes = R.drawable.ic_tool_level,
            route = LevelRoute,
            category = ToolCategory.Utility,
        ),
        ToolDescriptor(
            id = "sound-meter",
            titleRes = R.string.tools_sound_meter_title,
            descriptionRes = R.string.tools_sound_meter_description,
            icon = Icons.Filled.GraphicEq,
            widgetIconRes = R.drawable.ic_tool_sound_meter,
            route = SoundMeterRoute,
            category = ToolCategory.Utility,
        ),
        ToolDescriptor(
            id = "speedometer",
            titleRes = R.string.tools_speedometer_title,
            descriptionRes = R.string.tools_speedometer_description,
            icon = Icons.Filled.Speed,
            widgetIconRes = R.drawable.ic_tool_speedometer,
            route = SpeedometerRoute,
            category = ToolCategory.Utility,
        ),
        ToolDescriptor(
            id = "exif-strip",
            titleRes = R.string.tools_exif_strip_title,
            descriptionRes = R.string.tools_exif_strip_description,
            icon = Icons.Filled.HideImage,
            widgetIconRes = R.drawable.ic_tool_exif_strip,
            route = ExifStripRoute,
            category = ToolCategory.Utility,
        ),
        ToolDescriptor(
            id = "ocr",
            titleRes = R.string.tools_ocr_title,
            descriptionRes = R.string.tools_ocr_description,
            icon = Icons.Filled.DocumentScanner,
            widgetIconRes = R.drawable.ic_tool_ocr,
            route = OcrRoute,
            category = ToolCategory.Utility,
        ),
    )

    /**
     * The tool promoted to the home's hero card.
     *
     * An id rather than a resolved [ToolDescriptor], because the hero is not guaranteed to be on
     * show: the catalog is filtered by the feature flags before the screen groups it, and the
     * scanner can be switched off from the console like anything else. The grouping — and the
     * decision about what a missing hero means — belongs to the home screen's own state.
     */
    const val HERO_ID: String = "qr-scan"
}
