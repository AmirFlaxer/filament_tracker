package com.amir.filamenttracker.presentation.orders

import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(private val inventory: InventoryRepository) : BaseViewModel() {
    /** Null until the first load, so empty sections really mean "no orders". */
    val sections: StateFlow<OrderSections?> = inventory.orders.map { orderSections(it) }.asState<OrderSections?>(null)

    fun archive(orderId: Long) = launchAction { inventory.setOrderArchived(orderId, true); "ההזמנה הועברה לארכיון" }
    fun restore(orderId: Long) = launchAction { inventory.setOrderArchived(orderId, false); "ההזמנה הוחזרה מהארכיון" }
}
