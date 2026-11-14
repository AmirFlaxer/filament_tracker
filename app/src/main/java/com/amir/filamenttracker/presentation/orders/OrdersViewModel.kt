package com.amir.filamenttracker.presentation.orders

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.OrderSummary
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.scan.LabelScanner
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.scan.ScanMatch
import com.amir.filamenttracker.presentation.scan.LabelScanFlow
import com.amir.filamenttracker.presentation.scan.ScanUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Spools that may be in the arriving package: ordered ones of non-archived orders, one per product
 * (from the oldest order), oldest order first.
 */
fun arrivalCandidates(spools: List<SpoolRow>, orders: List<OrderSummary>): List<SpoolRow> {
    val active = orders.filterNot { it.archived }.associateBy { it.id }
    return spools
        .filter { it.status == SpoolStatus.ORDERED && it.orderId in active }
        .sortedWith(
            compareBy<SpoolRow> { active.getValue(it.orderId!!).orderedAt }
                .thenBy { it.orderId }
                .thenBy { it.spoolId },
        )
        .distinctBy { it.productId }
}

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    private val catalog: CatalogRepository,
    private val scanner: LabelScanner,
    @ApplicationContext private val context: Context,
) : BaseViewModel() {
    /** Null until the first load, so empty sections really mean "no orders". */
    val sections: StateFlow<OrderSections?> = inventory.orders.map { orderSections(it) }.asState<OrderSections?>(null)

    /** Read on demand: nothing collects it, so a cached state flow would be empty. */
    private suspend fun candidates(): List<SpoolRow> =
        combine(inventory.spools, inventory.orders, ::arrivalCandidates).first()

    /** The order of each candidate spool, for the "vendor + number" line on the result card. */
    val orderById: StateFlow<Map<Long, OrderSummary>> =
        inventory.orders.map { list -> list.associateBy { it.id } }.asState(emptyMap())

    private val scanFlow = LabelScanFlow(scanner, catalog, context, viewModelScope)
    val scan: StateFlow<ScanUiState> = scanFlow.state

    fun archive(orderId: Long) = launchAction { inventory.setOrderArchived(orderId, true); "ההזמנה הועברה לארכיון" }
    fun restore(orderId: Long) = launchAction { inventory.setOrderArchived(orderId, false); "ההזמנה הוחזרה מהארכיון" }

    fun onPhoto(uri: Uri?) = scanFlow.onPhoto(uri, ::candidates)

    fun clearScan() = scanFlow.clear()

    /** Marks the spool arrived, then remembers the scanned barcode (if any) for its product. */
    fun confirmArrived(spoolId: Long) {
        val spool = ((scan.value as? ScanUiState.Result)?.match as? ScanMatch.Found)
            ?.spools?.firstOrNull { it.spoolId == spoolId }
        val barcode = scanFlow.barcodeToSave(spool)
        clearScan()
        launchAction {
            inventory.markArrived(listOf(spoolId))
            if (barcode != null && spool != null) {
                catalog.rememberBarcode(barcode, spool.productId)
            }
            "הסליל סומן שהגיע"
        }
    }
}
