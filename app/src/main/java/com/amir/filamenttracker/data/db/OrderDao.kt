package com.amir.filamenttracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert suspend fun insertOrder(order: OrderEntity): Long

    @Query(
        """
        SELECT o.id AS id, o.vendor AS vendor, o.orderedAt AS orderedAt, o.expectedAt AS expectedAt,
               o.orderNumber AS orderNumber, o.archived AS archived,
               COUNT(s.id) AS spoolCount,
               COALESCE(SUM(CASE WHEN s.status = 'ORDERED' THEN 1 ELSE 0 END), 0) AS pendingCount
        FROM orders o
        LEFT JOIN spools s ON s.orderId = o.id
        GROUP BY o.id
        ORDER BY o.orderedAt DESC
        """,
    )
    fun orderSummaries(): Flow<List<OrderSummary>>

    @Query("SELECT * FROM orders WHERE id = :id")
    fun order(id: Long): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun orderOnce(id: Long): OrderEntity?

    @Update suspend fun updateOrder(order: OrderEntity)

    @Query("$SPOOL_ROW_SELECT WHERE s.orderId = :orderId ORDER BY m.name, c.name, s.id")
    fun orderSpools(orderId: Long): Flow<List<SpoolRow>>

    @Query("UPDATE orders SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("SELECT orderNumber FROM orders WHERE orderNumber IS NOT NULL AND orderNumber <> ''")
    suspend fun allOrderNumbers(): List<String>

    @Query(
        """
        SELECT * FROM orders o
        WHERE o.archived = 0 AND o.trackingNumber IS NULL
          AND EXISTS (SELECT 1 FROM spools s WHERE s.orderId = o.id AND s.status = 'ORDERED')
        """,
    )
    suspend fun pendingOrdersWithoutTracking(): List<OrderEntity>

    /** The automatic fill: writes only while no tracking number exists, so a manual entry is never replaced. */
    @Query("UPDATE orders SET trackingNumber = :trackingNumber WHERE id = :id AND trackingNumber IS NULL")
    suspend fun fillTrackingNumber(id: Long, trackingNumber: String)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrder(id: Long)
}
