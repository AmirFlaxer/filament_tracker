package com.amir.filamenttracker.presentation.ams

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.scan.LabelScanner
import com.amir.filamenttracker.domain.SpoolPlanner
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.scan.LabelScanFlow
import com.amir.filamenttracker.presentation.scan.ScanUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class AmsUiState(
    val slots: List<SpoolRow?> = List(SpoolPlanner.SLOT_COUNT) { null },
    val candidates: List<SpoolRow> = emptyList(),
)

@HiltViewModel
class AmsViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    private val catalog: CatalogRepository,
    private val scanner: LabelScanner,
    @ApplicationContext private val context: Context,
) : BaseViewModel() {
    val state: StateFlow<AmsUiState> = inventory.spools.map { spools ->
        AmsUiState(
            slots = (1..SpoolPlanner.SLOT_COUNT).map { slot ->
                spools.firstOrNull { it.status == SpoolStatus.IN_USE && it.amsSlot == slot }
            },
            candidates = slotCandidates(spools),
        )
    }.asState(AmsUiState())

    private val scanFlow = LabelScanFlow(scanner, catalog, context, viewModelScope)
    val scan: StateFlow<ScanUiState> = scanFlow.state

    fun load(spoolId: Long, slot: Int) = launchAction { inventory.loadIntoSlot(spoolId, slot); null }
    fun unload(spoolId: Long) = launchAction { inventory.unload(spoolId); "הסליל הוצא לאחסון" }
    fun finish(spoolId: Long) = launchAction { inventory.finish(spoolId); "הסליל סומן שהסתיים" }

    fun onPhoto(uri: Uri?) = scanFlow.onPhoto(uri) { slotCandidates(inventory.spools.first()) }

    fun clearScan() = scanFlow.clear()

    /** Loads the spool, then remembers the scanned barcode (if any) for its product. */
    fun confirmLoad(spoolId: Long, slot: Int) {
        val spool = state.value.candidates.firstOrNull { it.spoolId == spoolId }
        val barcode = scanFlow.barcodeToSave(spool)
        clearScan()
        launchAction {
            inventory.loadIntoSlot(spoolId, slot)
            if (barcode != null && spool != null) catalog.rememberBarcode(barcode, spool.productId)
            null
        }
    }

    fun retagAndLoad(spoolId: Long, brand: String, slot: Int) {
        val barcode = scanFlow.currentBarcode
        clearScan()
        launchAction {
            val productId = inventory.retagSpool(spoolId, brand)
            inventory.loadIntoSlot(spoolId, slot)
            if (barcode != null) catalog.rememberBarcode(barcode, productId)
            null
        }
    }
}

/** The spools that can go into a slot: open ones first (they are the ones that come back to the printer), then sealed. */
fun slotCandidates(spools: List<SpoolRow>): List<SpoolRow> = spools
    .filter { it.status == SpoolStatus.OPEN_STORED || it.status == SpoolStatus.SEALED }
    .sortedWith(
        compareBy<SpoolRow> { if (it.status == SpoolStatus.OPEN_STORED) 0 else 1 }
            .thenBy { it.materialName }
            .thenBy { it.colorName },
    )
