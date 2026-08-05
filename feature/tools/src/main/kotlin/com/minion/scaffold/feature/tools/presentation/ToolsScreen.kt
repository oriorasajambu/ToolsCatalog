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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.QrCodeImage
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.feature.tools.R

/**
 * The tool catalog — the Midnight Pro home.
 *
 * Stateless by construction: it renders [ToolCatalog] and reports clicks upward. There is no
 * ViewModel because there is no state — the list is a compile-time constant, nothing loads, and
 * nothing can fail.
 *
 * The layout is the imported design: a hero card promoting the scanner, a bordered **Create** list,
 * and a two-column **Utilities** grid. Colours come entirely from `MaterialTheme.colorScheme`, so
 * the whole screen is the Midnight palette without a single hex literal here — a hex outside the
 * design system is the bug the design system exists to prevent.
 */
@Composable
internal fun ToolsScreen(
    onOpenTool: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
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
            HomeHeader()

            HeroCard(
                tool = ToolCatalog.hero,
                onClick = { onOpenTool(ToolCatalog.hero.route) },
            )

            ToolCatalog.secondaryReaders.forEach { tool ->
                ToolRow(tool = tool, onClick = { onOpenTool(tool.route) })
            }

            Section(title = stringResource(R.string.tools_section_create)) {
                ToolCatalog.creators.forEach { tool ->
                    ToolRow(tool = tool, onClick = { onOpenTool(tool.route) })
                }
            }

            Section(title = stringResource(R.string.tools_section_utilities)) {
                UtilityGrid(
                    tools = ToolCatalog.utilities,
                    onOpenTool = onOpenTool,
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = dimensionResource(R.dimen.tools_gutter)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = Icons.Filled.QrCode2,
            size = dimensionResource(R.dimen.tools_brand_tile),
        )
        Spacer(Modifier.width(dimensionResource(R.dimen.tools_row_gap)))
        Text(
            text = stringResource(R.string.tools_home_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        // Decorative brand anchor, not a control — there is no profile screen behind it.
        Box(
            modifier = Modifier
                .size(dimensionResource(R.dimen.tools_avatar))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        colors = listOf(scheme.primary.copy(alpha = 0.35f), Color.Transparent),
        center = Offset.Unspecified,
        radius = HERO_GLOW_RADIUS,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimensionResource(R.dimen.tools_hero_radius)))
            .border(
                width = 1.dp,
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.tools_hero_eyebrow),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
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
            .background(Color.White)
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
                .height(2.dp)
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
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
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
                width = 1.dp,
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
    onOpenTool: (AppRoute) -> Unit,
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
                        onClick = { onOpenTool(tool.route) },
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
                width = 1.dp,
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
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(dimensionResource(R.dimen.tools_tile_radius)))
            .background(MaterialTheme.colorScheme.primaryContainer),
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
private const val SCANLINE_MILLIS = 2400
private const val SCANLINE_TRAVEL = 0.82f

@Preview
@Composable
internal fun ToolsScreenPreview() {
    AppTheme {
        ToolsScreen(onOpenTool = {})
    }
}
