package com.amir.filamenttracker.domain

import java.time.Duration
import java.time.Instant

sealed interface Forecast {
    data object NotEnoughHistory : Forecast
    data object OutOfStock : Forecast
    data object Soon : Forecast
    data class At(val date: Instant) : Forecast
}

/** Spec section 5, "rate and forecast". Informational only, never feeds alerts in v1. */
object ConsumptionForecast {
    fun averageInterval(finishes: List<Instant>): Duration? {
        if (finishes.size < 2) return null
        val sorted = finishes.sorted()
        return Duration.between(sorted.first(), sorted.last()).dividedBy((sorted.size - 1).toLong())
    }

    fun forecast(finishes: List<Instant>, available: Int, now: Instant): Forecast {
        if (available == 0) return Forecast.OutOfStock
        val interval = averageInterval(finishes) ?: return Forecast.NotEnoughHistory
        val at = finishes.max().plus(interval.multipliedBy(available.toLong()))
        return if (at.isAfter(now)) Forecast.At(at) else Forecast.Soon
    }
}
