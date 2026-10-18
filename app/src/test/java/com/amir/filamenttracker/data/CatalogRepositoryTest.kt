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
    fun `non-breaking space in a name merges with a normal space`() = runTest {
        val before = repo.materials.first().size
        val id = repo.addMaterial("PLA Pro")
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
        val id = repo.createProduct("Sunlu", "TPU", "כחול", "", 1000, "")
        val product = repo.products.first().single()
        assertEquals(id, product.productId)
        assertEquals("TPU", product.materialName)
        assertEquals("כחול", product.colorName)
        assertEquals(3, repo.materials.first().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank brand is rejected`() = runTest { repo.createProduct(" ", "PLA", "שחור", "", 1000, "") }

    @Test(expected = IllegalArgumentException::class)
    fun `zero weight is rejected`() = runTest { repo.createProduct("X", "PLA", "שחור", "", 0, "") }

    @Test
    fun `product with spools is archived, product without spools is deleted`() = runTest {
        val used = repo.createProduct("A", "PLA", "שחור", "", 1000, "")
        val unused = repo.createProduct("B", "PLA", "לבן", "", 1000, "")
        db.spoolDao().insertSpool(SpoolEntity(productId = used, orderId = null, status = SpoolStatus.SEALED, amsSlot = null))

        assertEquals(RemoveResult.ARCHIVED, repo.removeProduct(used))
        assertEquals(RemoveResult.DELETED, repo.removeProduct(unused))
        assertTrue(repo.products.first().isEmpty())
    }

    @Test
    fun `color in use is archived, its target dropped, and re-adding revives it`() = runTest {
        repo.createProduct("A", "PLA", "שחור", "", 1000, "")
        val pla = db.catalogDao().materialByName("PLA")!!.id
        val black = db.catalogDao().colorByName("שחור")!!.id
        db.stockDao().upsert(StockTargetEntity(pla, black, 1))

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
        repo.renameMaterial(petg, "pla")
    }
}
