package com.amir.filamenttracker.data.db

import com.amir.filamenttracker.domain.model.SpoolStatus
import java.time.Instant
import java.time.LocalDate

/** A spool joined with its product, material and color names. */
data class SpoolRow(
    val spoolId: Long,
    val status: SpoolStatus,
    val amsSlot: Int?,
    val orderId: Long?,
    val productId: Long,
    val brand: String,
    val weightGrams: Int,
    val materialId: Long,
    val materialName: String,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
)

data class ProductRow(
    val productId: Long,
    val brand: String,
    val weightGrams: Int,
    val note: String,
    val materialId: Long,
    val materialName: String,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
)

data class OrderSummary(
    val id: Long,
    val vendor: String,
    val orderedAt: Instant,
    val expectedAt: LocalDate?,
    val orderNumber: String?,
    val spoolCount: Int,
    val pendingCount: Int,
)

/** A material+color pair the user has products for. [minSealed] is null when not tracked. */
data class TargetRow(
    val materialId: Long,
    val materialName: String,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
    val minSealed: Int?,
)

data class FinishRow(
    val materialId: Long,
    val materialName: String,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
    val at: Instant,
)

// Extensions, not members: Room would try to map a member property to a column.
val SpoolRow.title: String get() = "$brand $materialName $colorName"
val ProductRow.title: String get() = "$brand $materialName $colorName"

internal const val SPOOL_ROW_SELECT = """
    SELECT s.id AS spoolId, s.status AS status, s.amsSlot AS amsSlot, s.orderId AS orderId,
           p.id AS productId, p.brand AS brand, p.weightGrams AS weightGrams,
           m.id AS materialId, m.name AS materialName,
           c.id AS colorId, c.name AS colorName, c.hex AS colorHex
    FROM spools s
    JOIN products p ON p.id = s.productId
    JOIN materials m ON m.id = p.materialId
    JOIN colors c ON c.id = p.colorId
"""
