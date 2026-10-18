package com.amir.filamenttracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.SpoolEntity
import com.amir.filamenttracker.data.db.StockTargetEntity
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.testutil.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CatalogRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CatalogRepository

    @Before fun setUp() { db = inMemoryDb(); repo = CatalogRepository(db) }
    @After fun tearDown() { db.close() }

    @Test
    fun `material names match regardless of case and outer spaces`() = runTest {
        val seeded = db.catalogDao().materialByName("PLA")!!.id
        assertEquals(seeded, repo.addMaterial("  pla "))
        assertEquals(2, repo.materials.first().size)
    }

    @Test
    fun `same family and variant gives the same material`() = runTest {
        val before = repo.materials.first().size
        val id = repo.addMaterial("PLA", "Pro")
        assertEquals(id, repo.addMaterial("PLA\u00A0Pro"))
        assertEquals(before + 1, repo.materials.first().size)
    }

    @Test
    fun `color names are trimmed and inner spaces collapsed`() = runTest {
        val id = repo.addColor("ירוק ים")
        assertEquals(id, repo.addColor("  ירוק   ים "))
        assertEquals(1, repo.colors.first().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank material name is rejected`() = runTest { repo.addMaterial("   ") }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed hex is rejected`() = runTest { repo.addColor("אדום", "red") }

    @Test
    fun `existing color without hex picks up a new hex`() = runTest {
        val id = repo.addColor("לבן")
        repo.addColor("לבן", "#ffffff")
        assertEquals("#FFFFFF", repo.colors.first().single { it.id == id }.hex)
    }

    @Test
    fun `createProduct creates a missing material and color`() = runTest {
        val id = repo.createProduct("Sunlu", "TPU", "", "כחול", "", 1000, "")
        val product = repo.products.first().single()
        assertEquals(id, product.productId)
        assertEquals("TPU", product.materialName)
        assertEquals("TPU", product.materialFamily)
        assertEquals("כחול", product.colorName)
        assertEquals(3, repo.materials.first().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank brand is rejected`() = runTest { repo.createProduct(" ", "PLA", "", "שחור", "", 1000, "") }

    @Test(expected = IllegalArgumentException::class)
    fun `zero weight is rejected`() = runTest { repo.createProduct("X", "PLA", "", "שחור", "", 0, "") }

    @Test
    fun `product with spools is archived, product without spools is deleted`() = runTest {
        val used = repo.createProduct("A", "PLA", "", "שחור", "", 1000, "")
        val unused = repo.createProduct("B", "PLA", "", "לבן", "", 1000, "")
        db.spoolDao().insertSpool(SpoolEntity(productId = used, orderId = null, status = SpoolStatus.SEALED, amsSlot = null))

        assertEquals(RemoveResult.ARCHIVED, repo.removeProduct(used))
        assertEquals(RemoveResult.DELETED, repo.removeProduct(unused))
        assertTrue(repo.products.first().isEmpty())
    }

    @Test
    fun `color in use is archived, its target dropped, and re-adding revives it`() = runTest {
        repo.createProduct("A", "PLA", "", "שחור", "", 1000, "")
        val black = db.catalogDao().colorByName("שחור")!!.id
        db.stockDao().upsert(StockTargetEntity("PLA", black, 1))

        assertEquals(RemoveResult.ARCHIVED, repo.removeColor(black))
        assertTrue(repo.colors.first().isEmpty())
        assertTrue(db.stockDao().targetRows().first().none { it.minSealed != null })

        assertEquals(black, repo.addColor("שחור"))
        assertEquals(1, repo.colors.first().size)
    }

    @Test
    fun `unused color is deleted`() = runTest {
        val id = repo.addColor("סגול")
        assertEquals(RemoveResult.DELETED, repo.removeColor(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `renaming a material onto an existing name is rejected`() = runTest {
        val petg = db.catalogDao().materialByName("PETG")!!.id
        repo.updateMaterial(petg, "pla", "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank family is rejected`() = runTest { repo.addMaterial("  ", "Rapid") }

    @Test
    fun `addMaterial finds the existing family and variant ignoring case`() = runTest {
        val rapid = repo.addMaterial("PETG", "Rapid")
        assertEquals(rapid, repo.addMaterial("petg", "rapid"))
        assertEquals("PETG Rapid", db.catalogDao().materialByName("petg rapid")!!.name)
    }

    @Test
    fun `addMaterial with a symbol variant composes an attached name`() = runTest {
        val id = repo.addMaterial("PLA", "+")
        val m = db.catalogDao().materialByName("PLA+")!!
        assertEquals(id, m.id)
        assertEquals("PLA", m.family)
        assertEquals("+", m.variant)
    }

    @Test
    fun `addMaterial revives an archived material`() = runTest {
        val id = repo.addMaterial("PETG", "Rapid")
        repo.createProduct("A", "PETG", "Rapid", "שחור", "", 1000, "").also {
            db.spoolDao().insertSpool(SpoolEntity(productId = it, orderId = null, status = SpoolStatus.SEALED, amsSlot = null))
            repo.removeProduct(it)
        }
        assertEquals(RemoveResult.ARCHIVED, repo.removeMaterial(id))
        assertEquals(id, repo.addMaterial("PETG", "Rapid"))
        assertTrue(repo.materials.first().any { it.id == id })
    }

    @Test
    fun `addMaterial falls back to a legacy material with the composed name`() = runTest {
        db.catalogDao().insertMaterial(com.amir.filamenttracker.data.db.MaterialEntity(name = "PETG Rapid", family = "Other", variant = "X"))
        val legacy = db.catalogDao().materialByName("PETG Rapid")!!.id
        assertEquals(legacy, repo.addMaterial("PETG", "Rapid"))
    }

    @Test
    fun `removing one material of a family keeps the family targets`() = runTest {
        val rapid = repo.addMaterial("PETG", "Rapid")
        repo.createProduct("A", "PETG", "", "שחור", "", 1000, "")
        val black = db.catalogDao().colorByName("שחור")!!.id
        db.stockDao().upsert(StockTargetEntity("PETG", black, 1))

        assertEquals(RemoveResult.DELETED, repo.removeMaterial(rapid))
        assertEquals(listOf(1), db.stockDao().targetRows().first().mapNotNull { it.minSealed })
    }

    @Test
    fun `removing the last material of a family drops its targets`() = runTest {
        val petg = repo.addMaterial("PETG")
        val black = repo.addColor("שחור")
        db.stockDao().upsert(StockTargetEntity("PETG", black, 1))
        assertEquals(RemoveResult.DELETED, repo.removeMaterial(petg))
        assertTrue(db.stockDao().targetRows().first().none { it.minSealed != null })
    }

    @Test
    fun `updateMaterial recomputes the name`() = runTest {
        val id = repo.addMaterial("PETG", "Rapid")
        repo.updateMaterial(id, "PETG", "Matte")
        val m = repo.materials.first().single { it.id == id }
        assertEquals("PETG Matte", m.name)
        assertEquals("PETG", m.family)
        assertEquals("Matte", m.variant)
    }

    @Test
    fun `updateMaterial moves family targets along when the family is renamed`() = runTest {
        repo.removeMaterial(db.catalogDao().materialByName("PETG")!!.id) // the seeded plain PETG
        val id = repo.addMaterial("PETG", "Rapid")
        val black = repo.addColor("שחור")
        db.stockDao().upsert(StockTargetEntity("PETG", black, 2))
        repo.updateMaterial(id, "PET-G", "Rapid")
        assertEquals(listOf("PET-G"), db.stockDao().targetRows().first().filter { it.minSealed != null }.map { it.family })
    }

    @Test
    fun `brands are distinct ignoring case, sorted, and include archived products`() = runTest {
        repo.createProduct("Sunlu", "PLA", "", "שחור", "", 1000, "")
        repo.createProduct("sunlu", "PLA", "", "לבן", "", 1000, "")
        val archived = repo.createProduct("Bambu", "PETG", "", "לבן", "", 1000, "")
        repo.createProduct("Elegoo", "PLA", "", "אדום", "", 1000, "")
        db.spoolDao().insertSpool(SpoolEntity(productId = archived, orderId = null, status = SpoolStatus.SEALED, amsSlot = null))
        assertEquals(RemoveResult.ARCHIVED, repo.removeProduct(archived))
        assertEquals(listOf("Bambu", "Elegoo", "Sunlu"), repo.brands.first())
    }
}
