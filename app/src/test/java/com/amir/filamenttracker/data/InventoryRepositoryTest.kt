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
        black = catalog.createProduct("TEMPlast", "PLA", "שחור", "#000000", 1000, "")
        white = catalog.createProduct("TEMPlast", "PLA", "לבן", "#FFFFFF", 1000, "")
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
}
