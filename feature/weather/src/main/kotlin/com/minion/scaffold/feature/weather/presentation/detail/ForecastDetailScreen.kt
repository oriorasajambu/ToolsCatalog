package com.minion.scaffold.feature.weather.presentation.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.ui.error.toMessageRes
import com.minion.scaffold.core.weather.model.CurrentConditions
import com.minion.scaffold.core.weather.model.DailyEntry
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.model.HourlyEntry
import com.minion.scaffold.core.weather.model.NotableCondition
import com.minion.scaffold.core.weather.model.WeatherCondition
import com.minion.scaffold.core.weather.model.WeatherUnit
import com.minion.scaffold.feature.weather.R
import com.minion.scaffold.feature.weather.presentation.humidityWindFormatRes
import com.minion.scaffold.feature.weather.presentation.stalenessLabel
import com.minion.scaffold.feature.weather.presentation.temperatureFormatRes
import com.minion.scaffold.feature.weather.presentation.toIcon
import com.minion.scaffold.feature.weather.presentation.toLabelRes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
internal fun ForecastDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForecastDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ForecastDetailContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForecastDetailContent(
    state: ForecastDetailState,
    onIntent: (ForecastDetailIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.weather_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.weather_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.weather_navigate_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { onIntent(ForecastDetailIntent.PullToRefresh) },
            modifier = Modifier.fillMaxSize().padding(contentPadding),
        ) {
            when (val content = state.content) {
                ForecastDetailState.ContentState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                is ForecastDetailState.ContentState.Success -> ForecastList(
                    forecast = content.forecast,
                    staleHoursAgo = content.staleHoursAgo,
                    unit = state.unit,
                    spacing = spacing,
                )

                is ForecastDetailState.ContentState.Failure -> Column(
                    modifier = Modifier.fillMaxWidth().padding(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    Text(
                        text = stringResource(content.error.toMessageRes()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppButton(
                        text = stringResource(R.string.weather_retry),
                        onClick = { onIntent(ForecastDetailIntent.Retry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastList(forecast: Forecast, staleHoursAgo: Long?, unit: WeatherUnit, spacing: Dp) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        item(key = "current") {
            CurrentConditionsBlock(
                current = forecast.current,
                staleHoursAgo = staleHoursAgo,
                unit = unit,
                spacing = spacing,
            )
        }

        if (forecast.notableConditions.isNotEmpty()) {
            item(key = "notable") {
                NotableConditionsBanner(conditions = forecast.notableConditions, spacing = spacing)
            }
        }

        val hourlyWindow = forecast.hourly.next24Hours()
        val rainWindows = hourlyWindow.rainWindows()
        if (rainWindows.isNotEmpty()) {
            item(key = "rain-windows") {
                RainWindowsBanner(windows = rainWindows, spacing = spacing)
            }
        }

        item(key = "hourly") {
            HourlyStrip(hourly = hourlyWindow, spacing = spacing)
        }

        items(forecast.daily, key = { it.date.toString() }) { day ->
            DailyRow(day = day)
        }
    }
}

@Composable
private fun CurrentConditionsBlock(
    current: CurrentConditions,
    staleHoursAgo: Long?,
    unit: WeatherUnit,
    spacing: Dp,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing / 4),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = current.condition.toIcon(),
                    contentDescription = stringResource(current.condition.toLabelRes()),
                )
                Text(
                    text = stringResource(unit.temperatureFormatRes(), current.temperature.roundToInt()),
                    style = MaterialTheme.typography.displaySmall,
                )
            }
            Text(
                text = stringResource(R.string.weather_feels_like, current.apparentTemperature.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    unit.humidityWindFormatRes(),
                    current.humidity,
                    current.windSpeed.roundToInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            staleHoursAgo?.let { hours ->
                Text(
                    text = stalenessLabel(hours),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Worded as app-computed observations, never "ALERT"/"WARNING" styling — SPEC.md §7 is explicit
 * that this must not read as an official source.
 */
@Composable
private fun NotableConditionsBanner(conditions: List<NotableCondition>, spacing: Dp) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing / 4),
        ) {
            conditions.forEach { condition ->
                Text(
                    text = stringResource(condition.kind.toLabelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun NotableCondition.Kind.toLabelRes(): Int = when (this) {
    NotableCondition.Kind.HEAVY_RAIN -> R.string.weather_notable_heavy_rain
    NotableCondition.Kind.HIGH_WIND -> R.string.weather_notable_high_wind
    NotableCondition.Kind.EXTREME_HEAT -> R.string.weather_notable_extreme_heat
    NotableCondition.Kind.EXTREME_COLD -> R.string.weather_notable_extreme_cold
    NotableCondition.Kind.SNOW -> R.string.weather_notable_snow
}

/** One contiguous stretch of rain/drizzle within the hourly window, e.g. "Rain, 13:00–15:00". */
private data class RainWindow(
    @param:StringRes val conditionLabelRes: Int,
    val startLabel: String,
    val endLabel: String,
)

/**
 * Groups [this] into contiguous rain/drizzle runs — not every rainy hour individually, which would
 * be one banner line per hour, and not a single min/max span, which would lie about a dry gap in
 * the middle of the day reading as one long rain window.
 *
 * [HourlyEntry]s are consecutive hours already (the strip they're computed from is one hour apart,
 * end to end), so "the next entry's hour is exactly the previous one's hour + 1" is what "still the
 * same run" means — a gap of any size (a dry hour in between) starts a new run.
 */
private fun List<HourlyEntry>.rainWindows(zoneId: ZoneId = ZoneId.systemDefault()): List<RainWindow> {
    val runs = mutableListOf<MutableList<HourlyEntry>>()
    for (entry in this) {
        if (entry.condition != WeatherCondition.RAIN && entry.condition != WeatherCondition.DRIZZLE) continue

        val currentRun = runs.lastOrNull()
        if (currentRun != null && currentRun.last().time.plus(1, ChronoUnit.HOURS) == entry.time) {
            currentRun.add(entry)
        } else {
            runs.add(mutableListOf(entry))
        }
    }

    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return runs.map { run ->
        RainWindow(
            conditionLabelRes = if (run.any { it.condition == WeatherCondition.RAIN }) {
                R.string.weather_condition_rain
            } else {
                R.string.weather_condition_drizzle
            },
            startLabel = formatter.format(ZonedDateTime.ofInstant(run.first().time, zoneId)),
            // The end of the window is the boundary after its last rainy hour, not that hour's own
            // start — "rain 13:00-15:00" means it clears at 15:00, not that 15:00 is still rainy.
            endLabel = formatter.format(ZonedDateTime.ofInstant(run.last().time.plus(1, ChronoUnit.HOURS), zoneId)),
        )
    }
}

/**
 * A distinct banner from [NotableConditionsBanner] — this is a plain schedule ("it rains from X to
 * Y today"), not an app-computed severity judgement, so it gets its own visual language
 * (`tertiaryContainer`) rather than sharing the notable-conditions banner's color or copy.
 */
@Composable
private fun RainWindowsBanner(windows: List<RainWindow>, spacing: Dp) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing / 4),
        ) {
            windows.forEach { window ->
                Text(
                    text = stringResource(
                        R.string.weather_rain_window,
                        stringResource(window.conditionLabelRes),
                        window.startLabel,
                        window.endLabel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * A rolling 24-hour window starting at the current hour — not the whole multi-day array Open-Meteo
 * returns (up to 10 days' worth, since [ForecastFields.FORECAST_DAYS] fetches that far ahead for
 * the daily list below; a strip that scrolled through several days of hours read as "too much" and
 * duplicated what the daily list already shows), and not clipped to the current calendar day
 * either — the strip is meant to always show a full day ahead, so late at night it deliberately
 * spills into tomorrow's early hours rather than shrinking down to two or three cells before
 * midnight.
 *
 * Filtering and truncating on the raw UTC [Instant] is enough here — unlike the calendar-day
 * version this replaced, "the next 24 hours" needs no local-timezone calendar-boundary math at
 * all, since it's just "starting now, take 24" over an already-chronological list.
 */
private fun List<HourlyEntry>.next24Hours(): List<HourlyEntry> {
    val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
    return filter { !it.time.isBefore(currentHour) }.take(HOURLY_WINDOW_SIZE)
}

private const val HOURLY_WINDOW_SIZE = 24

/**
 * [hourly] is expected to already be [List<HourlyEntry>.next24Hours], which starts at the current
 * hour — so index 0 is "now" and index 1 is the next hour, with no further clock reads needed here.
 */
@Composable
private fun HourlyStrip(hourly: List<HourlyEntry>, spacing: Dp) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing / 2)) {
        itemsIndexed(hourly, key = { _, hour -> hour.time.toEpochMilli() }) { index, hour ->
            HourlyCell(hour = hour, emphasis = HourEmphasis.fromIndex(index))
        }
    }
}

private val HOURLY_CELL_WIDTH = 84.dp

/** How strongly one [HourlyCell] stands out from the rest of the strip. */
private enum class HourEmphasis {
    CURRENT,
    NEXT,
    NONE,
    ;

    companion object {
        fun fromIndex(index: Int): HourEmphasis = when (index) {
            0 -> CURRENT
            1 -> NEXT
            else -> NONE
        }
    }
}

@Composable
private fun HourlyCell(hour: HourlyEntry, emphasis: HourEmphasis) {
    val hourLabel = DateTimeFormatter.ofPattern("HH:mm")
        .format(ZonedDateTime.ofInstant(hour.time, ZoneId.systemDefault()))

    val containerColor = when (emphasis) {
        HourEmphasis.CURRENT -> MaterialTheme.colorScheme.primaryContainer
        HourEmphasis.NEXT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = SOFT_HIGHLIGHT_ALPHA)
        HourEmphasis.NONE -> CardDefaults.cardColors().containerColor
    }
    val contentColor = when (emphasis) {
        HourEmphasis.CURRENT -> MaterialTheme.colorScheme.onPrimaryContainer
        HourEmphasis.NEXT -> MaterialTheme.colorScheme.onPrimaryContainer
        HourEmphasis.NONE -> CardDefaults.cardColors().contentColor
    }

    Card(
        modifier = Modifier.width(HOURLY_CELL_WIDTH),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (emphasis == HourEmphasis.CURRENT) stringResource(R.string.weather_now) else hourLabel,
                style = MaterialTheme.typography.labelSmall,
            )
            Icon(
                imageVector = hour.condition.toIcon(),
                contentDescription = stringResource(hour.condition.toLabelRes()),
            )
            // Named, not just iconified — an icon alone is ambiguous (e.g. the drizzle and rain
            // glyphs read as near-identical at this size). Single line so a long name doesn't
            // widen its cell relative to the others in the row; marquee rather than an ellipsis
            // because "Partly clou…" and "Partly clou…" would be indistinguishable if two similar
            // conditions ever truncated to the same prefix.
            Text(
                text = stringResource(hour.condition.toLabelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = if (emphasis == HourEmphasis.NONE) MaterialTheme.colorScheme.onSurfaceVariant else contentColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.basicMarquee(),
            )
            Text(text = "${hour.temperature.roundToInt()}°", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private const val SOFT_HIGHLIGHT_ALPHA = 0.4f

@Composable
private fun DailyRow(day: DailyEntry) {
    val locale = LocalLocale.current.platformLocale
    val isToday = day.date == LocalDate.now()
    val dayOfWeekLabel = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val dateLabel = if (isToday) {
        stringResource(R.string.weather_today)
    } else {
        DateTimeFormatter.ofPattern("d/MMM", locale).format(day.date)
    }

    // A solid fill, not a low-alpha tint — the same treatment HourlyCell gives the current hour —
    // because a tint this close to the row's own dark background barely registered (the earlier
    // 40%-alpha version was reported as "not visible enough").
    val containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified
    val mutedContentColor = if (isToday) {
        contentColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = containerColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$dayOfWeekLabel - $dateLabel",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.SemiBold else null,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = day.condition.toIcon(),
                contentDescription = null,
                tint = if (isToday) contentColor else LocalContentColor.current,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(day.condition.toLabelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = mutedContentColor,
            )
        }
        Text(
            text = stringResource(
                R.string.weather_daily_min_max,
                day.minTemperature.roundToInt(),
                day.maxTemperature.roundToInt(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Preview
@Composable
internal fun ForecastDetailSuccessPreview() {
    AppTheme {
        ForecastDetailContent(
            state = ForecastDetailState(
                content = ForecastDetailState.ContentState.Success(
                    forecast = Forecast(
                        current = CurrentConditions(
                            temperature = 28.0,
                            apparentTemperature = 30.0,
                            humidity = 70,
                            windSpeed = 12.0,
                            condition = WeatherCondition.RAIN,
                        ),
                        hourly = (0..5).map {
                            HourlyEntry(Instant.now().plusSeconds(it * 3600L), 27.0 + it, WeatherCondition.RAIN, 80, 10.0)
                        },
                        daily = listOf(
                            DailyEntry(LocalDate.now(), 24.0, 30.0, WeatherCondition.RAIN, 80),
                            DailyEntry(LocalDate.now().plusDays(1), 23.0, 33.0, WeatherCondition.THUNDERSTORM, 60),
                        ),
                        notableConditions = listOf(
                            NotableCondition(NotableCondition.Kind.HEAVY_RAIN, NotableCondition.Severity.MODERATE),
                        ),
                        fetchedAt = Instant.now(),
                    ),
                    staleHoursAgo = null,
                ),
            ),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}
