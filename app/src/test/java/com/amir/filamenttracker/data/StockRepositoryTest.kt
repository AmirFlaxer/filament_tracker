package com.amir.filamenttracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StockRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var stock: StockRepository
    private lateinit var inventory: InventoryRepository
    private var blackProduct = 0L
    private var black = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        stock = StockRepository(db)
        inventory = InventoryRepository(db, FakeClock())
        val catalog = CatalogRepository(db)
        blackProduct = catalog.createProduct("TEMPlast", "PLA", "", "שחור", "#000000", 1000, "")
        catalog.createProduct("TEMPlast", "PLA", "", "לבן", "#FFFFFF", 1000, "")
        black = db.catalogDao().colorByName("שחור")!!.id
    }

    @After fun tearDown() { db.close() }

    @Test
    fun `target rows list product pairs and start untracked`() = runTest {
        val rows = stock.targetRows.first()
        assertEquals(listOf("לבן", "שחור"), rows.map { it.colorName })
        assertTrue(rows.all { it.minSealed == null })

        stock.setTarget("PLA", black, 1)
        assertEquals(1, stock.targetRows.first().single { it.colorId == black }.minSealed)
    }

    @Test
    fun `alerts follow the spool lifecycle`() = runTest {
        stock.setTarget("PLA", black, 1)
        val missing = stock.alerts.first().single()
        assertEquals(AlertKind.MISSING, missing.kind)
        assertEquals("PLA", missing.family)
        assertEquals("שחור", missing.colorName)

        inventory.addExisting(blackProduct, 1, IN_USE, slot = 1)
        assertEquals(AlertKind.RUNNING_LOW, stock.alerts.first().single().kind)

        val orderId = inventory.createOrder("Amazon", null, null, mapOf(blackProduct to 1))
        assertEquals(AlertKind.ON_THE_WAY, stock.alerts.first().single().kind)

        inventory.markOrderArrived(orderId)
        assertTrue(stock.alerts.first().isEmpty())
    }

    @Test
    fun `untracked pairs never alert`() = runTest {
        assertTrue(stock.alerts.first().isEmpty())
    }

    @Test
    fun `removing a target removes its alert`() = runTest {
        stock.setTarget("PLA", black, 1)
        stock.removeTarget("PLA", black)
        assertTrue(stock.alerts.first().isEmpty())
        assertNull(stock.targetRows.first().single { it.colorId == black }.minSealed)
    }

    @Test
    fun `a sealed spool of another variant satisfies a family target`() = runTest {
        val catalog = CatalogRepository(db)
        val rapid = catalog.createProduct("Bambu", "PETG", "Rapid", "שחור", "", 1000, "")
        stock.setTarget("PETG", black, 1)
        val missing = stock.alerts.first().single()
        assertEquals(AlertKind.MISSING, missing.kind)
        assertEquals("PETG", missing.family)

        inventory.addExisting(rapid, 1, SEALED, slot = null)
        assertTrue(stock.alerts.first().isEmpty())
    }

    @Test
    fun `target family matches ignoring case`() = runTest {
        val catalog = CatalogRepository(db)
        val petg = catalog.createProduct("Bambu", "petg", "", "שחור", "", 1000, "")
        stock.setTarget("PETG", black, 1)
        stock.setTarget("petg", black, 2)
        val petgRows = stock.targetRows.first().filter { it.colorId == black && it.family.equals("PETG", true) }
        assertEquals(listOf(2), petgRows.map { it.minSealed })
        inventory.addExisting(petg, 2, SEALED, slot = null)
        assertTrue(stock.alerts.first().isEmpty())
    }

    @Test
    fun `finishing logs a finish row and undo removes it`() = runTest {
        val id = inventory.addExisting(blackProduct, 1, IN_USE, slot = 1).single()
        inventory.finish(id)
        assertEquals("שחור", stock.finishes.first().single().colorName)

        inventory.undoLast(id)
        assertTrue(stock.finishes.first().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative minimum is rejected`() = runTest { stock.setTarget("PLA", black, -1) }
}
