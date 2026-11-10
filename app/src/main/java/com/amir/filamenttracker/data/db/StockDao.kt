package com.amir.filamenttracker.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    // One row per stock line (family and finish, ignoring case) and color. A pair appears once the
    // user has an active product for it, and a tracked pair stays listed even after its products are
    // archived. The displayed spelling is the target's own, else the first material's.
    @Query(
        """
        SELECT COALESCE(t.family, f.family) AS family, COALESCE(t.finish, f.finish) AS finish,
               c.id AS colorId, c.name AS colorName, c.hex AS colorHex, t.minSealed AS minSealed
        FROM (
            SELECT UPPER(m.family) AS fam, UPPER(m.finish) AS fin, p.colorId AS colorId
            FROM products p
            JOIN materials m ON m.id = p.materialId
            JOIN colors c ON c.id = p.colorId
            WHERE p.archived = 0 AND m.archived = 0 AND c.archived = 0
            UNION
            SELECT UPPER(family) AS fam, UPPER(finish) AS fin, colorId FROM stock_targets
        ) pairs
        JOIN colors c ON c.id = pairs.colorId
        LEFT JOIN (SELECT family AS family, finish AS finish, UPPER(family) AS fam, UPPER(finish) AS fin, MIN(id) AS firstId
                   FROM materials GROUP BY UPPER(family), UPPER(finish)) f ON f.fam = pairs.fam AND f.fin = pairs.fin
        LEFT JOIN stock_targets t ON t.family = pairs.fam COLLATE NOCASE AND t.finish = pairs.fin COLLATE NOCASE
                                  AND t.colorId = pairs.colorId
        ORDER BY pairs.fam, pairs.fin, c.name
        """,
    )
    fun targetRows(): Flow<List<TargetRow>>

    @Upsert suspend fun upsert(target: StockTargetEntity)

    @Query(
        "DELETE FROM stock_targets WHERE family = :family COLLATE NOCASE AND finish = :finish COLLATE NOCASE " +
            "AND colorId = :colorId",
    )
    suspend fun delete(family: String, finish: String, colorId: Long)

    @Query("DELETE FROM stock_targets WHERE family = :family COLLATE NOCASE AND finish = :finish COLLATE NOCASE")
    suspend fun deleteForLine(family: String, finish: String)

    @Query("SELECT * FROM stock_targets WHERE family = :family COLLATE NOCASE AND finish = :finish COLLATE NOCASE")
    suspend fun targetsOfLine(family: String, finish: String): List<StockTargetEntity>

    @Query("DELETE FROM stock_targets WHERE colorId = :colorId")
    suspend fun deleteForColor(colorId: Long)
}
