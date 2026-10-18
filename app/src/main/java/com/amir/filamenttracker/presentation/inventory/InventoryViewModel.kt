package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.AlertRow
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.StockRepository
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class ColorGroup(
    val materialId: Long,
    val colorId: Long,
    val colorName: String,
    val colorHex: String?,
    val counts: Map<SpoolStatus, Int>,
)

data class MaterialSection(val materialName: String, val colors: List<ColorGroup>)

data class InventoryUiState(
    val loaded: Boolean = false,
    val alerts: List<AlertRow> = emptyList(),
    val sections: List<MaterialSection> = emptyList(),
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    inventory: InventoryRepository,
    stock: StockRepository,
) : BaseViewModel() {
    val state: StateFlow<InventoryUiState> =
        combine(inventory.spools, stock.alerts) { spools, alerts ->
            InventoryUiState(loaded = true, alerts = alerts, sections = groupSpools(spools))
        }.asState(InventoryUiState())
}

/** Finished spools are history, not stock. Ordered ones stay, shown as "on the way". */
internal fun groupSpools(spools: List<SpoolRow>): List<MaterialSection> =
    spools.filter { it.status != SpoolStatus.FINISHED }
        .groupBy { it.materialName }
        .toSortedMap()
        .map { (material, rows) ->
            MaterialSection(
                materialName = material,
                colors = rows.groupBy { it.colorId }.values.map { group ->
                    val first = group.first()
                    ColorGroup(
                        materialId = first.materialId,
                        colorId = first.colorId,
                        colorName = first.colorName,
                        colorHex = first.colorHex,
                        counts = group.groupingBy { it.status }.eachCount(),
                    )
                }.sortedBy { it.colorName },
            )
        }
