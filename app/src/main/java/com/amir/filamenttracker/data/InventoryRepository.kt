package com.amir.filamenttracker.data

import androidx.room.withTransaction
import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.OrderEntity
import com.amir.filamenttracker.data.db.OrderSummary
import com.amir.filamenttracker.data.db.SpoolEntity
import com.amir.filamenttracker.data.db.SpoolEventEntity
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.toDomain
import com.amir.filamenttracker.domain.SpoolPlanner
import com.amir.filamenttracker.domain.SpoolRuleException
import com.amir.filamenttracker.domain.UndoNotPossibleException
import com.amir.filamenttracker.domain.model.Spool
import com.amir.filamenttracker.domain.model.SpoolChange
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val spoolDao = db.spoolDao()
    private val orderDao = db.orderDao()

    val spools: Flow<List<SpoolRow>> = spoolDao.spoolRows()
    val orders: Flow<List<OrderSummary>> = orderDao.orderSummaries()

    fun spool(id: Long): Flow<SpoolRow?> = spoolDao.spoolRow(id)
    fun events(spoolId: Long): Flow<List<SpoolEventEntity>> = spoolDao.events(spoolId)
    fun order(id: Long): Flow<OrderEntity?> = orderDao.order(id)
    fun orderSpools(orderId: Long): Flow<List<SpoolRow>> = orderDao.orderSpools(orderId)

    /** Stock that existed before the app: no order, created directly in [status]. */
    suspend fun addExisting(productId: Long, quantity: Int, status: SpoolStatus, slot: Int?): List<Long> =
        db.withTransaction {
            require(status == SEALED || status == IN_USE || status == OPEN_STORED) {
                "מלאי קיים נקלט כסגור, בשימוש או פתוח באחסון"
            }
            if (status == IN_USE) {
                require(quantity == 1) { "סליל בשימוש נקלט אחד בכל פעם" }
                require(slot != null && slot in 1..SpoolPlanner.SLOT_COUNT) { "יש לבחור חריץ" }
                SpoolPlanner.vacate(slot, inUse())?.let { apply(listOf(it)) }
            } else {
                require(quantity in 1..50) { "כמות חייבת להיות בין 1 ל-50" }
            }
            val storedSlot = if (status == IN_USE) slot else null
            (1..quantity).map { insertWithEvent(productId, orderId = null, status = status, slot = storedSlot) }
        }

    suspend fun createOrder(
        vendor: String,
        orderNumber: String?,
        expectedAt: LocalDate?,
        items: Map<Long, Int>,
    ): Long = db.withTransaction {
        val lines = items.filterValues { it > 0 }
        require(lines.isNotEmpty()) { "הזמנה חייבת לכלול לפחות סליל אחד" }
        val orderId = orderDao.insertOrder(
            OrderEntity(
                vendor = vendor.trim().ifEmpty { "Amazon" },
                orderedAt = clock.instant(),
                expectedAt = expectedAt,
                orderNumber = orderNumber?.trim()?.ifEmpty { null },
            ),
        )
        lines.forEach { (productId, quantity) ->
            repeat(quantity) { insertWithEvent(productId, orderId, ORDERED, slot = null) }
        }
        orderId
    }

    /** Fixes a miscount: the new spools join as not yet arrived, even if the rest already did. */
    suspend fun addToOrder(orderId: Long, productId: Long, quantity: Int): List<Long> = db.withTransaction {
        require(quantity in 1..50) { "כמות חייבת להיות בין 1 ל-50" }
        orderDao.orderOnce(orderId) ?: throw SpoolRuleException("ההזמנה לא נמצאה")
        (1..quantity).map { insertWithEvent(productId, orderId, ORDERED, slot = null) }
    }

    suspend fun updateOrder(orderId: Long, vendor: String, orderNumber: String?, expectedAt: LocalDate?) =
        db.withTransaction {
            val order = orderDao.orderOnce(orderId) ?: throw SpoolRuleException("ההזמנה לא נמצאה")
            orderDao.updateOrder(
                order.copy(
                    vendor = vendor.trim().ifEmpty { "Amazon" },
                    orderNumber = orderNumber?.trim()?.ifEmpty { null },
                    expectedAt = expectedAt,
                ),
            )
        }

    /** A wrong product typed into an order. A correction, not a status change, so no event is logged. */
    suspend fun changeOrderedProduct(spoolId: Long, productId: Long) = db.withTransaction {
        if (spoolOrThrow(spoolId).status != ORDERED) {
            throw SpoolRuleException("אפשר להחליף מוצר רק לסליל שעדיין לא הגיע")
        }
        spoolDao.updateProduct(spoolId, productId)
    }

    suspend fun markArrived(spoolIds: Collection<Long>) = db.withTransaction {
        apply(spoolIds.map { SpoolPlanner.move(spoolOrThrow(it), SEALED) })
    }

    suspend fun markOrderArrived(orderId: Long) = db.withTransaction {
        val pending = spoolDao.spoolsOfOrder(orderId).filter { it.status == ORDERED }
        apply(pending.map { SpoolPlanner.move(it.toDomain(), SEALED) })
    }

    /** A spool that will not arrive (partial delivery). Spec: no "cancelled" status, just delete. */
    suspend fun removeOrderedSpool(spoolId: Long) = db.withTransaction {
        if (spoolOrThrow(spoolId).status != ORDERED) {
            throw SpoolRuleException("אפשר למחוק רק סליל שעדיין לא הגיע")
        }
        spoolDao.deleteSpool(spoolId)
    }

    /** Deletes any spool whatever its status or history; its events cascade and its slot is freed with it. */
    suspend fun deleteSpool(spoolId: Long) = db.withTransaction {
        spoolDao.spool(spoolId) ?: throw SpoolRuleException("הסליל לא נמצא")
        spoolDao.deleteSpool(spoolId) // events cascade
    }

    suspend fun deleteOrder(orderId: Long) = db.withTransaction {
        if (spoolDao.spoolsOfOrder(orderId).any { it.status != ORDERED }) {
            throw SpoolRuleException("אפשר למחוק רק הזמנה שאף סליל ממנה עוד לא הגיע")
        }
        orderDao.deleteOrder(orderId) // spools and their events cascade
    }

    suspend fun loadIntoSlot(spoolId: Long, slot: Int) = db.withTransaction {
        apply(SpoolPlanner.load(spoolOrThrow(spoolId), slot, inUse()))
    }

    suspend fun unload(spoolId: Long) = db.withTransaction {
        apply(listOf(SpoolPlanner.move(spoolOrThrow(spoolId), OPEN_STORED)))
    }

    suspend fun finish(spoolId: Long) = db.withTransaction {
        apply(listOf(SpoolPlanner.move(spoolOrThrow(spoolId), FINISHED)))
    }

    /** Reverts the newest event of one spool and deletes it, so a second undo goes one step further. */
    suspend fun undoLast(spoolId: Long) = db.withTransaction {
        val last = spoolDao.lastEvent(spoolId)?.toDomain()
            ?: throw UndoNotPossibleException("אין פעולות לבטל")
        val change = SpoolPlanner.undo(spoolOrThrow(spoolId), last, inUse())
        spoolDao.updateState(change.spoolId, change.toStatus, change.toSlot)
        spoolDao.deleteEvent(last.id)
    }

    private suspend fun spoolOrThrow(id: Long): Spool =
        spoolDao.spool(id)?.toDomain() ?: throw SpoolRuleException("הסליל לא נמצא")

    private suspend fun inUse(): List<Spool> = spoolDao.inUse().map { it.toDomain() }

    private suspend fun insertWithEvent(productId: Long, orderId: Long?, status: SpoolStatus, slot: Int?): Long {
        val id = spoolDao.insertSpool(
            SpoolEntity(productId = productId, orderId = orderId, status = status, amsSlot = slot),
        )
        spoolDao.insertEvent(
            SpoolEventEntity(
                spoolId = id, fromStatus = null, toStatus = status,
                fromSlot = null, toSlot = slot, at = clock.instant(),
            ),
        )
        return id
    }

    // Order matters: a vacated slot must be cleared before another spool takes it (unique index).
    private suspend fun apply(changes: List<SpoolChange>) {
        val now = clock.instant()
        changes.forEach { c ->
            spoolDao.updateState(c.spoolId, c.toStatus, c.toSlot)
            spoolDao.insertEvent(
                SpoolEventEntity(
                    spoolId = c.spoolId, fromStatus = c.fromStatus, toStatus = c.toStatus,
                    fromSlot = c.fromSlot, toSlot = c.toSlot, at = now,
                ),
            )
        }
    }
}
