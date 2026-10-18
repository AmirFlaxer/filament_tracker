package com.amir.filamenttracker.presentation.inventory

import androidx.lifecycle.SavedStateHandle
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class ColorSpoolsViewModel @Inject constructor(
    handle: SavedStateHandle,
    inventory: InventoryRepository,
) : BaseViewModel() {
    private val materialId: Long = checkNotNull(handle.get<Long>("materialId"))
    private val colorId: Long = checkNotNull(handle.get<Long>("colorId"))

    // Finished spools go last: they are history, kept for reference.
    val spools = inventory.spools.map { rows ->
        rows.filter { it.materialId == materialId && it.colorId == colorId }
            .sortedBy { it.status == SpoolStatus.FINISHED }
    }.asState(emptyList())
}
