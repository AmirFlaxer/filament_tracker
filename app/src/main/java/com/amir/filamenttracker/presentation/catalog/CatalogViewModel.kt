package com.amir.filamenttracker.presentation.catalog

import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.RemoveResult
import com.amir.filamenttracker.domain.materialKind
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.common.NewProductInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(private val catalog: CatalogRepository) : BaseViewModel() {
    val materials = catalog.materials.asState(emptyList())
    val colors = catalog.colors.asState(emptyList())
    val brands = catalog.brands.asState(emptyList())
    val products = catalog.products.asState(emptyList())

    fun addMaterial(name: String) = launchAction {
        val kind = materialKind(name)
        catalog.addMaterial(kind.family, kind.variant.orEmpty())
        "החומר נשמר"
    }

    fun addColor(name: String, hex: String) = launchAction { catalog.addColor(name, hex); "הצבע נשמר" }

    fun createProduct(input: NewProductInput) = launchAction {
        catalog.createProduct(input.brand, input.materialKind.family, input.materialKind.variant.orEmpty(), input.colorName, input.colorHex, input.weightGrams, input.note)
        "המוצר נשמר"
    }

    fun renameMaterial(id: Long, name: String) = launchAction {
        val kind = materialKind(name)
        catalog.updateMaterial(id, kind.family, kind.variant.orEmpty())
        null
    }

    fun updateColor(id: Long, name: String, hex: String) = launchAction { catalog.updateColor(id, name, hex); null }

    fun updateProduct(id: Long, brand: String, weight: String, note: String) = launchAction {
        val grams = weight.trim().toIntOrNull() ?: throw IllegalArgumentException("משקל לא תקין")
        catalog.updateProduct(id, brand, grams, note)
        null
    }

    fun removeMaterial(id: Long) = launchAction { catalog.removeMaterial(id).message() }
    fun removeColor(id: Long) = launchAction { catalog.removeColor(id).message() }
    fun removeProduct(id: Long) = launchAction { catalog.removeProduct(id).message() }

    private fun RemoveResult.message() = when (this) {
        RemoveResult.DELETED -> "נמחק"
        RemoveResult.ARCHIVED -> "הועבר לארכיון, כי יש בו שימוש"
    }
}
