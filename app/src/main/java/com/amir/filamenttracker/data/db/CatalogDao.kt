package com.amir.filamenttracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM materials WHERE archived = 0 ORDER BY name")
    fun activeMaterials(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM colors WHERE archived = 0 ORDER BY name")
    fun activeColors(): Flow<List<ColorEntity>>

    // GROUP BY (not DISTINCT) so the earliest-created spelling comes first, deterministically.
    @Query("SELECT brand FROM products GROUP BY brand ORDER BY MIN(id)")
    fun allBrands(): Flow<List<String>>

    @Query(
        """
        SELECT p.id AS productId, p.brand AS brand, p.weightGrams AS weightGrams, p.note AS note,
               m.id AS materialId, m.name AS materialName,
               m.family AS materialFamily, m.variant AS materialVariant,
               c.id AS colorId, c.name AS colorName, c.hex AS colorHex
        FROM products p
        JOIN materials m ON m.id = p.materialId
        JOIN colors c ON c.id = p.colorId
        WHERE p.archived = 0 AND m.archived = 0 AND c.archived = 0
        ORDER BY m.name, c.name, p.brand
        """,
    )
    fun activeProducts(): Flow<List<ProductRow>>

    @Query("SELECT * FROM materials WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun materialByName(name: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE family = :family COLLATE NOCASE AND variant = :variant COLLATE NOCASE ORDER BY id LIMIT 1")
    suspend fun materialByFamilyVariant(family: String, variant: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun material(id: Long): MaterialEntity?

    @Query("SELECT COUNT(*) FROM materials WHERE archived = 0 AND id != :id AND family = :family COLLATE NOCASE")
    suspend fun otherActiveMaterialsInFamily(id: Long, family: String): Int

    @Query("SELECT * FROM colors WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun colorByName(name: String): ColorEntity?

    @Insert suspend fun insertMaterial(material: MaterialEntity): Long
    @Insert suspend fun insertColor(color: ColorEntity): Long
    @Insert suspend fun insertProduct(product: ProductEntity): Long

    @Query("UPDATE materials SET name = :name, family = :family, variant = :variant WHERE id = :id")
    suspend fun updateMaterial(id: Long, name: String, family: String, variant: String)

    @Query("UPDATE colors SET name = :name, hex = :hex WHERE id = :id")
    suspend fun updateColor(id: Long, name: String, hex: String?)

    @Query("UPDATE products SET brand = :brand, weightGrams = :weightGrams, note = :note WHERE id = :id")
    suspend fun updateProduct(id: Long, brand: String, weightGrams: Int, note: String)

    @Query("UPDATE materials SET archived = :archived WHERE id = :id")
    suspend fun setMaterialArchived(id: Long, archived: Boolean)

    @Query("UPDATE colors SET archived = :archived WHERE id = :id")
    suspend fun setColorArchived(id: Long, archived: Boolean)

    @Query("UPDATE products SET archived = :archived WHERE id = :id")
    suspend fun setProductArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM materials WHERE id = :id") suspend fun deleteMaterial(id: Long)
    @Query("DELETE FROM colors WHERE id = :id") suspend fun deleteColor(id: Long)
    @Query("DELETE FROM products WHERE id = :id") suspend fun deleteProduct(id: Long)

    @Query("SELECT COUNT(*) FROM products WHERE materialId = :id")
    suspend fun productCountForMaterial(id: Long): Int

    @Query("SELECT COUNT(*) FROM products WHERE colorId = :id")
    suspend fun productCountForColor(id: Long): Int

    @Query("SELECT COUNT(*) FROM spools WHERE productId = :id")
    suspend fun spoolCountForProduct(id: Long): Int
}
