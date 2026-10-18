package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.AlertRow
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.StockRepository
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class InventoryUiState(
    val loaded: Boolean = false,
    val alerts: List<AlertRow> = emptyList(),
    val sections: List<ListSection> = emptyList(),
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    inventory: InventoryRepository,
    stock: StockRepository,
) : BaseViewModel() {
    val state: StateFlow<InventoryUiState> =
        combine(inventory.spools, stock.alerts) { spools, alerts ->
            InventoryUiState(loaded = true, alerts = alerts, sections = byMaterial(spools))
        }.asState(InventoryUiState())
}
