package com.amir.filamenttracker.presentation.ams

import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.SpoolPlanner
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class AmsUiState(
    val slots: List<SpoolRow?> = List(SpoolPlanner.SLOT_COUNT) { null },
    val candidates: List<SpoolRow> = emptyList(),
)

@HiltViewModel
class AmsViewModel @Inject constructor(private val inventory: InventoryRepository) : BaseViewModel() {
    val state: StateFlow<AmsUiState> = inventory.spools.map { spools ->
        AmsUiState(
            slots = (1..SpoolPlanner.SLOT_COUNT).map { slot ->
                spools.firstOrNull { it.status == SpoolStatus.IN_USE && it.amsSlot == slot }
            },
            // Open spools first: they are the ones that come back to the printer.
            candidates = spools
                .filter { it.status == SpoolStatus.OPEN_STORED || it.status == SpoolStatus.SEALED }
                .sortedWith(
                    compareBy<SpoolRow> { if (it.status == SpoolStatus.OPEN_STORED) 0 else 1 }
                        .thenBy { it.materialName }
                        .thenBy { it.colorName },
                ),
        )
    }.asState(AmsUiState())

    fun load(spoolId: Long, slot: Int) = launchAction { inventory.loadIntoSlot(spoolId, slot); null }
    fun unload(spoolId: Long) = launchAction { inventory.unload(spoolId); "הסליל הוצא לאחסון" }
    fun finish(spoolId: Long) = launchAction { inventory.finish(spoolId); "הסליל סומן שהסתיים" }
}
