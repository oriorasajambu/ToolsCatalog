package com.minion.scaffold.feature.tools.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.QrCodeImage
import com.minion.scaffold.core.designsystem.theme.AppTextStyles
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.feature.tools.R

/**
 * The tool catalog — the Midnight Pro home.
 *
 * The thin stateful half: it collects which tools are on show and forwards a selection outward.
 * There *is* a ViewModel now — the catalog stopped being a compile-time constant when the Firebase
 * console gained the ability to withhold entries from it — but everything that draws is in
 * [ToolsContent], which takes a [ToolsState] and can therefore be previewed without Hilt.
 *
 * @param onOpenTool Called with the route of the tool the user selected.
 * @param modifier   The [Modifier] for the screen.
 * @param viewModel  The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun ToolsScreen(
    onOpenTool: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    onOpenComponentCatalog: (() -> Unit)? = null,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is ToolsEffect.OpenTool -> onOpenTool(effect.route)
        }
    }

    ToolsContent(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        onOpenComponentCatalog = onOpenComponentCatalog,
    )
}

/**
 * Everything the home screen draws.
 *
 * The layout is the imported design: a hero card promoting the scanner, a bordered **Create** list,
 * and a two-column **Utilities** grid. Colours come entirely from `MaterialTheme.colorScheme`, so
 * the whole screen is the Midnight palette without a single hex literal here — a hex outside the
 * design system is the bug the design system exists to prevent.
 *
 * Every section is conditional on having something in it. A tool hidden from the Firebase console
 * is simply absent, and a **CREATE** heading standing over nothing is worse than no heading — it
 * reads as a screen that failed to load rather than one that was configured this way.
 *
 * @param state    Which tools are on show.
 * @param onIntent Reports what the user did.
 * @param modifier The [Modifier] for the screen.
 */
@Composable
private fun ToolsContent(
    state: ToolsState,
    onIntent: (ToolsIntent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenComponentCatalog: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(R.dimen.tools_gutter))
                .padding(bottom = dimensionResource(R.dimen.tools_section_gap)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tools_section_gap)),
        ) {
            HomeHeader(onBrandClick = onOpenComponentCatalog)

            state.hero?.let { hero ->
                HeroCard(
                    tool = hero,
                    onClick = { onIntent(ToolsIntent.ToolSelected(hero)) },
                )
            }

            state.secondaryReaders.forEach { tool ->
                ToolRow(tool = tool, onClick = { onIntent(ToolsIntent.ToolSelected(tool)) })
            }

            if (state.creators.isNotEmpty()) {
                Section(title = stringResource(R.string.tools_section_create)) {
                    state.creators.forEach { tool ->
                        ToolRow(tool = tool, onClick = { onIntent(ToolsIntent.ToolSelected(tool)) })
                    }
                }
            }

            if (state.utilities.isNotEmpty()) {
                Section(title = stringResource(R.string.tools_section_utilities)) {
                    UtilityGrid(
                        tools = state.utilities,
                        onSelect = { tool -> onIntent(ToolsIntent.ToolSelected(tool)) },
                    )
                }
            }
        }
    }
}

/**
 * The brand row: logo tile and app name.
 *
 * [onBrandClick] is how the component catalog is reached. `:app` supplies it only in a debug
 * build, and a null lambda leaves the tile inert — so the developer entry point is genuinely
 * absent from release rather than merely hidden, and it costs the product UI no visible control.
 *
 * @param modifier     The [Modifier] for the row.
 * @param onBrandClick Called when the logo tile is tapped, or null to make it decorative.
 */
@Composable
private fun HomeHeader(
    modifier: Modifier = Modifier,
    onBrandClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = dimensionResource(R.dimen.tools_gutter)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = Icons.Filled.QrCode2,
            size = dimensionResource(R.dimen.tools_brand_tile),
            onClick = onBrandClick,
        )
        Spacer(Modifier.width(dimensionResource(R.dimen.tools_row_gap)))
        Text(
            text = stringResource(R.string.tools_home_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The hero: a violet-glow card that promotes the scanner.
 *
 * The QR on the right is a real [QrCodeImage] of the app name — decorative, but genuinely
 * scannable, and it reuses the encoder rather than faking a grid. A scanline sweeps it to sell the
 * reader.
 */
@Composable
private fun HeroCard(
    tool: Tool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glow = Brush.radialGradient(
        colors = listOf(scheme.primary.copy(alpha = HERO_GLOW_ALPHA), Color.Transparent),
        center = Offset.Unspecified,
        radius = HERO_GLOW_RADIUS,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimensionResource(R.dimen.tools_hero_radius)))
            .border(
                width = dimensionResource(R.dimen.tools_border),
                color = scheme.outlineVariant,
                shape = RoundedCornerShape(dimensionResource(R.dimen.tools_hero_radius)),
            )
            .background(scheme.surfaceContainer)
            .background(glow)
            .clickable(onClick = onClick)
            .padding(dimensionResource(R.dimen.tools_card_padding)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tools_hero_text_gap)),
            ) {
                Text(
                    text = stringResource(R.string.tools_hero_eyebrow),
                    style = AppTextStyles.eyebrow,
                    color = scheme.primary,
                )
                Text(
                    text = stringResource(R.string.tools_hero_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.tools_hero_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(dimensionResource(R.dimen.tools_row_gap)))
            ScanlineQr(payload = stringResource(R.string.tools_home_title))
        }

        Spacer(Modifier.height(dimensionResource(R.dimen.tools_card_padding)))

        AppButton(
            text = stringResource(R.string.tools_hero_action),
            onClick = onClick,
        )
    }
}

/** A small white QR tile with a violet scanline sweeping it. */
@Composable
private fun ScanlineQr(payload: String, modifier: Modifier = Modifier) {
    val tile = dimensionResource(R.dimen.tools_hero_qr)
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SCANLINE_MILLIS), RepeatMode.Reverse),
        label = "scanline",
    )

    Box(
        modifier = modifier
            .size(tile)
            .clip(RoundedCornerShape(dimensionResource(R.dimen.tools_hero_qr_radius)))
            .background(QR_QUIET_ZONE)
            .padding(dimensionResource(R.dimen.tools_hero_qr_inset)),
    ) {
        QrCodeImage(
            payload = payload,
            contentDescription = stringResource(R.string.tools_hero_qr_description),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(x = 0, y = (tile * SCANLINE_TRAVEL * progress).roundToPx()) }
                .height(dimensionResource(R.dimen.tools_scanline_thickness))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary,
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tools_row_gap)),
    ) {
        Text(
            text = title,
            style = AppTextStyles.sectionHeading,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** A full-width bordered card: icon tile, title, one-line description, caret. */
@Composable
private fun ToolRow(
    tool: Tool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimensionResource(R.dimen.tools_card_radius)))
            .border(
                width = dimensionResource(R.dimen.tools_border),
                color = scheme.outlineVariant,
                shape = RoundedCornerShape(dimensionResource(R.dimen.tools_card_radius)),
            )
            .background(scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(dimensionResource(R.dimen.tools_row_padding)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tools_row_gap)),
    ) {
        IconTile(icon = tool.icon, size = dimensionResource(R.dimen.tools_row_tile))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(tool.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            // Every description is longer than the row is wide, so an ellipsis hid the end of all
            // of them. Marquee rather than a second line: the rows are a fixed-height rhythm, and
            // letting one grow to two lines breaks the alignment with the tile beside it.
            Text(
                text = stringResource(tool.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
        )
    }
}

/** The two-column grid of compact utility cards. */
@Composable
private fun UtilityGrid(
    tools: List<Tool>,
    onSelect: (Tool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = dimensionResource(R.dimen.tools_row_gap)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        // Two per row; a trailing odd item takes a half-width cell rather than stretching.
        tools.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                pair.forEach { tool ->
                    UtilityCard(
                        tool = tool,
                        onClick = { onSelect(tool) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UtilityCard(
    tool: Tool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(dimensionResource(R.dimen.tools_card_radius)))
            .border(
                width = dimensionResource(R.dimen.tools_border),
                color = scheme.outlineVariant,
                shape = RoundedCornerShape(dimensionResource(R.dimen.tools_card_radius)),
            )
            .background(scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(dimensionResource(R.dimen.tools_row_padding)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tools_row_gap)),
    ) {
        IconTile(icon = tool.icon, size = dimensionResource(R.dimen.tools_util_tile))
        // `weight`, so the marquee gets a bounded width to scroll within — without it the text
        // would size to its content and never register as overflowing.
        Text(
            text = stringResource(tool.titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f).basicMarquee(),
        )
    }
}

/** The rounded soft-violet square an icon sits on — the design's signature affordance. */
@Composable
private fun IconTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickLabel = stringResource(R.string.tools_brand_catalog_action)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(dimensionResource(R.dimen.tools_tile_radius)))
            .background(MaterialTheme.colorScheme.primaryContainer)
            // Clickable after the clip, so the ripple stays inside the rounded square.
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(onClickLabel = clickLabel, onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private const val HERO_GLOW_RADIUS = 520f
private const val HERO_GLOW_ALPHA = 0.35f
private const val SCANLINE_MILLIS = 2400
private const val SCANLINE_TRAVEL = 0.82f

/**
 * The quiet zone behind the hero's QR — fixed white, not a theme surface.
 *
 * The one colour on this screen that answers to the scanner rather than to the palette: a QR needs
 * a light margin and high contrast to decode, and a dark-theme surface would leave a code that
 * looks right and does not scan.
 */
private val QR_QUIET_ZONE = Color.White

@Preview
@Composable
internal fun ToolsScreenPreview() {
    AppTheme {
        ToolsContent(state = ToolsState(), onIntent = {})
    }
}

/**
 * The catalog as it looks with tools withheld from the Firebase console.
 *
 * Worth a preview of its own: this is the arrangement nobody sees while developing — no hero, one
 * section gone entirely — and it is the one a misconfigured console produces.
 */
@Preview
@Composable
internal fun ToolsScreenFilteredPreview() {
    val hidden = setOf("qr-scan", "wifi-create", "url-create", "vcard-create", "qr-create")
    AppTheme {
        ToolsContent(
            state = ToolsState(tools = ToolCatalog.entries.filterNot { it.id in hidden }),
            onIntent = {},
        )
    }
}
