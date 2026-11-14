package com.amir.filamenttracker.presentation.ams

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.data.scan.LabelScanner
import com.amir.filamenttracker.domain.SpoolPlanner
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val _scan = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scan: StateFlow<ScanUiState> = _scan
    private var scanJob: Job? = null

    fun load(spoolId: Long, slot: Int) = launchAction { inventory.loadIntoSlot(spoolId, slot); null }
    fun unload(spoolId: Long) = launchAction { inventory.unload(spoolId); "הסליל הוצא לאחסון" }
    fun finish(spoolId: Long) = launchAction { inventory.finish(spoolId); "הסליל סומן שהסתיים" }

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
        val candidates = state.value.candidates
        val reading = readLabel(
            text = result.text,
            knownBrands = catalog.brands.first().filterNot { isGenericBrand(it) },
            knownFamilies = (candidates.map { it.materialFamily } + catalog.materials.first().map { it.family }).distinct(),
            knownColors = (candidates.map { it.colorName } + catalog.colors.first().map { it.name }).distinct(),
        )
        val barcode = result.barcodes.singleOrNull()
        val match = matchScan(reading, barcode?.let { catalog.productForBarcode(it) }, candidates)
        val title = (match as? ScanMatch.KnownProductNoSpool)?.let { known ->
            catalog.products.first().firstOrNull { it.productId == known.productId }?.title
        }
        return ScanUiState.Result(match, barcode, title)
    }

    fun clearScan() {
        scanJob?.cancel()
        _scan.value = ScanUiState.Idle
    }

    /** Loads the spool, then remembers the scanned barcode (if any) for its product. */
    fun confirmLoad(spoolId: Long, slot: Int) {
        val barcode = (scan.value as? ScanUiState.Result)?.barcode
        val spool = state.value.candidates.firstOrNull { it.spoolId == spoolId }
        clearScan()
        launchAction {
            inventory.loadIntoSlot(spoolId, slot)
            // A code never goes onto a generic product: it names no maker.
            if (barcode != null && spool != null && !isGenericBrand(spool.brand)) {
                catalog.rememberBarcode(barcode, spool.productId)
            }
            null
        }
    }

    fun retagAndLoad(spoolId: Long, brand: String, slot: Int) {
        val barcode = (scan.value as? ScanUiState.Result)?.barcode
        clearScan()
        launchAction {
            val productId = inventory.retagSpool(spoolId, brand)
            inventory.loadIntoSlot(spoolId, slot)
            if (barcode != null) catalog.rememberBarcode(barcode, productId)
            null
        }
    }
}
