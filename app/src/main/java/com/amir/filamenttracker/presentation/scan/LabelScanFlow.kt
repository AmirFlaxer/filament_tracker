package com.amir.filamenttracker.presentation.scan

import android.content.Context
import android.net.Uri
import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.data.scan.LabelScanner
import com.amir.filamenttracker.domain.isGenericBrand
import com.amir.filamenttracker.domain.readLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The label-scan state machine shared by the screens that scan: read the photo, match it against the
 * caller's candidate spools, and always delete the photo afterwards. Each screen keeps its own
 * candidate source and its own confirm action.
 */
class LabelScanFlow(
    private val scanner: LabelScanner,
    private val catalog: CatalogRepository,
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state
    private var job: Job? = null

    /** The single barcode of the current result, or null (none, several, or no result). */
    val currentBarcode: String? get() = (_state.value as? ScanUiState.Result)?.barcode

    /** Reads the photo behind [uri] and shows the match; null (the user cancelled) goes back to idle. */
    fun onPhoto(uri: Uri?, candidates: suspend () -> List<SpoolRow>) {
        if (uri == null) {
            _state.value = ScanUiState.Idle
            return
        }
        job?.cancel()
        _state.value = ScanUiState.Reading
        job = scope.launch {
            _state.value = try {
                analyze(uri, candidates())
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

    private suspend fun analyze(uri: Uri, candidates: List<SpoolRow>): ScanUiState.Result {
        val result = scanner.scan(uri)
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

    fun clear() {
        job?.cancel()
        _state.value = ScanUiState.Idle
    }

    /** The barcode to remember for [spool]'s product: only a single scanned code, never onto a generic product. */
    fun barcodeToSave(spool: SpoolRow?): String? =
        currentBarcode.takeIf { spool != null && !isGenericBrand(spool.brand) }
}
