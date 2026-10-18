package com.amir.filamenttracker.presentation.consumption

import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.StockRepository
import com.amir.filamenttracker.data.db.FinishRow
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.ConsumptionForecast
import com.amir.filamenttracker.domain.StockCalculator
import com.amir.filamenttracker.domain.model.StockKey
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.common.formatInterval
import com.amir.filamenttracker.presentation.common.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

enum class ConsumptionRange(val label: String, val months: Long?) {
    THREE_MONTHS("3 חודשים", 3),
    SIX_MONTHS("6 חודשים", 6),
    YEAR("שנה", 12),
    ALL("הכל", null),
}

data class ConsumptionRowUi(
    val key: StockKey,
    val title: String,
    val colorHex: String?,
    val finishedInRange: Int,
    val rateText: String,
    val forecastText: String?,
)

@HiltViewModel
class ConsumptionViewModel @Inject constructor(
    stock: StockRepository,
    inventory: InventoryRepository,
    private val clock: Clock,
) : BaseViewModel() {
    private val range = MutableStateFlow(ConsumptionRange.SIX_MONTHS)
    val selectedRange: StateFlow<ConsumptionRange> = range.asStateFlow()

    /** Null until the first load. */
    val rows: StateFlow<List<ConsumptionRowUi>?> =
        combine(stock.finishes, inventory.spools, range) { finishes, spools, r ->
            buildRows(finishes, spools, r, clock.instant())
        }.asState<List<ConsumptionRowUi>?>(null)

    fun selectRange(r: ConsumptionRange) { range.value = r }
}

internal fun buildRows(
    finishes: List<FinishRow>,
    spools: List<SpoolRow>,
    range: ConsumptionRange,
    now: Instant,
): List<ConsumptionRowUi> {
    val from = range.months?.let { now.atZone(ZoneId.systemDefault()).minusMonths(it).toInstant() }
    val available = StockCalculator.count(spools.map { StockKey(it.materialId, it.colorId) to it.status })
    return finishes.groupBy { StockKey(it.materialId, it.colorId) }.map { (key, rows) ->
        val times = rows.map { it.at }
        val first = rows.first()
        ConsumptionRowUi(
            key = key,
            title = "${first.materialName} ${first.colorName}",
            colorHex = first.colorHex,
            finishedInRange = times.count { from == null || !it.isBefore(from) },
            // The rate uses the whole history: a short window would make it jumpy.
            rateText = ConsumptionForecast.averageInterval(times)?.formatInterval() ?: "אין מספיק היסטוריה לקצב",
            forecastText = ConsumptionForecast.forecast(times, available[key]?.available ?: 0, now).label(),
        )
    }.sortedWith(compareByDescending<ConsumptionRowUi> { it.finishedInRange }.thenBy { it.title })
}
