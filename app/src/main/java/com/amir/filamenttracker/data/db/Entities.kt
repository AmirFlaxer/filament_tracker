package com.amir.filamenttracker.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amir.filamenttracker.domain.model.SpoolStatus
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "materials", indices = [Index(value = ["name"], unique = true)])
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val family: String,
    val variant: String = "",
    val archived: Boolean = false,
)

@Entity(tableName = "colors", indices = [Index(value = ["name"], unique = true)])
data class ColorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hex: String? = null,
    val archived: Boolean = false,
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(entity = MaterialEntity::class, parentColumns = ["id"], childColumns = ["materialId"]),
        ForeignKey(entity = ColorEntity::class, parentColumns = ["id"], childColumns = ["colorId"]),
    ],
    indices = [Index("materialId"), Index("colorId")],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val materialId: Long,
    val colorId: Long,
    val weightGrams: Int = 1000,
    val note: String = "",
    val archived: Boolean = false,
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendor: String = "Amazon",
    val orderedAt: Instant,
    val expectedAt: LocalDate? = null,
    val orderNumber: String? = null,
)

@Entity(
    tableName = "spools",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"]),
        ForeignKey(
            entity = OrderEntity::class, parentColumns = ["id"], childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // SQLite lets a unique index hold many NULLs, so this enforces one spool per slot
    // only for spools that are actually in a slot.
    indices = [Index("productId"), Index("orderId"), Index(value = ["amsSlot"], unique = true)],
)
data class SpoolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val orderId: Long?,
    val status: SpoolStatus,
    val amsSlot: Int?,
)

@Entity(
    tableName = "spool_events",
    foreignKeys = [
        ForeignKey(
            entity = SpoolEntity::class, parentColumns = ["id"], childColumns = ["spoolId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("spoolId")],
)
data class SpoolEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spoolId: Long,
    val fromStatus: SpoolStatus?,
    val toStatus: SpoolStatus,
    val fromSlot: Int?,
    val toSlot: Int?,
    val at: Instant,
)

@Entity(
    tableName = "stock_targets",
    primaryKeys = ["family", "colorId"],
    foreignKeys = [
        ForeignKey(
            entity = ColorEntity::class, parentColumns = ["id"], childColumns = ["colorId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("colorId")],
)
/**
 * A target is a main material (family) plus a color. [family] keeps the spelling of the first
 * material that used it; queries compare it with COLLATE NOCASE.
 */
data class StockTargetEntity(val family: String, val colorId: Long, val minSealed: Int = 1)
