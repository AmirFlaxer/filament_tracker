package com.amir.filamenttracker.domain.amazon

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmazonOrdersTest {
    private val page = javaClass.getResource("/amazon/order-history.txt")!!.readText()
    private val today = LocalDate.of(2026, 11, 16)

    @Test fun `fixture has ten orders in page order`() {
        val orders = parseAmazonOrders(page)!!
        assertEquals(10, orders.size)
        assertEquals("111-0000001-0000001", orders[0].orderNumber)
        assertEquals(LocalDate.of(2026, 11, 15), orders[0].orderedOn)
        assertEquals("Arriving November 24 - December 1", orders[0].status)
        assertEquals(
            listOf(
                AmazonItem(
                    2,
                    "Panchroma Matte PLA 3D Printer Filament Bundle 2kg, Earth Brown+Wood Brown | 1.75mm High Speed 3D Printing Filament Fits for Most FDM Printers, ±0.02mm Accuracy, Neaty Wound & Matte Finish, 2kg Pack",
                ),
            ),
            orders[0].items,
        )
    }

    @Test fun `missing quantity means one`() {
        val items = parseAmazonOrders(page)!![1].items
        assertEquals(1, items.size)
        assertEquals(1, items[0].quantity)
        assertTrue(items[0].title.startsWith("iSANMATE Silk PLA"))
        assertTrue(items[0].title.endsWith("4 Spools 1kg Each"))
    }

    @Test fun `an order with two products keeps both`() {
        val items = parseAmazonOrders(page)!![4].items
        assertEquals(listOf(2, 2), items.map { it.quantity })
        assertTrue(items[0].title.startsWith("QFOUR"))
        assertTrue(items[1].title.startsWith("XZN"))
    }

    @Test fun `footer after the last order is not an item`() {
        val items = parseAmazonOrders(page)!![9].items
        assertEquals(1, items.size)
        assertTrue(items[0].title.startsWith("UGUESS"))
    }

    @Test fun `delivered orders are marked`() {
        assertEquals(8, parseAmazonOrders(page)!!.count { it.isDelivered })
    }

    @Test fun `page without orders is null, not empty`() {
        assertNull(parseAmazonOrders("Sign in\nEmail"))
    }

    private fun expected(status: String, ordered: LocalDate) = expectedDate(status, ordered, today)

    @Test fun `range uses its end date`() {
        assertEquals(LocalDate.of(2026, 12, 1), expected("Arriving November 24 - December 1", LocalDate.of(2026, 11, 15)))
        assertEquals(LocalDate.of(2026, 12, 21), expected("Arriving December 8 - December 21", LocalDate.of(2026, 11, 8)))
    }

    @Test fun `range crossing the year gives next year`() {
        assertEquals(LocalDate.of(2027, 1, 9), expected("Arriving January 3 - January 9", LocalDate.of(2026, 12, 20)))
    }

    @Test fun `single date and short month names`() {
        assertEquals(LocalDate.of(2026, 11, 24), expected("Arriving November 24", LocalDate.of(2026, 11, 15)))
        assertEquals(LocalDate.of(2026, 12, 1), expected("Arriving Nov 24 - Dec 1", LocalDate.of(2026, 11, 15)))
    }

    @Test fun `tomorrow and weekday are relative to today`() {
        assertEquals(LocalDate.of(2026, 11, 17), expected("Arriving tomorrow", LocalDate.of(2026, 11, 15)))
        assertEquals(LocalDate.of(2026, 11, 21), expected("Arriving Saturday", LocalDate.of(2026, 11, 15)))
        assertEquals(LocalDate.of(2026, 11, 23), expected("Arriving Monday", LocalDate.of(2026, 11, 15)))
    }

    @Test fun `status without an arrival is null`() {
        assertNull(expected("Shipped", LocalDate.of(2026, 11, 15)))
    }

    private fun range(status: String, ordered: LocalDate) = expectedRange(status, ordered, today)

    @Test fun `range reader gives both ends`() {
        assertEquals(
            DateRange(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 12, 1)),
            range("Arriving November 24 - December 1", LocalDate.of(2026, 11, 15)),
        )
        assertEquals(
            DateRange(LocalDate.of(2027, 1, 3), LocalDate.of(2027, 1, 9)),
            range("Arriving January 3 - January 9", LocalDate.of(2026, 12, 20)),
        )
    }

    @Test fun `range crossing the year moves only the later end`() {
        assertEquals(
            DateRange(LocalDate.of(2026, 12, 29), LocalDate.of(2027, 1, 4)),
            range("Arriving December 29 - January 4", LocalDate.of(2026, 12, 20)),
        )
    }

    @Test fun `single date gives equal ends`() {
        assertEquals(DateRange(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 24)), range("Arriving November 24", LocalDate.of(2026, 11, 15)))
        assertEquals(DateRange(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 17)), range("Arriving tomorrow", LocalDate.of(2026, 11, 15)))
        assertEquals(DateRange(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 21)), range("Arriving Saturday", LocalDate.of(2026, 11, 15)))
    }

    @Test fun `range reader without an arrival is null`() {
        assertNull(range("Shipped", LocalDate.of(2026, 11, 15)))
    }

    @Test fun `arriving today is today`() {
        assertEquals(today, expected("Arriving today", LocalDate.of(2026, 11, 15)))
        assertEquals(DateRange(today, today), range("Arriving today", LocalDate.of(2026, 11, 15)))
    }

    @Test fun `cancelled and returned statuses are closed`() {
        fun closed(status: String) = AmazonOrder("x", today, status, emptyList()).isClosed
        assertTrue(closed("Delivered November 20"))
        assertTrue(closed("Cancelled"))
        assertTrue(closed("Canceled"))
        assertTrue(closed("Return complete"))
        assertTrue(closed("Refund issued"))
        assertTrue(!closed("Arriving tomorrow"))
        assertTrue(!closed("Shipped"))
    }
}
