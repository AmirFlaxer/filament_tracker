package com.amir.filamenttracker.presentation.inventory

import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.common.NewProductInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

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

    fun select(productId: Long) { selectedId.value = productId }

    fun createProduct(input: NewProductInput) = launchAction {
        selectedId.value = catalog.createProduct(
            input.brand, input.materialName, input.colorName, input.colorHex, input.weightGrams, input.note,
        )
        "המוצר נשמר"
    }

    fun add(quantity: Int, status: SpoolStatus, slot: Int?) = launchAction {
        val productId = selectedId.value ?: throw IllegalArgumentException("יש לבחור מוצר")
        val ids = inventory.addExisting(productId, quantity, status, slot)
        if (ids.size == 1) "נוסף סליל אחד" else "נוספו ${ids.size} סלילים"
    }
}
