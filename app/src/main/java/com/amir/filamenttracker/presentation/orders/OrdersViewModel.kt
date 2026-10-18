package com.amir.filamenttracker.presentation.orders

import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.OrderSummary
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(inventory: InventoryRepository) : BaseViewModel() {
    /** Null until the first load, so an empty list really means "no orders". */
    val orders: StateFlow<List<OrderSummary>?> = inventory.orders.asState<List<OrderSummary>?>(null)
}
