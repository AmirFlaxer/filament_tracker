package com.amir.filamenttracker.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    // One row per family (ignoring case) and color. A pair appears once the user has an active
    // product for it, and a tracked pair stays listed even after its products are archived.
    // The displayed family is the target's own spelling, else the first material's.
    @Query(
        """
        SELECT COALESCE(t.family, f.family) AS family,
               c.id AS colorId, c.name AS colorName, c.hex AS colorHex, t.minSealed AS minSealed
        FROM (
            SELECT UPPER(m.family) AS fam, p.colorId AS colorId
            FROM products p
            JOIN materials m ON m.id = p.materialId
            JOIN colors c ON c.id = p.colorId
            WHERE p.archived = 0 AND m.archived = 0 AND c.archived = 0
            UNION
            SELECT UPPER(family) AS fam, colorId FROM stock_targets
        ) pairs
        JOIN colors c ON c.id = pairs.colorId
        LEFT JOIN (SELECT family AS family, UPPER(family) AS fam, MIN(id) AS firstId
                   FROM materials GROUP BY UPPER(family)) f ON f.fam = pairs.fam
        LEFT JOIN stock_targets t ON t.family = pairs.fam COLLATE NOCASE AND t.colorId = pairs.colorId
        ORDER BY pairs.fam, c.name
        """,
    )
    fun targetRows(): Flow<List<TargetRow>>

    @Upsert suspend fun upsert(target: StockTargetEntity)

    @Query("DELETE FROM stock_targets WHERE family = :family COLLATE NOCASE AND colorId = :colorId")
    suspend fun delete(family: String, colorId: Long)

    @Query("DELETE FROM stock_targets WHERE family = :family COLLATE NOCASE")
    suspend fun deleteForFamily(family: String)

    @Query("SELECT * FROM stock_targets WHERE family = :family COLLATE NOCASE")
    suspend fun targetsOfFamily(family: String): List<StockTargetEntity>

    @Query("DELETE FROM stock_targets WHERE colorId = :colorId")
    suspend fun deleteForColor(colorId: Long)
}
