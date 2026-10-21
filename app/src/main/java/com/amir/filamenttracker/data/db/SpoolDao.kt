package com.amir.filamenttracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.amir.filamenttracker.domain.model.SpoolStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SpoolDao {
    @Query("$SPOOL_ROW_SELECT ORDER BY m.name, c.name, p.brand, s.id")
    fun spoolRows(): Flow<List<SpoolRow>>

    @Query("$SPOOL_ROW_SELECT WHERE s.id = :id")
    fun spoolRow(id: Long): Flow<SpoolRow?>

    @Query("SELECT * FROM spools WHERE id = :id")
    suspend fun spool(id: Long): SpoolEntity?

    @Query("SELECT * FROM spools WHERE status = 'IN_USE'")
    suspend fun inUse(): List<SpoolEntity>

    @Query("SELECT * FROM spools WHERE orderId = :orderId")
    suspend fun spoolsOfOrder(orderId: Long): List<SpoolEntity>

    @Insert suspend fun insertSpool(spool: SpoolEntity): Long

    @Query("UPDATE spools SET status = :status, amsSlot = :slot WHERE id = :id")
    suspend fun updateState(id: Long, status: SpoolStatus, slot: Int?)

    @Query("UPDATE spools SET productId = :productId WHERE id = :id")
    suspend fun updateProduct(id: Long, productId: Long)

    @Query("DELETE FROM spools WHERE id = :id")
    suspend fun deleteSpool(id: Long)

    @Insert suspend fun insertEvent(event: SpoolEventEntity): Long

    // Order by id (autoincrement), not by time: a clock change must not make an older event look newest.
    @Query("SELECT * FROM spool_events WHERE spoolId = :spoolId ORDER BY id DESC LIMIT 1")
    suspend fun lastEvent(spoolId: Long): SpoolEventEntity?

    @Query("SELECT * FROM spool_events WHERE spoolId = :spoolId ORDER BY id DESC")
    fun events(spoolId: Long): Flow<List<SpoolEventEntity>>

    @Query("DELETE FROM spool_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query(
        """
        SELECT m.id AS materialId, m.name AS materialName, c.id AS colorId, c.name AS colorName,
               c.hex AS colorHex, e.at AS at
        FROM spool_events e
        JOIN spools s ON s.id = e.spoolId
        JOIN products p ON p.id = s.productId
        JOIN materials m ON m.id = p.materialId
        JOIN colors c ON c.id = p.colorId
        WHERE e.toStatus = 'FINISHED'
        ORDER BY e.at
        """,
    )
    fun finishEvents(): Flow<List<FinishRow>>
}
