package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.AlertRow
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.amir.filamenttracker.domain.materialKind

class InventoryGroupingTest {
    private var nextId = 1L
    private fun row(
        material: String, materialId: Long, color: String, colorId: Long, status: SpoolStatus, slot: Int? = null,
    ) = SpoolRow(
        spoolId = nextId++, status = status, amsSlot = slot, orderId = null, productId = 1, brand = "X",
        weightGrams = 1000, materialId = materialId, materialName = material,
        materialFamily = materialKind(material).family, materialVariant = materialKind(material).variant.orEmpty(),
        colorId = colorId, colorName = color, colorHex = null,
    )

    private fun missing(family: String, color: String, colorId: Long) = AlertRow(
        kind = AlertKind.MISSING, family = family,
        colorId = colorId, colorName = color, colorHex = null,
        count = com.amir.filamenttracker.domain.StockCount(0, 0, 0), minSealed = 1,
    )

    private val sample = listOf(
        row("PLA", 1, "לבן", 10, IN_USE, slot = 2),
        row("PLA", 1, "לבן", 10, SEALED),
        row("PLA Matte", 3, "לבן", 10, OPEN_STORED),
        row("PETG", 2, "לבן", 10, ORDERED),
        row("PETG", 2, "שחור", 11, SEALED),
        row("PETG", 2, "שחור", 11, FINISHED),
    )

    @Test
    fun `by material groups variants under their family`() {
        val sections = byMaterial(sample)
        assertEquals(listOf("PETG", "PLA"), sections.map { it.title })
        val pla = sections.single { it.title == "PLA" }
        assertEquals(listOf("לבן", "לבן · Matte"), pla.rows.map { it.label })
        assertEquals(mapOf(IN_USE to 1, SEALED to 1), pla.rows.first { it.label == "לבן" }.counts)
    }

    @Test
    fun `by color puts every material of a color together`() {
        val sections = byColor(sample)
        assertEquals(listOf("לבן", "שחור"), sections.map { it.title })
        assertEquals(listOf("PETG", "PLA", "PLA Matte"), sections.first().rows.map { it.label })
    }

    @Test
    fun `by status orders sections and lists slots`() {
        val sections = byStatus(sample)
        assertEquals(listOf("בשימוש", "פתוחים באחסון", "סגורים", "בדרך"), sections.map { it.title })
        val inUse = sections.first().rows.single()
        assertEquals("PLA לבן", inUse.label)
        assertEquals(listOf(2), inUse.slots)
        assertEquals(mapOf(IN_USE to 1), inUse.counts)
    }

    @Test
    fun `status table totals include on the way`() {
        val sections = statusTable(sample)
        val petg = sections.single { it.family == "PETG" }.rows.single { it.label == "לבן" }
        assertEquals(1, petg.ordered)
        assertEquals(1, petg.total)
        val pla = sections.single { it.family == "PLA" }.rows.single { it.label == "לבן" }
        assertEquals(1, pla.inUse)
        assertEquals(1, pla.sealed)
        assertEquals(2, pla.total)
    }

    @Test
    fun `material table merges variants into one cell`() {
        val table = materialTable(sample, emptyList())
        assertEquals(listOf("PETG", "PLA"), table.families)
        val white = table.rows.single { it.colorName == "לבן" }
        val pla = white.cells[1]!!
        assertEquals(3, pla.total)
        assertEquals(listOf(1L, 3L), pla.materials.map { it.materialId })
    }

    @Test
    fun `material table shows missing only for an empty cell`() {
        val table = materialTable(sample, listOf(missing("PETG", "כחול", 12)))
        val blue = table.rows.single { it.colorName == "כחול" }
        assertTrue(blue.cells[0]!!.missing)
        assertEquals(0, blue.cells[0]!!.total)
        assertNull(blue.cells[1])

        val withMatte = materialTable(sample, listOf(missing("PLA", "לבן", 10)))
        val whitePla = withMatte.rows.single { it.colorName == "לבן" }.cells[1]
        assertNotNull(whitePla)
        assertFalse(whitePla!!.missing)
    }

    @Test
    fun `every view counts the same spools`() {
        assertEquals(5, byMaterial(sample).sumOf { s -> s.rows.sumOf { it.counts.values.sum() } })
        assertEquals(5, byColor(sample).sumOf { s -> s.rows.sumOf { it.counts.values.sum() } })
        assertEquals(5, byStatus(sample).sumOf { s -> s.rows.sumOf { it.counts.values.sum() } })
        assertEquals(5, statusTable(sample).sumOf { s -> s.rows.sumOf { it.total } })
        assertEquals(5, materialTable(sample, emptyList()).rows.sumOf { r -> r.cells.sumOf { it?.total ?: 0 } })
    }

    @Test
    fun `family spelling comes from the first material alphabetically`() {
        val spools = listOf(row("pla", 5, "אדום", 20, SEALED), row("PLA", 6, "אדום", 20, SEALED))
        assertEquals(listOf("PLA"), byMaterial(spools).map { it.title })
    }
}
