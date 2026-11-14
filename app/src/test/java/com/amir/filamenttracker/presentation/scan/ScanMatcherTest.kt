package com.amir.filamenttracker.presentation.scan

import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.LabelReading
import com.amir.filamenttracker.domain.colorKey
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.readLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanMatcherTest {
    private fun row(
        id: Long,
        brand: String,
        family: String,
        finish: String,
        color: String,
        productId: Long = id,
        status: SpoolStatus = SpoolStatus.SEALED,
        variant: String = "",
    ) = SpoolRow(
        spoolId = id, status = status, amsSlot = null, orderId = null, productId = productId,
        brand = brand, weightGrams = 1000, materialId = id, materialName = family,
        materialFamily = family, materialVariant = variant, materialFinish = finish,
        colorId = id, colorName = color, colorHex = null,
    )

    private fun reading(brand: String?, family: String?, finish: String = "", vararg colors: String, variant: String = "") =
        LabelReading(brand, family?.uppercase(), variant, finish, colors.map { colorKey(it) }.toSet())

    private fun emptyReading() = readLabel("", emptyList(), emptyList(), emptyList())

    @Test fun `barcode product with spool is confirmed found`() {
        val a = row(1, "Sunlu", "PLA", "", "Black", productId = 7)
        val b = row(2, "Sunlu", "PLA", "", "Black", productId = 8)
        val result = matchScan(reading("Sunlu", null), 7, listOf(b, a))
        assertEquals(ScanMatch.Found(listOf(a), true, "Sunlu"), result)
    }

    @Test fun `barcode product without candidate spool`() {
        assertEquals(ScanMatch.KnownProductNoSpool(7), matchScan(emptyReading(), 7, emptyList()))
    }

    @Test fun `barcode without candidate falls back to text`() {
        val a = row(1, "Sunlu", "PLA", "", "Black", productId = 3)
        val result = matchScan(reading("Sunlu", "PLA", "", "Black"), 7, listOf(a))
        assertEquals(ScanMatch.Found(listOf(a), true, "Sunlu"), result)
    }

    @Test fun `empty text gives None`() {
        val a = row(1, "Sunlu", "PLA", "", "Black")
        assertEquals(ScanMatch.None, matchScan(emptyReading(), null, listOf(a)))
    }

    @Test fun `brand match narrows to that brand`() {
        val a = row(1, "iSANMATE", "PETG", "", "Black")
        val b = row(2, "Sunlu", "PETG", "", "Black")
        val result = matchScan(reading("iSANMATE", "PETG", "", "Black"), null, listOf(b, a))
        assertEquals(ScanMatch.Found(listOf(a), true, "iSANMATE"), result)
    }

    @Test fun `generic suggested when label brand missing`() {
        val g = row(1, "גנרי", "PLA", "Silk", "Gold")
        val s = row(2, "Sunlu", "PLA", "Silk", "Gold")
        val result = matchScan(reading("XZN", "PLA", "Silk", "Gold"), null, listOf(s, g))
        assertEquals(ScanMatch.GenericSuggestion("XZN", listOf(g)), result)
    }

    @Test fun `unknown brand with same line shows unconfirmed match`() {
        val s = row(1, "Sunlu", "PETG", "", "Black")
        val result = matchScan(reading("XYZ", "PETG", "", "Black"), null, listOf(s))
        assertEquals(ScanMatch.Found(listOf(s), false, "XYZ"), result)
    }

    @Test fun `generic reading brand is treated as unknown`() {
        val g = row(1, "גנרי", "PLA", "", "Black")
        val result = matchScan(reading("Generic", "PLA", "", "Black"), null, listOf(g))
        assertEquals(ScanMatch.Found(listOf(g), false, null), result)
    }

    @Test fun `finish must match exactly`() {
        val a = row(1, "Sunlu", "PLA", "", "Gold")
        assertEquals(ScanMatch.None, matchScan(reading("Sunlu", "PLA", "Silk", "Gold"), null, listOf(a)))
    }

    @Test fun `combined color needs all parts`() {
        val a = row(1, "Sunlu", "PLA", "", "Black / Red")
        assertEquals(ScanMatch.None, matchScan(reading("Sunlu", "PLA", "", "Black"), null, listOf(a)))
    }

    @Test fun `hebrew color name matches english label`() {
        val a = row(1, "Sunlu", "PLA", "", "שחור")
        val result = matchScan(reading("Sunlu", "PLA", "", "Black"), null, listOf(a))
        assertEquals(ScanMatch.Found(listOf(a), true, "Sunlu"), result)
    }

    @Test fun `family and brand compare case-insensitively`() {
        val a = row(1, "isanmate", "pla", "", "Black")
        val result = matchScan(reading("iSANMATE", "PLA", "", "Black"), null, listOf(a))
        assertEquals(ScanMatch.Found(listOf(a), true, "iSANMATE"), result)
    }

    @Test fun `empty brand spool counts as generic`() {
        val g = row(1, "", "PLA", "", "Black")
        val result = matchScan(reading("XZN", "PLA", "", "Black"), null, listOf(g))
        assertEquals(ScanMatch.GenericSuggestion("XZN", listOf(g)), result)
    }

    @Test fun `variant breaks ties`() {
        val plain = row(1, "Sunlu", "PLA", "", "Black")
        val hs = row(2, "Sunlu", "PLA", "", "Black", variant = "High Speed")
        val result = matchScan(reading("Sunlu", "PLA", "", "Black", variant = "High Speed"), null, listOf(plain, hs))
        assertEquals(ScanMatch.Found(listOf(hs, plain), true, "Sunlu"), result)
    }
}
