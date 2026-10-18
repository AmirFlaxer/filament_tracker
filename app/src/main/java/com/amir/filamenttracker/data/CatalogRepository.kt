package com.amir.filamenttracker.data

import androidx.room.withTransaction
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.ColorEntity
import com.amir.filamenttracker.data.db.MaterialEntity
import com.amir.filamenttracker.data.db.ProductEntity
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.domain.ColorHex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class RemoveResult { DELETED, ARCHIVED }

@Singleton
class CatalogRepository @Inject constructor(private val db: AppDatabase) {
    private val dao = db.catalogDao()
    private val stockDao = db.stockDao()

    val materials: Flow<List<MaterialEntity>> = dao.activeMaterials()
    val colors: Flow<List<ColorEntity>> = dao.activeColors()
    val products: Flow<List<ProductRow>> = dao.activeProducts()

    /** Every brand ever used (archived products included), unique ignoring case, first spelling kept. */
    val brands: Flow<List<String>> = dao.allBrands().map { names ->
        names.distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /** Id of the material with this name: the existing one (revived if archived) or a new one. */
    suspend fun addMaterial(name: String): Long = db.withTransaction {
        val clean = cleanName(name)
        val existing = dao.materialByName(clean)
        if (existing == null) {
            dao.insertMaterial(MaterialEntity(name = clean))
        } else {
            if (existing.archived) dao.setMaterialArchived(existing.id, false)
            existing.id
        }
    }

    /** Like [addMaterial]. A hex given for an existing color only fills in a missing swatch. */
    suspend fun addColor(name: String, hexInput: String = ""): Long = db.withTransaction {
        val clean = cleanName(name)
        val hex = ColorHex.normalize(hexInput)
        val existing = dao.colorByName(clean)
        if (existing == null) {
            dao.insertColor(ColorEntity(name = clean, hex = hex))
        } else {
            if (existing.archived) dao.setColorArchived(existing.id, false)
            if (existing.hex == null && hex != null) dao.updateColor(existing.id, existing.name, hex)
            existing.id
        }
    }

    suspend fun createProduct(
        brand: String,
        materialName: String,
        colorName: String,
        colorHex: String,
        weightGrams: Int,
        note: String,
    ): Long = db.withTransaction {
        val cleanBrand = cleanName(brand, what = "מותג")
        require(weightGrams > 0) { "משקל חייב להיות גדול מאפס" }
        val materialId = addMaterial(materialName)
        val colorId = addColor(colorName, colorHex)
        dao.insertProduct(
            ProductEntity(
                brand = cleanBrand, materialId = materialId, colorId = colorId,
                weightGrams = weightGrams, note = note.trim(),
            ),
        )
    }

    suspend fun renameMaterial(id: Long, name: String) = db.withTransaction {
        val clean = cleanName(name)
        val clash = dao.materialByName(clean)
        require(clash == null || clash.id == id) { "כבר קיים חומר בשם $clean" }
        dao.renameMaterial(id, clean)
    }

    suspend fun updateColor(id: Long, name: String, hexInput: String) = db.withTransaction {
        val clean = cleanName(name)
        val clash = dao.colorByName(clean)
        require(clash == null || clash.id == id) { "כבר קיים צבע בשם $clean" }
        dao.updateColor(id, clean, ColorHex.normalize(hexInput))
    }

    suspend fun updateProduct(id: Long, brand: String, weightGrams: Int, note: String) {
        require(weightGrams > 0) { "משקל חייב להיות גדול מאפס" }
        dao.updateProduct(id, cleanName(brand, what = "מותג"), weightGrams, note.trim())
    }

    /** Spec section 4: archive when something still uses it, otherwise delete. */
    suspend fun removeMaterial(id: Long): RemoveResult = db.withTransaction {
        if (dao.productCountForMaterial(id) > 0) {
            dao.setMaterialArchived(id, true)
            stockDao.deleteForMaterial(id)
            RemoveResult.ARCHIVED
        } else {
            dao.deleteMaterial(id)
            RemoveResult.DELETED
        }
    }

    suspend fun removeColor(id: Long): RemoveResult = db.withTransaction {
        if (dao.productCountForColor(id) > 0) {
            dao.setColorArchived(id, true)
            stockDao.deleteForColor(id)
            RemoveResult.ARCHIVED
        } else {
            dao.deleteColor(id)
            RemoveResult.DELETED
        }
    }

    suspend fun removeProduct(id: Long): RemoveResult = db.withTransaction {
        if (dao.spoolCountForProduct(id) > 0) {
            dao.setProductArchived(id, true)
            RemoveResult.ARCHIVED
        } else {
            dao.deleteProduct(id)
            RemoveResult.DELETED
        }
    }

    // NOCASE lookups handle Latin case; this handles spaces, so "PLA " and "pla" are one row.
    private fun cleanName(raw: String, what: String = "שם"): String {
        val clean = raw.trim().replace(Regex("[\\s\\p{Z}]+"), " ")
        require(clean.isNotEmpty()) { "$what לא יכול להיות ריק" }
        return clean
    }
}
