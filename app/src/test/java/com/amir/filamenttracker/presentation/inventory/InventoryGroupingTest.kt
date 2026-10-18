package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryGroupingTest {
    private var nextId = 1L
    private fun row(material: String, materialId: Long, color: String, colorId: Long, status: SpoolStatus) = SpoolRow(
        spoolId = nextId++, status = status, amsSlot = null, orderId = null, productId = 1, brand = "X",
        weightGrams = 1000, materialId = materialId, materialName = material,
        colorId = colorId, colorName = color, colorHex = null,
    )

    @Test
    fun `groups by material then color, counts statuses, and drops finished`() {
        val sections = groupSpools(
            listOf(
                row("PLA", 1, "שחור", 10, IN_USE),
                row("PLA", 1, "שחור", 10, SEALED),
                row("PLA", 1, "שחור", 10, SEALED),
                row("PLA", 1, "לבן", 11, ORDERED),
                row("PETG", 2, "שקוף", 12, FINISHED),
            ),
        )
        assertEquals(listOf("PLA"), sections.map { it.materialName })
        val colors = sections.single().colors
        assertEquals(listOf("לבן", "שחור"), colors.map { it.colorName })
        assertEquals(mapOf(IN_USE to 1, SEALED to 2), colors.single { it.colorName == "שחור" }.counts)
    }
}
