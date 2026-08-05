package com.minion.scaffold.feature.weather.data.local

import com.google.gson.annotations.SerializedName
import com.minion.scaffold.core.weather.model.CurrentConditions
import com.minion.scaffold.core.weather.model.DailyEntry
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.model.HourlyEntry
import com.minion.scaffold.core.weather.model.NotableCondition
import com.minion.scaffold.core.weather.model.WeatherCondition
import java.time.Instant
import java.time.LocalDate

/**
 * [Forecast], reshaped with only Gson-friendly types (epoch millis instead of [Instant], ISO
 * strings instead of [LocalDate]) so [ForecastCacheEntity.forecastJson] round-trips without a
 * custom `TypeAdapter`. Never leaves `data/local/` — everything above this package works with the
 * domain [Forecast].
 *
 * Every field carries an explicit [SerializedName], even though nothing outside this process ever
 * reads this JSON. Two reasons, both found the hard way:
 *
 * - `:core:network`'s Gson ProGuard rule (`app/proguard-rules.pro`) only keeps fields annotated
 *   `@SerializedName` — anything else is free to be renamed and, worse, to have its field
 *   `Signature` stripped. A nested generic field like `hourly: List<CachedHourlyEntry>` then loses
 *   the type Gson needs to deserialize its elements, and every cached forecast comes back as a
 *   `ClassCastException: LinkedTreeMap cannot be cast to CachedHourlyEntry` in a signed release
 *   build — R8's tree-shaking is invisible to Gson's reflection, so nothing catches this short of
 *   actually running a signed release.
 * - Independently of R8: without a pinned name, the on-disk cache's JSON keys are whatever the
 *   Kotlin property happens to be called *this build*. Renaming a property during a later refactor
 *   would silently corrupt every previously cached row on the next app update, since Gson would
 *   no longer find the old key and every field the entry has would deserialize as its default.
 */
internal data class CachedForecast(
    @SerializedName("current") val current: CachedCurrentConditions,
    @SerializedName("hourly") val hourly: List<CachedHourlyEntry>,
    @SerializedName("daily") val daily: List<CachedDailyEntry>,
    @SerializedName("notable_conditions") val notableConditions: List<CachedNotableCondition>,
    @SerializedName("fetched_at_epoch_millis") val fetchedAtEpochMillis: Long,
)

internal data class CachedCurrentConditions(
    @SerializedName("temperature") val temperature: Double,
    @SerializedName("apparent_temperature") val apparentTemperature: Double,
    @SerializedName("humidity") val humidity: Int,
    @SerializedName("wind_speed") val windSpeed: Double,
    @SerializedName("condition") val condition: WeatherCondition,
)

internal data class CachedHourlyEntry(
    @SerializedName("epoch_millis") val epochMillis: Long,
    @SerializedName("temperature") val temperature: Double,
    @SerializedName("condition") val condition: WeatherCondition,
    @SerializedName("precipitation_probability") val precipitationProbability: Int,
    @SerializedName("wind_speed") val windSpeed: Double,
)

internal data class CachedDailyEntry(
    @SerializedName("iso_date") val isoDate: String,
    @SerializedName("min_temperature") val minTemperature: Double,
    @SerializedName("max_temperature") val maxTemperature: Double,
    @SerializedName("condition") val condition: WeatherCondition,
    @SerializedName("precipitation_probability") val precipitationProbability: Int,
)

internal data class CachedNotableCondition(
    @SerializedName("kind") val kind: NotableCondition.Kind,
    @SerializedName("severity") val severity: NotableCondition.Severity,
)

internal fun Forecast.toCache(): CachedForecast = CachedForecast(
    current = CachedCurrentConditions(
        temperature = current.temperature,
        apparentTemperature = current.apparentTemperature,
        humidity = current.humidity,
        windSpeed = current.windSpeed,
        condition = current.condition,
    ),
    hourly = hourly.map {
        CachedHourlyEntry(
            epochMillis = it.time.toEpochMilli(),
            temperature = it.temperature,
            condition = it.condition,
            precipitationProbability = it.precipitationProbability,
            windSpeed = it.windSpeed,
        )
    },
    daily = daily.map {
        CachedDailyEntry(
            isoDate = it.date.toString(),
            minTemperature = it.minTemperature,
            maxTemperature = it.maxTemperature,
            condition = it.condition,
            precipitationProbability = it.precipitationProbability,
        )
    },
    notableConditions = notableConditions.map { CachedNotableCondition(it.kind, it.severity) },
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
)

internal fun CachedForecast.toDomain(): Forecast = Forecast(
    current = CurrentConditions(
        temperature = current.temperature,
        apparentTemperature = current.apparentTemperature,
        humidity = current.humidity,
        windSpeed = current.windSpeed,
        condition = current.condition,
    ),
    hourly = hourly.map {
        HourlyEntry(
            time = Instant.ofEpochMilli(it.epochMillis),
            temperature = it.temperature,
            condition = it.condition,
            precipitationProbability = it.precipitationProbability,
            windSpeed = it.windSpeed,
        )
    },
    daily = daily.map {
        DailyEntry(
            date = LocalDate.parse(it.isoDate),
            minTemperature = it.minTemperature,
            maxTemperature = it.maxTemperature,
            condition = it.condition,
            precipitationProbability = it.precipitationProbability,
        )
    },
    notableConditions = notableConditions.map { NotableCondition(it.kind, it.severity) },
    fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis),
)
