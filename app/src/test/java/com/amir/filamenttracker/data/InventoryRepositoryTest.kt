package com.amir.filamenttracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.domain.IllegalTransitionException
import com.amir.filamenttracker.domain.SlotOccupiedException
import com.amir.filamenttracker.domain.SpoolRuleException
import com.amir.filamenttracker.domain.UndoNotPossibleException
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import com.amir.filamenttracker.testutil.FakeClock
import com.amir.filamenttracker.testutil.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InventoryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: InventoryRepository
    private val clock = FakeClock()
    private var black = 0L
    private var white = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        repo = InventoryRepository(db, clock)
        val catalog = CatalogRepository(db)
        black = catalog.createProduct("TEMPlast", "PLA", "", "שחור", "#000000", 1000, "")
        white = catalog.createProduct("TEMPlast", "PLA", "", "לבן", "#FFFFFF", 1000, "")
    }

    @After fun tearDown() { db.close() }

    private suspend fun spool(id: Long) = db.spoolDao().spool(id)!!
    private suspend fun events(id: Long) = db.spoolDao().events(id).first()

    @Test
    fun `existing sealed spools are created with one creation event each`() = runTest {
        val ids = repo.addExisting(black, 3, SEALED, slot = null)
        assertEquals(3, ids.size)
        ids.forEach { id ->
            assertEquals(SEALED, spool(id).status)
            val e = events(id).single()
            assertNull(e.fromStatus)
            assertEquals(SEALED, e.toStatus)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `in-use spools are added one at a time`() = runTest { repo.addExisting(black, 2, IN_USE, slot = 1) }

    @Test(expected = IllegalArgumentException::class)
    fun `in-use spool needs a slot`() = runTest { repo.addExisting(black, 1, IN_USE, slot = null) }

    @Test(expected = IllegalArgumentException::class)
    fun `existing stock cannot start as ordered`() = runTest { repo.addExisting(black, 1, ORDERED, slot = null) }

    @Test
    fun `existing in-use spool into an occupied slot moves the occupant to storage`() = runTest {
        val old = repo.addExisting(white, 1, IN_USE, slot = 1).single()
        val new = repo.addExisting(black, 1, IN_USE, slot = 1).single()
        assertEquals(OPEN_STORED, spool(old).status)
        assertNull(spool(old).amsSlot)
        assertEquals(IN_USE, spool(new).status)
        assertEquals(1, spool(new).amsSlot)
    }

    @Test
    fun `an order creates ordered spools per quantity`() = runTest {
        val orderId = repo.createOrder("Amazon", "123-456", null, mapOf(black to 2, white to 1))
        val rows = repo.orderSpools(orderId).first()
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.status == ORDERED && it.orderId == orderId })
        val summary = repo.orders.first().single()
        assertEquals(3, summary.spoolCount)
        assertEquals(3, summary.pendingCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an order without items is rejected`() = runTest { repo.createOrder("Amazon", null, null, emptyMap()) }

    @Test(expected = IllegalArgumentException::class)
    fun `an order whose quantities are all zero is rejected`() = runTest {
        repo.createOrder("Amazon", null, null, mapOf(black to 0))
    }

    @Test
    fun `arrival per spool and per order`() = runTest {
        val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 2, white to 1))
        val first = repo.orderSpools(orderId).first().first().spoolId
        repo.markArrived(listOf(first))
        assertEquals(2, repo.orders.first().single().pendingCount)

        repo.markOrderArrived(orderId)
        assertEquals(0, repo.orders.first().single().pendingCount)
        assertTrue(repo.orderSpools(orderId).first().all { it.status == SEALED })
    }

    @Test
    fun `only a spool that has not arrived can be removed from an order`() = runTest {
        val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 2))
        val (a, b) = repo.orderSpools(orderId).first().map { it.spoolId }
        repo.markArrived(listOf(a))
        try {
            repo.removeOrderedSpool(a)
            throw AssertionError("expected SpoolRuleException")
        } catch (expected: SpoolRuleException) {
        }
        repo.removeOrderedSpool(b)
        assertEquals(listOf(a), repo.orderSpools(orderId).first().map { it.spoolId })
    }

    @Test
    fun `any spool can be deleted with its events, whatever happened to it`() = runTest {
        val id = repo.addExisting(black, 1, SEALED, slot = null).single()
        repo.loadIntoSlot(id, 1)
        repo.unload(id)
        repo.deleteSpool(id)
        assertNull(db.spoolDao().spool(id))
        assertTrue(events(id).isEmpty())
    }

    @Test
    fun `deleting an in-use spool frees its slot`() = runTest {
        val first = repo.addExisting(black, 1, IN_USE, slot = 1).single()
        repo.deleteSpool(first)
        val second = repo.addExisting(white, 1, SEALED, slot = null).single()
        repo.loadIntoSlot(second, 1)
        assertEquals(IN_USE, spool(second).status)
        assertEquals(1, spool(second).amsSlot)
    }

    @Test
    fun `an arrived spool from an order can be deleted and leaves the order`() = runTest {
        val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 2))
        repo.markOrderArrived(orderId)
        val id = repo.orderSpools(orderId).first().first().spoolId
        repo.deleteSpool(id)
        assertEquals(1, repo.orderSpools(orderId).first().size)
    }

    @Test
    fun `a finished spool can be deleted`() = runTest {
        val id = repo.addExisting(black, 1, SEALED, slot = null).single()
        repo.loadIntoSlot(id, 1)
        repo.finish(id)
        repo.deleteSpool(id)
        assertNull(db.spoolDao().spool(id))
    }

    @Test
    fun `an order can be deleted only while nothing has arrived`() = runTest {
        val arrived = repo.createOrder("Amazon", null, null, mapOf(black to 1))
        repo.markOrderArrived(arrived)
        try {
            repo.deleteOrder(arrived)
            throw AssertionError("expected SpoolRuleException")
        } catch (expected: SpoolRuleException) {
        }

        val pending = repo.createOrder("Amazon", null, null, mapOf(white to 2))
        repo.deleteOrder(pending)
        assertEquals(listOf(arrived), repo.orders.first().map { it.id })
        assertEquals(1, repo.spools.first().size)
    }

    @Test
    fun `spools can be added to an existing order, even after everything arrived`() = runTest {
        val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 5))
        repo.markOrderArrived(orderId)
        val added = repo.addToOrder(orderId, black, 1)
        assertEquals(1, added.size)
        assertEquals(ORDERED, spool(added.single()).status)
        assertEquals(orderId, spool(added.single()).orderId)
        assertEquals(ORDERED, events(added.single()).single().toStatus)
        val summary = repo.orders.first().single()
        assertEquals(6, summary.spoolCount)
        assertEquals(1, summary.pendingCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `adding zero spools to an order is rejected`() = runTest {
        val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 1))
        repo.addToOrder(orderId, black, 0)
    }

    @Test(expected = SpoolRuleException::class)
    fun `adding to a missing order is rejected`() = runTest { repo.addToOrder(999, black, 1) }

    @Test
    fun `order details can be corrected`() = runTest {
        val orderId = repo.createOrder("Amazon", "111", null, mapOf(black to 1))
        val date = java.time.LocalDate.of(2026, 10, 25)
        repo.updateOrder(orderId, " AliExpress ", " 222 ", date, " RU1Z ")
        val o = repo.order(orderId).first()!!
        assertEquals("AliExpress", o.vendor)
        assertEquals("222", o.orderNumber)
        assertEquals(date, o.expectedAt)
        assertEquals("RU1Z", o.trackingNumber)

        repo.updateOrder(orderId, " ", "", null, "")
        val cleared = repo.order(orderId).first()!!
        assertEquals("Amazon", cleared.vendor)
        assertNull(cleared.orderNumber)
        assertNull(cleared.expectedAt)
        assertNull(cleared.trackingNumber)
    }

    @Test
    fun `createOrder keeps the given ordered date`() = runTest {
        val at = java.time.Instant.parse("2026-11-15T00:00:00Z")
        val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 1), orderedAt = at)
        assertEquals(at, repo.order(orderId).first()!!.orderedAt)
    }

    @Test
    fun `orderNumbers includes archived orders and skips blank ones`() = runTest {
        val archived = repo.createOrder("Amazon", "111-0000001-0000001", null, mapOf(black to 1))
        repo.markOrderArrived(archived)
        repo.setOrderArchived(archived, true)
        repo.createOrder("Amazon", null, null, mapOf(black to 1))
        assertEquals(setOf("111-0000001-0000001"), repo.orderNumbers())
    }

    @Test
    fun `ordersAwaitingTracking returns only pending Amazon orders without a tracking number`() = runTest {
        val first = repo.createOrder("Amazon", "111-0000001-0000001", null, mapOf(black to 1))
        val withTracking = repo.createOrder("Amazon", "111-0000002-0000002", null, mapOf(black to 1))
        repo.setTrackingNumber(withTracking, "RU1Z")
        repo.createOrder("AliExpress", "RU0000000000Z", null, mapOf(black to 1))
        val arrived = repo.createOrder("Amazon", "111-0000003-0000003", null, mapOf(black to 1))
        repo.markOrderArrived(arrived)
        assertEquals(listOf(first), repo.ordersAwaitingTracking().map { it.id })
    }

    @Test
    fun `automatic tracking fill never overwrites an existing number`() = runTest {
        val orderId = repo.createOrder("Amazon", "111-0000001-0000001", null, mapOf(black to 1))
        repo.setTrackingNumber(orderId, "RU0000000000Z")
        repo.setTrackingNumber(orderId, "RU1111111111Z")
        assertEquals("RU0000000000Z", repo.order(orderId).first()!!.trackingNumber)
        val empty = repo.createOrder("Amazon", "111-0000002-0000002", null, mapOf(black to 1))
        repo.setTrackingNumber(empty, "RU2222222222Z")
        assertEquals("RU2222222222Z", repo.order(empty).first()!!.trackingNumber)
        repo.updateOrder(orderId, "Amazon", "111-0000001-0000001", null, "RU3333333333Z")
        assertEquals("RU3333333333Z", repo.order(orderId).first()!!.trackingNumber)
    }

    @Test
    fun `only a spool that has not arrived can change product`() = runTest {
        val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 2))
        val (a, b) = repo.orderSpools(orderId).first().map { it.spoolId }
        repo.changeOrderedProduct(a, white)
        assertEquals(white, spool(a).productId)
        assertEquals(1, events(a).size) // a correction, not a status change

        repo.markArrived(listOf(b))
        try {
            repo.changeOrderedProduct(b, white)
            throw AssertionError("expected SpoolRuleException")
        } catch (expected: SpoolRuleException) {
        }
        assertEquals(black, spool(b).productId)
    }

    @Test
    fun `only an order that fully arrived can be archived, and it can come back`() = runTest {
        val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 2))
        try {
            repo.setOrderArchived(orderId, true)
            throw AssertionError("expected SpoolRuleException")
        } catch (expected: SpoolRuleException) {
        }
        assertEquals(false, repo.orders.first().single().archived)

        repo.markOrderArrived(orderId)
        repo.setOrderArchived(orderId, true)
        assertEquals(true, repo.orders.first().single().archived)

        repo.setOrderArchived(orderId, false)
        assertEquals(false, repo.orders.first().single().archived)
    }

    @Test
    fun `adding a spool to an archived order takes it out of the archive`() = runTest {
        val orderId = repo.createOrder("Amazon", null, null, mapOf(black to 1))
        repo.markOrderArrived(orderId)
        repo.setOrderArchived(orderId, true)
        repo.addToOrder(orderId, black, 1)
        val summary = repo.orders.first().single()
        assertEquals(false, summary.archived)
        assertEquals(1, summary.pendingCount)
    }

    @Test
    fun `loading into an occupied slot swaps without a constraint crash`() = runTest {
        val inSlot = repo.addExisting(white, 1, IN_USE, slot = 2).single()
        val next = repo.addExisting(black, 1, SEALED, slot = null).single()
        repo.loadIntoSlot(next, 2)
        assertEquals(OPEN_STORED, spool(inSlot).status)
        assertEquals(IN_USE, spool(next).status)
        assertEquals(2, spool(next).amsSlot)
    }

    @Test(expected = IllegalTransitionException::class)
    fun `a finished spool cannot be loaded`() = runTest {
        val id = repo.addExisting(black, 1, IN_USE, slot = 1).single()
        repo.finish(id)
        repo.loadIntoSlot(id, 1)
    }

    @Test
    fun `finish clears the slot and logs an event`() = runTest {
        val id = repo.addExisting(black, 1, IN_USE, slot = 3).single()
        repo.finish(id)
        assertEquals(FINISHED, spool(id).status)
        assertNull(spool(id).amsSlot)
        assertEquals(FINISHED, events(id).first().toStatus)
    }

    @Test
    fun `undo of finish restores the slot and removes the event`() = runTest {
        val id = repo.addExisting(black, 1, IN_USE, slot = 3).single()
        repo.finish(id)
        repo.undoLast(id)
        assertEquals(IN_USE, spool(id).status)
        assertEquals(3, spool(id).amsSlot)
        assertEquals(1, events(id).size)
    }

    @Test
    fun `two undos walk back two steps`() = runTest {
        val id = repo.addExisting(black, 1, SEALED, slot = null).single()
        clock.advance(Duration.ofMinutes(1))
        repo.loadIntoSlot(id, 2)
        clock.advance(Duration.ofMinutes(1))
        repo.unload(id)

        repo.undoLast(id)
        assertEquals(IN_USE, spool(id).status)
        assertEquals(2, spool(id).amsSlot)

        repo.undoLast(id)
        assertEquals(SEALED, spool(id).status)
        assertNull(spool(id).amsSlot)
    }

    @Test(expected = UndoNotPossibleException::class)
    fun `the creation event cannot be undone`() = runTest {
        val id = repo.addExisting(black, 1, SEALED, slot = null).single()
        repo.undoLast(id)
    }

    @Test
    fun `undo is blocked when the old slot is taken, and nothing changes`() = runTest {
        val first = repo.addExisting(white, 1, IN_USE, slot = 1).single()
        val second = repo.addExisting(black, 1, SEALED, slot = null).single()
        repo.loadIntoSlot(second, 1)
        try {
            repo.undoLast(first)
            throw AssertionError("expected SlotOccupiedException")
        } catch (expected: SlotOccupiedException) {
        }
        assertEquals(OPEN_STORED, spool(first).status)
        assertEquals(1, spool(second).amsSlot)
    }

    private suspend fun brandOf(spoolId: Long) = db.spoolDao().spoolRow(spoolId).first()!!

    @Test
    fun `retag generic spool moves only that spool`() = runTest {
        val generic = CatalogRepository(db).createProduct("גנרי", "PLA", "", "זהב", "", 1000, "")
        val (first, second) = repo.addExisting(generic, 2, SEALED, slot = null)
        val newId = repo.retagSpool(first, "XZN")
        val row = brandOf(first)
        assertEquals(newId, row.productId)
        assertEquals("XZN", row.brand)
        assertEquals("PLA", row.materialFamily)
        assertEquals(generic, brandOf(second).productId)
        assertEquals("גנרי", brandOf(second).brand)
    }

    @Test
    fun `retag reuses existing product`() = runTest {
        val catalog = CatalogRepository(db)
        val generic = catalog.createProduct("גנרי", "PLA", "", "זהב", "", 1000, "")
        val existing = catalog.createProduct("XZN", "PLA", "", "זהב", "", 1000, "")
        val before = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM products").use { it.moveToFirst(); it.getInt(0) }
        val (first) = repo.addExisting(generic, 1, SEALED, slot = null)
        assertEquals(existing, repo.retagSpool(first, " xzn "))
        val after = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM products").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(before, after)
    }

    @Test
    fun `retag rejects non generic`() = runTest {
        val sunlu = CatalogRepository(db).createProduct("Sunlu", "PLA", "", "זהב", "", 1000, "")
        val (first) = repo.addExisting(sunlu, 1, SEALED, slot = null)
        val e = runCatching { repo.retagSpool(first, "XZN") }.exceptionOrNull()
        assertTrue(e is SpoolRuleException)
        assertEquals("אפשר לעדכן רק סליל גנרי", e!!.message)
    }

    private fun line(productId: Long?, quantity: Int, color: String = "Red") = ImportLine(
        productId = productId, quantity = quantity, brand = "ACME", family = "PLA", variant = "",
        finish = "Matte", colorName = color, weightGrams = 1000,
    )

    @Test
    fun `importOrder creates new products once and the order with Amazon's date`() = runTest {
        val before = db.catalogDao().activeProducts().first().size
        val orderedAt = java.time.Instant.parse("2026-11-01T00:00:00Z")
        val expected = java.time.LocalDate.of(2026, 11, 20)
        val id = repo.importOrder(
            " 111-0000001-0000001 ", orderedAt, expected,
            listOf(line(null, 2), line(null, 1), line(black, 3)),
        )
        assertEquals(before + 1, db.catalogDao().activeProducts().first().size)
        val order = db.orderDao().orderOnce(id)!!
        assertEquals("Amazon", order.vendor)
        assertEquals("111-0000001-0000001", order.orderNumber)
        assertEquals(orderedAt, order.orderedAt)
        assertEquals(expected, order.expectedAt)
        assertEquals(6, db.spoolDao().spoolsOfOrder(id).count { it.status == ORDERED })
    }

    @Test
    fun `importOrder refuses an order number that already exists`() = runTest {
        repo.importOrder("111-0000001-0000001", clock.instant(), null, listOf(line(null, 1)))
        val before = db.catalogDao().activeProducts().first().size
        val e = runCatching {
            repo.importOrder("111-0000001-0000001", clock.instant(), null, listOf(line(null, 1, "Blue")))
        }.exceptionOrNull()
        assertTrue(e is SpoolRuleException)
        assertEquals("ההזמנה הזו כבר רשומה", e!!.message)
        assertEquals(before, db.catalogDao().activeProducts().first().size)
    }

    @Test
    fun `importOrder with nothing to order fails before creating products`() = runTest {
        val before = db.catalogDao().activeProducts().first().size
        val e = runCatching {
            repo.importOrder("111-0000001-0000001", clock.instant(), null, listOf(line(null, 0)))
        }.exceptionOrNull()
        assertTrue(e != null)
        assertEquals(before, db.catalogDao().activeProducts().first().size)
    }
}
