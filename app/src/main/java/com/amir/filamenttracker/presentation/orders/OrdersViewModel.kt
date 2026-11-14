package com.amir.filamenttracker.presentation.orders

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.OrderSummary
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.data.scan.LabelScanner
import com.amir.filamenttracker.domain.isGenericBrand
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.readLabel
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.scan.ScanMatch
import com.amir.filamenttracker.presentation.scan.ScanUiState
import com.amir.filamenttracker.presentation.scan.labelFile
import com.amir.filamenttracker.presentation.scan.matchScan
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Spools that may be in the arriving package: ordered ones of non-archived orders, one per product
 * (from the oldest order), oldest order first.
 */
fun arrivalCandidates(spools: List<SpoolRow>, orders: List<OrderSummary>): List<SpoolRow> {
    val active = orders.filterNot { it.archived }.associateBy { it.id }
    return spools
        .filter { it.status == SpoolStatus.ORDERED && it.orderId in active }
        .sortedBy { active.getValue(it.orderId!!).orderedAt }
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

    private val _scan = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scan: StateFlow<ScanUiState> = _scan
    private var scanJob: Job? = null

    fun archive(orderId: Long) = launchAction { inventory.setOrderArchived(orderId, true); "ההזמנה הועברה לארכיון" }
    fun restore(orderId: Long) = launchAction { inventory.setOrderArchived(orderId, false); "ההזמנה הוחזרה מהארכיון" }

    /** Reads the photo behind [uri] and shows the match; null (the user cancelled) goes back to idle. */
    fun onPhoto(uri: Uri?) {
        if (uri == null) {
            _scan.value = ScanUiState.Idle
            return
        }
        scanJob?.cancel()
        _scan.value = ScanUiState.Reading
        scanJob = viewModelScope.launch {
            _scan.value = try {
                analyze(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanUiState.Result(ScanMatch.None, null)
            } finally {
                // The photo is the user's data: it does not stay in the cache.
                withContext(NonCancellable + Dispatchers.IO) { labelFile(context).delete() }
            }
        }
    }

    private suspend fun analyze(uri: Uri): ScanUiState.Result {
        val result = scanner.scan(uri)
        val list = candidates()
        val reading = readLabel(
            text = result.text,
            knownBrands = catalog.brands.first().filterNot { isGenericBrand(it) },
            knownFamilies = (list.map { it.materialFamily } + catalog.materials.first().map { it.family }).distinct(),
            knownColors = (list.map { it.colorName } + catalog.colors.first().map { it.name }).distinct(),
        )
        val barcode = result.barcodes.singleOrNull()
        val match = matchScan(reading, barcode?.let { catalog.productForBarcode(it) }, list)
        val title = (match as? ScanMatch.KnownProductNoSpool)?.let { known ->
            catalog.products.first().firstOrNull { it.productId == known.productId }?.title
        }
        return ScanUiState.Result(match, barcode, title)
    }

    fun clearScan() {
        scanJob?.cancel()
        _scan.value = ScanUiState.Idle
    }

    /** Marks the spool arrived, then remembers the scanned barcode (if any) for its product. */
    fun confirmArrived(spoolId: Long) {
        val result = scan.value as? ScanUiState.Result
        val barcode = result?.barcode
        val spool = (result?.match as? ScanMatch.Found)?.spools?.firstOrNull { it.spoolId == spoolId }
        clearScan()
        launchAction {
            inventory.markArrived(listOf(spoolId))
            // A code never goes onto a generic product: it names no maker.
            if (barcode != null && spool != null && !isGenericBrand(spool.brand)) {
                catalog.rememberBarcode(barcode, spool.productId)
            }
            "הסליל סומן שהגיע"
        }
    }
}
