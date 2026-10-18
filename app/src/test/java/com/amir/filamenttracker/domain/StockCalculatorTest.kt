package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import com.amir.filamenttracker.domain.model.StockKey
import com.amir.filamenttracker.domain.model.StockTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockCalculatorTest {
    private val black = StockKey(materialId = 1, colorId = 10)
    private val white = StockKey(materialId = 1, colorId = 11)

    private fun alertsFor(target: StockTarget<StockKey>, vararg statuses: SpoolStatus) =
        StockCalculator.alerts(StockCalculator.count(statuses.map { target.key to it }), listOf(target))

    @Test
    fun `calculator works with a string key`() {
        val counts = StockCalculator.count(listOf("PETG" to SEALED, "PETG" to ORDERED))
        assertEquals(StockCount(available = 1, sealed = 1, onTheWay = 1), counts["PETG"])
        val alerts = StockCalculator.alerts(counts, listOf(StockTarget("PETG", 2)))
        assertEquals(listOf(AlertKind.ON_THE_WAY), alerts.map { it.kind })
        assertEquals("PETG", alerts.single().key)
    }

    @Test
    fun `count splits statuses and ignores finished`() {
        val counts = StockCalculator.count(
            listOf(black to SEALED, black to SEALED, black to IN_USE, black to OPEN_STORED, black to ORDERED, black to FINISHED),
        )
        assertEquals(StockCount(available = 4, sealed = 2, onTheWay = 1), counts[black])
    }

    @Test
    fun `untracked combination never alerts`() {
        val counts = StockCalculator.count(listOf(white to FINISHED))
        assertTrue(StockCalculator.alerts(counts, emptyList()).isEmpty())
    }

    @Test
    fun `nothing available and nothing ordered is missing`() {
        assertEquals(AlertKind.MISSING, alertsFor(StockTarget(black, 1), FINISHED).single().kind)
    }

    @Test
    fun `tracked combination with no spools at all is missing`() {
        val alerts = StockCalculator.alerts(emptyMap(), listOf(StockTarget(black, 1)))
        assertEquals(AlertKind.MISSING, alerts.single().kind)
    }

    @Test
    fun `nothing available but ordered is on the way`() {
        assertEquals(AlertKind.ON_THE_WAY, alertsFor(StockTarget(black, 1), ORDERED).single().kind)
    }

    @Test
    fun `open spool without sealed backup is running low`() {
        assertEquals(AlertKind.RUNNING_LOW, alertsFor(StockTarget(black, 1), IN_USE).single().kind)
    }

    @Test
    fun `running low but covered by an order is on the way`() {
        assertEquals(AlertKind.ON_THE_WAY, alertsFor(StockTarget(black, 1), IN_USE, ORDERED).single().kind)
    }

    @Test
    fun `min two with one sealed is running low, with two sealed is fine`() {
        assertEquals(AlertKind.RUNNING_LOW, alertsFor(StockTarget(black, 2), IN_USE, SEALED).single().kind)
        assertTrue(alertsFor(StockTarget(black, 2), SEALED, SEALED).isEmpty())
    }

    @Test
    fun `alerts are sorted missing, running low, on the way`() {
        val counts = StockCalculator.count(listOf(white to IN_USE, black to ORDERED))
        val third = StockKey(2, 12)
        val kinds = StockCalculator.alerts(
            counts,
            listOf(StockTarget(black, 1), StockTarget(white, 1), StockTarget(third, 1)),
        ).map { it.kind }
        assertEquals(listOf(AlertKind.MISSING, AlertKind.RUNNING_LOW, AlertKind.ON_THE_WAY), kinds)
    }
}
