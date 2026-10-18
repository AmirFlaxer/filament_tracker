package com.amir.filamenttracker.presentation.consumption

import com.amir.filamenttracker.data.db.FinishRow
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.model.SpoolStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ConsumptionRowsTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private fun daysAgo(n: Long) = now.minus(Duration.ofDays(n))
    private fun finish(colorId: Long, color: String, at: Instant) = FinishRow(1, "PLA", colorId, color, null, at)
    private fun spool(colorId: Long, status: SpoolStatus) = SpoolRow(
        spoolId = colorId * 100, status = status, amsSlot = null, orderId = null, productId = 1, brand = "X",
        weightGrams = 1000, materialId = 1, materialName = "PLA", materialFamily = "PLA", materialVariant = "", colorId = colorId, colorName = "c", colorHex = null,
    )

    @Test
    fun `counts finishes in range, uses full history for the rate, sorts by count`() {
        val rows = buildRows(
            finishes = listOf(
                finish(10, "שחור", daysAgo(400)),
                finish(10, "שחור", daysAgo(30)),
                finish(10, "שחור", daysAgo(10)),
                finish(11, "לבן", daysAgo(5)),
            ),
            spools = listOf(spool(10, SpoolStatus.SEALED)),
            range = ConsumptionRange.SIX_MONTHS,
            now = now,
        )
        assertEquals(listOf("PLA שחור", "PLA לבן"), rows.map { it.title })
        assertEquals(2, rows[0].finishedInRange)
        assertEquals("סליל כל ~27 שבועות", rows[0].rateText)
        assertEquals("אזל מהמלאי", rows[1].forecastText)
    }
}
