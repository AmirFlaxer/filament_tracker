package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.common.NewProductInput
import com.amir.filamenttracker.presentation.common.countLabel
import com.amir.filamenttracker.presentation.common.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Confirmation line shown after a successful add. */
fun addedText(title: String, count: Int, status: SpoolStatus, slot: Int?): String =
    if (count == 1) {
        "נוסף: $title, " + if (status == SpoolStatus.IN_USE && slot != null) "בחריץ $slot" else status.label()
    } else {
        "נוספו $count: $title, ${countLabel(status, count)}"
    }

@HiltViewModel
class AddExistingViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
) : BaseViewModel() {
    val materials = catalog.materials.asState(emptyList())
    val colors = catalog.colors.asState(emptyList())
    val products = catalog.products.asState(emptyList())

    private val selectedId = MutableStateFlow<Long?>(null)
    val selected: StateFlow<ProductRow?> =
        combine(selectedId, catalog.products) { id, list -> list.firstOrNull { it.productId == id } }.asState(null)

    /** Slot number to the title of the spool in it, to warn before displacing it. */
    val occupants: StateFlow<Map<Int, String>> = inventory.spools.map { rows ->
        rows.filter { it.status == SpoolStatus.IN_USE && it.amsSlot != null }.associate { it.amsSlot!! to it.title }
    }.asState(emptyMap())

    private val _lastAdded = MutableStateFlow<String?>(null)
    val lastAdded: StateFlow<String?> = _lastAdded

    /** Single place that changes the selection; the confirmation card only describes the product it was added for. */
    private fun setSelected(productId: Long) {
        if (selectedId.value != productId) _lastAdded.value = null
        selectedId.value = productId
    }

    fun select(productId: Long) = setSelected(productId)

    fun createProduct(input: NewProductInput) = launchAction {
        setSelected(catalog.createProduct(
            input.brand, input.materialName, input.colorName, input.colorHex, input.weightGrams, input.note,
        ))
        "המוצר נשמר"
    }

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding

    /** Ignores taps while an add is running, so a double tap cannot create ghost spools. */
    fun add(quantity: Int, status: SpoolStatus, slot: Int?, onSuccess: () -> Unit) {
        if (!_adding.compareAndSet(false, true)) return
        launchAction {
            try {
                // Capture before the suspend call: the text must describe the product actually added.
                val product = selected.value ?: throw IllegalArgumentException("יש לבחור מוצר")
                val ids = inventory.addExisting(product.productId, quantity, status, slot)
                onSuccess()
                _lastAdded.value = addedText(product.title, ids.size, status, slot)
                null
            } finally {
                _adding.value = false
            }
        }
    }
}
