package com.amir.filamenttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ConsumptionForecastTest {
    private val d0 = Instant.parse("2026-01-01T00:00:00Z")
    private fun day(n: Long): Instant = d0.plus(Duration.ofDays(n))

    @Test
    fun `interval needs at least two finishes`() {
        assertNull(ConsumptionForecast.averageInterval(emptyList()))
        assertNull(ConsumptionForecast.averageInterval(listOf(d0)))
    }

    @Test
    fun `interval is the mean gap between consecutive finishes`() {
        assertEquals(Duration.ofDays(15), ConsumptionForecast.averageInterval(listOf(d0, day(10), day(30))))
    }

    @Test
    fun `input order does not matter`() {
        assertEquals(Duration.ofDays(15), ConsumptionForecast.averageInterval(listOf(day(30), d0, day(10))))
    }

    @Test
    fun `nothing available is out of stock even with history`() {
        assertEquals(Forecast.OutOfStock, ConsumptionForecast.forecast(listOf(d0, day(10)), available = 0, now = day(11)))
    }

    @Test
    fun `one finish is not enough history`() {
        assertEquals(Forecast.NotEnoughHistory, ConsumptionForecast.forecast(listOf(d0), available = 2, now = day(1)))
    }

    @Test
    fun `forecast is last finish plus interval times available`() {
        val result = ConsumptionForecast.forecast(listOf(d0, day(10), day(30)), available = 2, now = day(35))
        assertEquals(Forecast.At(day(60)), result)
    }

    @Test
    fun `a forecast date in the past reads as soon`() {
        val result = ConsumptionForecast.forecast(listOf(d0, day(10), day(30)), available = 2, now = day(70))
        assertEquals(Forecast.Soon, result)
    }
}
