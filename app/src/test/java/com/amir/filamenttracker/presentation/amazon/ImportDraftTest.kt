package com.amir.filamenttracker.presentation.amazon

import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.domain.amazon.AmazonItem
import com.amir.filamenttracker.domain.amazon.AmazonOrder
import com.amir.filamenttracker.domain.amazon.parseAmazonOrders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ImportDraftTest {
    private val families = listOf("PLA", "PETG")
    private val lists = CatalogLists(brands = listOf("iSANMATE"), families = families, colors = listOf("Black", "White"))
    private val orders = parseAmazonOrders(
        javaClass.getResource("/amazon/order-history.txt")!!.readText(),
    )!!

    private fun product(weight: Int = 1000, color: String = "earth brown") = ProductRow(
        productId = 7, brand = "Panchroma", weightGrams = weight, note = "", materialId = 1,
        materialName = "PLA Matte", materialFamily = "PLA", materialVariant = "", materialFinish = "Matte",
        colorId = 1, colorName = color, colorHex = null,
    )

    private fun order(vararg titles: String) = AmazonOrder(
        "111-0000099-0000099", LocalDate.of(2026, 1, 1), "Arriving tomorrow", titles.map { AmazonItem(1, it) },
    )

    @Test
    fun `only orders not delivered and not already registered`() {
        val result = importableOrders(orders, setOf("111-0000002-0000002"), families)
        assertEquals(listOf("111-0000001-0000001"), result.orders.map { it.orderNumber })
    }

    @Test
    fun `non filament items of a shown order are listed as skipped`() {
        val plate = "Chitu Systems PlateCycler C1M for Bambu Lab A1 Mini 3D Printer"
        val panchroma = orders[0].items.first()
        val mixed = order(panchroma.title, plate)
        val result = importableOrders(listOf(mixed, order(plate)), emptySet(), families)
        assertEquals(1, result.orders.size)
        assertEquals(listOf(panchroma.title), result.orders[0].items.map { it.title })
        assertEquals(listOf(plate, plate), result.skippedTitles) // the second order has no filament at all
    }

    @Test
    fun `an order with no filament shows only in the gray line`() {
        val plate = "Chitu Systems PlateCycler C1M for Bambu Lab A1 Mini 3D Printer"
        val result = importableOrders(listOf(order(plate)), emptySet(), families)
        assertEquals(emptyList<AmazonOrder>(), result.orders)
        assertEquals(listOf(plate), result.skippedTitles)
    }

    @Test
    fun `cancelled and returned orders are neither offered nor listed as skipped`() {
        val title = orders[0].items.first().title
        val closed = listOf("Cancelled", "Canceled", "Return complete", "Refund issued").mapIndexed { i, s ->
            order(title).copy(orderNumber = "111-000009$i-0000099", status = s)
        }
        val result = importableOrders(closed, emptySet(), families)
        assertEquals(emptyList<AmazonOrder>(), result.orders)
        assertEquals(emptyList<String>(), result.skippedTitles)
    }

    private fun single(title: String, quantity: Int = 1) =
        buildCards(AmazonOrder("x", LocalDate.of(2026, 1, 1), "s", listOf(AmazonItem(quantity, title))), emptyList(), lists)[0]

    @Test
    fun `three pack with two colors asks to mark one`() {
        val c = single("ACME PLA Filament Black/White 3-Pack 1kg")
        assertEquals(listOf(ImportRow("Black", 1, null), ImportRow("White", 1, null)), c.rows)
        assertEquals(1, c.colorsToMark)
    }

    @Test
    fun `four pack with one color asks to mark three`() {
        val c = single("ACME PLA Filament Black 4-Pack 1kg", quantity = 2)
        assertEquals(listOf(ImportRow("Black", 2, null)), c.rows)
        assertEquals(3, c.colorsToMark)
    }

    @Test
    fun `matching color count splits one each`() {
        val c = single("ACME PLA Filament Black/White 2-Pack 1kg", quantity = 3)
        assertEquals(listOf(ImportRow("Black", 3, null), ImportRow("White", 3, null)), c.rows)
        assertEquals(0, c.colorsToMark)
    }

    @Test
    fun `panchroma becomes two new rows of two`() {
        val cards = buildCards(orders[0], emptyList(), lists)
        assertEquals(1, cards.size)
        assertEquals(listOf(ImportRow("Earth Brown", 2, null), ImportRow("Wood Brown", 2, null)), cards[0].rows)
        assertEquals(0, cards[0].colorsToMark)
    }

    @Test
    fun `existing product is matched`() {
        val cards = buildCards(orders[0], listOf(product()), lists)
        assertEquals(7L, cards[0].rows[0].productId)
        assertNull(cards[0].rows[1].productId)
        val other = buildCards(orders[0], listOf(product(weight = 750)), lists)
        assertNull(other[0].rows[0].productId)
    }

    @Test
    fun `bundle without colors asks to mark`() {
        val isanmate = orders.flatMap { it.items }.first { it.title.startsWith("iSANMATE") }
        val card = buildCards(order(isanmate.title), emptyList(), lists)[0]
        assertEquals(emptyList<ImportRow>(), card.rows)
        assertEquals(4, card.colorsToMark)
        val qfour = orders.flatMap { it.items }.first { it.title.startsWith("QFOUR") }
        val q = buildCards(
            AmazonOrder("x", LocalDate.of(2026, 1, 1), "s", listOf(AmazonItem(2, qfour.title))), emptyList(), lists,
        )[0]
        assertEquals(1, q.colorsToMark)
    }

    @Test
    fun `combined colors make one row`() {
        val o = AmazonOrder(
            "x", LocalDate.of(2026, 1, 1), "s", listOf(AmazonItem(3, "ACME Silk PLA Dual Color Red/Gold 1kg")),
        )
        assertEquals(listOf(ImportRow("Red / Gold", 3, null)), buildCards(o, emptyList(), lists)[0].rows)
    }

    @Test
    fun `spool count`() {
        assertEquals(4, listOf(buildCards(orders[0], emptyList(), lists)[0]).spoolCount)
    }
}

class ImportCardEditTest {
    private fun card(rows: List<ImportRow>, toMark: Int = 0, itemQuantity: Int = 2) = ImportCard(
        key = 0, sourceTitle = "t", itemQuantity = itemQuantity, brand = "B", family = "PLA", variant = "", finish = "",
        weightGrams = 1000, rows = rows, colorsToMark = toMark,
    )

    @Test fun zeroQuantityRemovesRow() {
        val c = card(listOf(ImportRow("Black", 2, null), ImportRow("White", 2, null)))
        assertEquals(listOf("White"), c.withRowQuantity(0, 0, emptyList()).rows.map { it.colorName })
    }

    @Test fun quantityChangesOnlyThatRow() {
        val c = card(listOf(ImportRow("Black", 2, null), ImportRow("White", 2, null)))
        assertEquals(listOf(2, 5), c.withRowQuantity(1, 5, emptyList()).rows.map { it.quantity })
    }

    @Test fun blankRenameIsIgnored() {
        val c = card(listOf(ImportRow("Black", 2, null)))
        assertEquals(c, c.withRowColor(0, "  ", emptyList()))
        assertEquals("Red", c.withRowColor(0, " Red ", emptyList()).rows.single().colorName)
    }

    @Test fun markingAddsRowsAndUsesUpTheCount() {
        val c = card(emptyList(), toMark = 3).markColors(listOf("Black", "White"), emptyList())
        assertEquals(listOf("Black" to 2, "White" to 2), c.rows.map { it.colorName to it.quantity })
        assertEquals(1, c.colorsToMark)
    }

    @Test fun markingNeverGoesBelowZeroAndMergesSameColor() {
        val c = card(listOf(ImportRow("Black", 2, null)), toMark = 1).markColors(listOf("black", "Red"), emptyList())
        assertEquals(0, c.colorsToMark)
        assertEquals(listOf("Black" to 4, "Red" to 2), c.rows.map { it.colorName to it.quantity })
    }
}
