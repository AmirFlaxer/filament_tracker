package com.amir.filamenttracker.presentation.orders

import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.common.NewProductInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class NewOrderViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
) : BaseViewModel() {
    val materials = catalog.materials.asState(emptyList())
    val colors = catalog.colors.asState(emptyList())
    val brands = catalog.brands.asState(emptyList())
    val products = catalog.products.asState(emptyList())

    // Product id to quantity. Map + keeps insertion order, so lines stay in the order added.
    private val quantities = MutableStateFlow<Map<Long, Int>>(emptyMap())

    val lines: StateFlow<List<Pair<ProductRow, Int>>> = combine(quantities, catalog.products) { q, list ->
        q.mapNotNull { (id, n) -> list.firstOrNull { it.productId == id }?.let { it to n } }
    }.asState(emptyList())

    fun add(productId: Long) = quantities.update { it + (productId to ((it[productId] ?: 0) + 1)) }

    fun setQuantity(productId: Long, n: Int) =
        quantities.update { if (n <= 0) it - productId else it + (productId to n) }

    fun createProductAndAdd(input: NewProductInput) = launchAction {
        add(catalog.createProduct(input.brand, input.materialName, input.colorName, input.colorHex, input.weightGrams, input.note))
        "המוצר נשמר"
    }

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    /** Ignores re-entry while a save runs, so a double tap cannot create two orders. */
    fun save(vendor: String, orderNumber: String, expectedAt: LocalDate?, onDone: () -> Unit) {
        if (!_saving.compareAndSet(false, true)) return
        launchAction {
            var saved = false
            try {
                inventory.createOrder(vendor, orderNumber, expectedAt, quantities.value)
                saved = true
                onDone()
                null
            } finally {
                if (!saved) _saving.value = false // after success the screen closes; stay locked
            }
        }
    }
}
