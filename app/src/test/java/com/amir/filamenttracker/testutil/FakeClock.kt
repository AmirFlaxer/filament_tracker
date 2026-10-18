package com.amir.filamenttracker.testutil

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FakeClock(var now: Instant = Instant.parse("2026-10-01T10:00:00Z")) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    fun advance(duration: Duration) { now = now.plus(duration) }
}
