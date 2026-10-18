package com.amir.filamenttracker.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    // Pairs come from products, so a pair appears once the user has a product for it.
    // A tracked pair stays listed even after its products are archived.
    @Query(
        """
        SELECT DISTINCT m.id AS materialId, m.name AS materialName, c.id AS colorId,
               c.name AS colorName, c.hex AS colorHex, t.minSealed AS minSealed
        FROM products p
        JOIN materials m ON m.id = p.materialId
        JOIN colors c ON c.id = p.colorId
        LEFT JOIN stock_targets t ON t.materialId = p.materialId AND t.colorId = p.colorId
        WHERE (p.archived = 0 AND m.archived = 0 AND c.archived = 0) OR t.materialId IS NOT NULL
        ORDER BY m.name, c.name
        """,
    )
    fun targetRows(): Flow<List<TargetRow>>

    @Upsert suspend fun upsert(target: StockTargetEntity)

    @Query("DELETE FROM stock_targets WHERE materialId = :materialId AND colorId = :colorId")
    suspend fun delete(materialId: Long, colorId: Long)

    @Query("DELETE FROM stock_targets WHERE materialId = :materialId")
    suspend fun deleteForMaterial(materialId: Long)

    @Query("DELETE FROM stock_targets WHERE colorId = :colorId")
    suspend fun deleteForColor(colorId: Long)
}
