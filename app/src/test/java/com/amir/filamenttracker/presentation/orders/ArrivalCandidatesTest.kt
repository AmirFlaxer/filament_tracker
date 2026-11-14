package com.amir.filamenttracker.presentation.orders

import com.amir.filamenttracker.data.db.OrderSummary
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.model.SpoolStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ArrivalCandidatesTest {
    private fun order(id: Long, at: Long, archived: Boolean = false) =
        OrderSummary(id, "Amazon", Instant.ofEpochSecond(at), null, "N$id", 1, 1, archived)

    private fun spool(id: Long, orderId: Long?, productId: Long, status: SpoolStatus = SpoolStatus.ORDERED) = SpoolRow(
        spoolId = id, status = status, amsSlot = null, orderId = orderId, productId = productId, brand = "XZN",
        weightGrams = 1000, materialId = 1, materialName = "PLA", materialFamily = "PLA", materialVariant = "",
        colorId = 1, colorName = "Gold", colorHex = null,
    )

    @Test
    fun `only ordered spools of active orders`() {
        val orders = listOf(order(1, 100), order(2, 200, archived = true))
        val spools = listOf(
            spool(10, 1, 1),
            spool(11, 1, 2, SpoolStatus.SEALED),
            spool(12, 2, 3),
            spool(13, null, 4),
        )
        assertEquals(listOf(10L), arrivalCandidates(spools, orders).map { it.spoolId })
    }

    @Test
    fun `one spool per product from the oldest order`() {
        val orders = listOf(order(1, 300), order(2, 100))
        val spools = listOf(spool(10, 1, 7), spool(11, 2, 7), spool(12, 1, 8))
        assertEquals(listOf(11L, 12L), arrivalCandidates(spools, orders).map { it.spoolId })
    }
}
