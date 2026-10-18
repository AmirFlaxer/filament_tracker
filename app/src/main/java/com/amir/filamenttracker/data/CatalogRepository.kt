package com.amir.filamenttracker.data

import androidx.room.withTransaction
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.ColorEntity
import com.amir.filamenttracker.data.db.MaterialEntity
import com.amir.filamenttracker.data.db.ProductEntity
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.data.db.StockTargetEntity
import com.amir.filamenttracker.domain.ColorHex
import com.amir.filamenttracker.domain.composeMaterialName
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

    /**
     * Id of the material with this main material and sub-type: the existing one (revived if
     * archived), else one whose composed name matches, else a new one.
     */
    suspend fun addMaterial(family: String, variant: String = ""): Long = db.withTransaction {
        val cleanFamily = cleanName(family, what = "חומר ראשי")
        val cleanVariant = cleanText(variant)
        val existing = dao.materialByFamilyVariant(cleanFamily, cleanVariant)
            ?: dao.materialByName(composeMaterialName(cleanFamily, cleanVariant))
        if (existing == null) {
            dao.insertMaterial(
                MaterialEntity(
                    name = composeMaterialName(cleanFamily, cleanVariant), family = cleanFamily, variant = cleanVariant,
                ),
            )
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
        materialFamily: String,
        materialVariant: String,
        colorName: String,
        colorHex: String,
        weightGrams: Int,
        note: String,
    ): Long = db.withTransaction {
        val cleanBrand = cleanName(brand, what = "מותג")
        require(weightGrams > 0) { "משקל חייב להיות גדול מאפס" }
        val materialId = addMaterial(materialFamily, materialVariant)
        val colorId = addColor(colorName, colorHex)
        dao.insertProduct(
            ProductEntity(
                brand = cleanBrand, materialId = materialId, colorId = colorId,
                weightGrams = weightGrams, note = note.trim(),
            ),
        )
    }

    suspend fun updateMaterial(id: Long, family: String, variant: String) = db.withTransaction {
        val cleanFamily = cleanName(family, what = "חומר ראשי")
        val cleanVariant = cleanText(variant)
        val name = composeMaterialName(cleanFamily, cleanVariant)
        val clash = dao.materialByName(name)
        require(clash == null || clash.id == id) { "כבר קיים חומר בשם $name" }
        val old = dao.material(id)
        dao.updateMaterial(id, name, cleanFamily, cleanVariant)
        // A renamed family takes its targets with it, unless another material still uses the old one.
        if (old != null && !old.family.equals(cleanFamily, ignoreCase = true) &&
            dao.otherActiveMaterialsInFamily(id, old.family) == 0
        ) {
            val moving = stockDao.targetsOfFamily(old.family)
            stockDao.deleteForFamily(old.family)
            for (target in moving) {
                // Merge into an existing target of the new family (any spelling), keeping the highest minimum.
                val existing = stockDao.targetsOfFamily(cleanFamily).firstOrNull { it.colorId == target.colorId }
                stockDao.delete(cleanFamily, target.colorId)
                stockDao.upsert(
                    StockTargetEntity(
                        existing?.family ?: cleanFamily, target.colorId,
                        maxOf(target.minSealed, existing?.minSealed ?: 0),
                    ),
                )
            }
        }
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
        val family = dao.material(id)?.family
        val result = if (dao.productCountForMaterial(id) > 0) {
            dao.setMaterialArchived(id, true)
            RemoveResult.ARCHIVED
        } else {
            dao.deleteMaterial(id)
            RemoveResult.DELETED
        }
        // Targets belong to the main material: they go only with its last active material.
        if (family != null && dao.otherActiveMaterialsInFamily(id, family) == 0) stockDao.deleteForFamily(family)
        result
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

    private fun cleanText(raw: String): String = raw.trim().replace(Regex("[\\s\\p{Z}]+"), " ")

    // NOCASE lookups handle Latin case; this handles spaces, so "PLA " and "pla" are one row.
    private fun cleanName(raw: String, what: String = "שם"): String {
        val clean = cleanText(raw)
        require(clean.isNotEmpty()) { "$what לא יכול להיות ריק" }
        return clean
    }
}
