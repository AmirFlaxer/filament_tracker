package com.amir.filamenttracker.presentation.ams

import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.model.SpoolStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SlotCandidatesTest {
    private fun spool(id: Long, status: SpoolStatus, material: String = "PLA", color: String = "c") = SpoolRow(
        spoolId = id, status = status, amsSlot = null, orderId = null, productId = 1, brand = "X",
        weightGrams = 1000, materialId = 1, materialName = material, materialFamily = "PLA", materialVariant = "",
        colorId = 1, colorName = color, colorHex = null,
    )

    @Test
    fun `keeps only open and sealed spools, open first, then by material and color`() {
        val result = slotCandidates(
            listOf(
                spool(1, SpoolStatus.SEALED, "PLA", "a"),
                spool(2, SpoolStatus.IN_USE),
                spool(3, SpoolStatus.OPEN_STORED, "PLA", "b"),
                spool(4, SpoolStatus.ORDERED),
                spool(5, SpoolStatus.FINISHED),
                spool(6, SpoolStatus.OPEN_STORED, "PLA", "a"),
            ),
        )
        assertEquals(listOf(6L, 3L, 1L), result.map { it.spoolId })
    }
}
