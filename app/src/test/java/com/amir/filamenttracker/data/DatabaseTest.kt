package com.amir.filamenttracker.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.ColorEntity
import com.amir.filamenttracker.data.db.ProductEntity
import com.amir.filamenttracker.data.db.SpoolEntity
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.testutil.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    @Test
    fun `PLA and PETG are seeded`() = runTest {
        assertEquals(listOf("PETG", "PLA"), db.catalogDao().activeMaterials().first().map { it.name })
    }

    @Test
    fun `spool rows join names and keep status`() = runTest {
        val pla = db.catalogDao().materialByName("PLA")!!.id
        val black = db.catalogDao().insertColor(ColorEntity(name = "שחור", hex = "#000000"))
        val product = db.catalogDao().insertProduct(ProductEntity(brand = "TEMPlast", materialId = pla, colorId = black))
        db.spoolDao().insertSpool(SpoolEntity(productId = product, orderId = null, status = SpoolStatus.IN_USE, amsSlot = 2))

        val row = db.spoolDao().spoolRows().first().single()
        assertEquals("TEMPlast PLA שחור", "${row.brand} ${row.materialName} ${row.colorName}")
        assertEquals("PLA", row.materialFamily)
        assertEquals("", row.materialVariant)
        assertEquals(SpoolStatus.IN_USE, row.status)
        assertEquals(2, row.amsSlot)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `two spools can never share a slot`() = runTest {
        val pla = db.catalogDao().materialByName("PLA")!!.id
        val black = db.catalogDao().insertColor(ColorEntity(name = "שחור"))
        val product = db.catalogDao().insertProduct(ProductEntity(brand = "X", materialId = pla, colorId = black))
        db.spoolDao().insertSpool(SpoolEntity(productId = product, orderId = null, status = SpoolStatus.IN_USE, amsSlot = 1))
        db.spoolDao().insertSpool(SpoolEntity(productId = product, orderId = null, status = SpoolStatus.IN_USE, amsSlot = 1))
    }

    @Test
    fun `many spools outside slots are fine`() = runTest {
        val pla = db.catalogDao().materialByName("PLA")!!.id
        val black = db.catalogDao().insertColor(ColorEntity(name = "שחור"))
        val product = db.catalogDao().insertProduct(ProductEntity(brand = "X", materialId = pla, colorId = black))
        repeat(3) {
            db.spoolDao().insertSpool(SpoolEntity(productId = product, orderId = null, status = SpoolStatus.SEALED, amsSlot = null))
        }
        assertEquals(3, db.spoolDao().spoolRows().first().size)
    }
}
