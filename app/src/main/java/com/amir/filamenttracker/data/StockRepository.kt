package com.amir.filamenttracker.data

import com.amir.filamenttracker.data.db.AppDatabase
import com.amir.filamenttracker.data.db.FinishRow
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.StockTargetEntity
import com.amir.filamenttracker.data.db.TargetRow
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.domain.StockCalculator
import com.amir.filamenttracker.domain.StockCount
import com.amir.filamenttracker.domain.model.StockKey
import com.amir.filamenttracker.domain.model.StockTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class AlertRow(
    val kind: AlertKind,
    val materialId: Long,
    val materialName: String,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
    val count: StockCount,
    val minSealed: Int,
)

@Singleton
class StockRepository @Inject constructor(db: AppDatabase) {
    private val stockDao = db.stockDao()
    private val spoolDao = db.spoolDao()

    val targetRows: Flow<List<TargetRow>> = stockDao.targetRows()

    val alerts: Flow<List<AlertRow>> =
        combine(spoolDao.spoolRows(), stockDao.targetRows()) { spools, rows -> buildAlerts(spools, rows) }

    val finishes: Flow<List<FinishRow>> = spoolDao.finishEvents()

    suspend fun setTarget(materialId: Long, colorId: Long, minSealed: Int) {
        require(minSealed in 0..20) { "מינימום חייב להיות בין 0 ל-20" }
        stockDao.upsert(StockTargetEntity(materialId, colorId, minSealed))
    }

    suspend fun removeTarget(materialId: Long, colorId: Long) = stockDao.delete(materialId, colorId)

    private fun buildAlerts(spools: List<SpoolRow>, rows: List<TargetRow>): List<AlertRow> {
        val counts = StockCalculator.count(spools.map { StockKey(it.materialId, it.colorId) to it.status })
        val tracked = rows.filter { it.minSealed != null }.associateBy { StockKey(it.materialId, it.colorId) }
        val targets = tracked.map { (key, row) -> StockTarget(key, row.minSealed!!) }
        return StockCalculator.alerts(counts, targets).map { alert ->
            val row = tracked.getValue(alert.key)
            AlertRow(
                kind = alert.kind, materialId = row.materialId, materialName = row.materialName,
                colorId = row.colorId, colorName = row.colorName, colorHex = row.colorHex,
                count = alert.count, minSealed = alert.minSealed,
            )
        }
    }
}
