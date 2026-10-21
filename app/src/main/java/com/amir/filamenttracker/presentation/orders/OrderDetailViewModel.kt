package com.amir.filamenttracker.presentation.orders

import androidx.lifecycle.SavedStateHandle
import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.common.NewProductInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
) : BaseViewModel() {
    private val orderId: Long = checkNotNull(handle.get<Long>("orderId"))

    val order = inventory.order(orderId).asState(null)
    val spools = inventory.orderSpools(orderId).asState(emptyList())

    val materials = catalog.materials.asState(emptyList())
    val colors = catalog.colors.asState(emptyList())
    val brands = catalog.brands.asState(emptyList())
    val products = catalog.products.asState(emptyList())

    fun arriveAll() = launchAction { inventory.markOrderArrived(orderId); "כל ההזמנה סומנה שהגיעה" }
    fun arrive(spoolId: Long) = launchAction { inventory.markArrived(listOf(spoolId)); null }
    fun removeSpool(spoolId: Long) = launchAction { inventory.removeOrderedSpool(spoolId); "הסליל הוסר מההזמנה" }

    fun addSpools(productId: Long, quantity: Int) = launchAction {
        inventory.addToOrder(orderId, productId, quantity)
        if (quantity == 1) "נוסף סליל להזמנה" else "נוספו $quantity סלילים להזמנה"
    }

    fun changeProduct(spoolId: Long, productId: Long) = launchAction {
        inventory.changeOrderedProduct(spoolId, productId)
        "המוצר הוחלף"
    }

    fun updateDetails(vendor: String, orderNumber: String, expectedAt: LocalDate?) = launchAction {
        inventory.updateOrder(orderId, vendor, orderNumber, expectedAt)
        "פרטי ההזמנה עודכנו"
    }

    /** Saves a product typed in from the picker, then hands its id on to whatever the picker was for. */
    fun createProduct(input: NewProductInput, then: (Long) -> Unit) = launchAction {
        then(catalog.createProduct(input.brand, input.materialFamily, input.materialVariant, input.colorName, input.colorHex, input.weightGrams, input.note))
        "המוצר נשמר"
    }

    fun deleteOrder(onDone: () -> Unit) = launchAction {
        inventory.deleteOrder(orderId)
        onDone()
        null
    }
}
