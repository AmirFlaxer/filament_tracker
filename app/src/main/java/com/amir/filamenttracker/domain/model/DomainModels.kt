package com.amir.filamenttracker.domain.model

import java.time.Instant

/** The slice of a spool that the domain rules need. */
data class Spool(val id: Long, val status: SpoolStatus, val amsSlot: Int?)

data class SpoolEvent(
    val id: Long,
    val spoolId: Long,
    val fromStatus: SpoolStatus?,
    val toStatus: SpoolStatus,
    val fromSlot: Int?,
    val toSlot: Int?,
    val at: Instant,
)

/** One status change for one spool. A list of changes must be applied in order. */
data class SpoolChange(
    val spoolId: Long,
    val fromStatus: SpoolStatus?,
    val toStatus: SpoolStatus,
    val fromSlot: Int?,
    val toSlot: Int?,
)

/** Stock is counted per material+color, regardless of brand. */
data class StockKey(val materialId: Long, val colorId: Long)

data class StockTarget<K>(val key: K, val minSealed: Int)
