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

    @Query(
        """
        SELECT p.id AS productId, p.brand AS brand, p.weightGrams AS weightGrams, p.note AS note,
               m.id AS materialId, m.name AS materialName,
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

    @Query("SELECT * FROM colors WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun colorByName(name: String): ColorEntity?

    @Insert suspend fun insertMaterial(material: MaterialEntity): Long
    @Insert suspend fun insertColor(color: ColorEntity): Long
    @Insert suspend fun insertProduct(product: ProductEntity): Long

    @Query("UPDATE materials SET name = :name WHERE id = :id")
    suspend fun renameMaterial(id: Long, name: String)

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
