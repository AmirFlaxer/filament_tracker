package com.amir.filamenttracker.presentation.orders

import androidx.lifecycle.SavedStateHandle
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val inventory: InventoryRepository,
) : BaseViewModel() {
    private val orderId: Long = checkNotNull(handle.get<Long>("orderId"))

    val order = inventory.order(orderId).asState(null)
    val spools = inventory.orderSpools(orderId).asState(emptyList())

    fun arriveAll() = launchAction { inventory.markOrderArrived(orderId); "כל ההזמנה סומנה שהגיעה" }
    fun arrive(spoolId: Long) = launchAction { inventory.markArrived(listOf(spoolId)); null }
    fun removeSpool(spoolId: Long) = launchAction { inventory.removeOrderedSpool(spoolId); "הסליל הוסר מההזמנה" }

    fun deleteOrder(onDone: () -> Unit) = launchAction {
        inventory.deleteOrder(orderId)
        onDone()
        null
    }
}
