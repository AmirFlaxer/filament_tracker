package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import com.amir.filamenttracker.domain.model.StockTarget

data class StockCount(val available: Int = 0, val sealed: Int = 0, val onTheWay: Int = 0)

/** Declaration order is display order. */
enum class AlertKind { MISSING, RUNNING_LOW, ON_THE_WAY }

data class StockAlert<K>(val key: K, val kind: AlertKind, val count: StockCount, val minSealed: Int)

/** Spec section 5. */
object StockCalculator {
    fun <K> count(spools: List<Pair<K, SpoolStatus>>): Map<K, StockCount> =
        spools.groupBy({ it.first }, { it.second }).mapValues { (_, statuses) ->
            StockCount(
                available = statuses.count { it == SEALED || it == IN_USE || it == OPEN_STORED },
                sealed = statuses.count { it == SEALED },
                onTheWay = statuses.count { it == ORDERED },
            )
        }

    fun <K> alerts(counts: Map<K, StockCount>, targets: List<StockTarget<K>>): List<StockAlert<K>> =
        targets.mapNotNull { target ->
            val c = counts[target.key] ?: StockCount()
            val kind = when {
                c.available == 0 ->
                    if (c.onTheWay > 0) AlertKind.ON_THE_WAY else AlertKind.MISSING
                c.sealed < target.minSealed ->
                    if (c.sealed + c.onTheWay >= target.minSealed) AlertKind.ON_THE_WAY else AlertKind.RUNNING_LOW
                else -> null
            }
            kind?.let { StockAlert(target.key, it, c, target.minSealed) }
        }.sortedBy { it.kind.ordinal }
}
