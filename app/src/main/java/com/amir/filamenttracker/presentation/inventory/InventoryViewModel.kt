package com.amir.filamenttracker.presentation.inventory

import androidx.lifecycle.viewModelScope
import com.amir.filamenttracker.data.AlertRow
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.InventoryViewPreference
import com.amir.filamenttracker.data.StockRepository
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.model.InventoryView
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [view] is null until the saved choice has been read, so the list never flashes the wrong view. */
data class InventoryUiState(
    val loaded: Boolean = false,
    val alerts: List<AlertRow> = emptyList(),
    val view: InventoryView? = null,
    val spools: List<SpoolRow> = emptyList(),
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    inventory: InventoryRepository,
    stock: StockRepository,
    private val preference: InventoryViewPreference,
) : BaseViewModel() {
    val state: StateFlow<InventoryUiState> =
        combine(inventory.spools, stock.alerts, preference.view) { spools, alerts, view ->
            InventoryUiState(loaded = true, alerts = alerts, view = view, spools = spools)
        }.asState(InventoryUiState())

    fun select(view: InventoryView) {
        viewModelScope.launch { preference.set(view) }
    }
}
