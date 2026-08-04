package com.minion.scaffold.core.weather.mapper

import com.minion.scaffold.core.weather.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

class WmoConditionMapperTest {

    private val mapper = WmoConditionMapper()

    @Test
    fun `clear sky maps to CLEAR`() {
        assertEquals(WeatherCondition.CLEAR, mapper(0))
    }

    @Test
    fun `partly cloudy codes map to PARTLY_CLOUDY`() {
        assertEquals(WeatherCondition.PARTLY_CLOUDY, mapper(1))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, mapper(2))
    }

    @Test
    fun `overcast maps to CLOUDY`() {
        assertEquals(WeatherCondition.CLOUDY, mapper(3))
    }

    @Test
    fun `fog codes map to FOG`() {
        assertEquals(WeatherCondition.FOG, mapper(45))
        assertEquals(WeatherCondition.FOG, mapper(48))
    }

    @Test
    fun `drizzle codes map to DRIZZLE`() {
        listOf(51, 53, 55, 56, 57).forEach { code ->
            assertEquals(WeatherCondition.DRIZZLE, mapper(code))
        }
    }

    @Test
    fun `rain and rain shower codes map to RAIN`() {
        listOf(61, 63, 65, 66, 67, 80, 81, 82).forEach { code ->
            assertEquals(WeatherCondition.RAIN, mapper(code))
        }
    }

    @Test
    fun `snow codes map to SNOW`() {
        listOf(71, 73, 75, 77, 85, 86).forEach { code ->
            assertEquals(WeatherCondition.SNOW, mapper(code))
        }
    }

    @Test
    fun `thunderstorm codes map to THUNDERSTORM`() {
        listOf(95, 96, 99).forEach { code ->
            assertEquals(WeatherCondition.THUNDERSTORM, mapper(code))
        }
    }

    @Test
    fun `unrecognised code falls back to CLOUDY`() {
        assertEquals(WeatherCondition.CLOUDY, mapper(-1))
    }
}
