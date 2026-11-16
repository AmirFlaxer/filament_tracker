package com.amir.filamenttracker.presentation.amazon

import androidx.lifecycle.viewModelScope
import com.amir.filamenttracker.data.CatalogRepository
import com.amir.filamenttracker.data.ImportLine
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.amazon.AmazonWeb
import com.amir.filamenttracker.data.amazon.ORDER_HISTORY_URL
import com.amir.filamenttracker.data.amazon.PageRead
import com.amir.filamenttracker.data.db.OrderEntity
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.domain.amazon.AmazonOrder
import com.amir.filamenttracker.domain.amazon.expectedDate
import com.amir.filamenttracker.domain.amazon.parseAmazonOrders
import com.amir.filamenttracker.domain.amazon.parseTrackingIds
import com.amir.filamenttracker.presentation.common.BaseViewModel
import com.amir.filamenttracker.presentation.common.NewProductInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ImportScreenState {
    data object Loading : ImportScreenState
    data object SignIn : ImportScreenState
    data class Error(val message: String, val canShowPage: Boolean) : ImportScreenState
    data class Choices(val choices: ImportChoices, val trackingAdded: Int) : ImportScreenState
    data class Confirm(val order: AmazonOrder, val expectedAt: LocalDate?, val cards: List<ImportCard>) : ImportScreenState
}

/** Process-wide scope for work that must survive leaving the screen. */
private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private const val MSG_UNREADABLE = "לא הצלחתי לקרוא את רשימת ההזמנות מאמזון"

@HiltViewModel
class AmazonImportViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
    private val web: AmazonWeb,
) : BaseViewModel() {
    val materials = catalog.materials.asState(emptyList())
    val colors = catalog.colors.asState(emptyList())
    val brands = catalog.brands.asState(emptyList())

    private val _state = MutableStateFlow<ImportScreenState>(ImportScreenState.Loading)
    val state: StateFlow<ImportScreenState> = _state

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    /** The list shown last, so that going back from the confirm screen needs no new read. */
    private var lastChoices: ImportScreenState.Choices? = null
    private var loadJob: Job? = null
    private var trackingJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        trackingJob?.cancel()
        _state.value = ImportScreenState.Loading
        loadJob = viewModelScope.launch {
            _state.value = when (val page = web.readPage(ORDER_HISTORY_URL) { "ORDER PLACED" in it }) {
                PageRead.SignInNeeded -> ImportScreenState.SignIn
                is PageRead.Failed -> ImportScreenState.Error(page.reason, canShowPage = false)
                PageRead.Unexpected -> ImportScreenState.Error(MSG_UNREADABLE, canShowPage = true)
                is PageRead.Text -> {
                    val orders = parseAmazonOrders(page.text)
                    if (orders == null) {
                        ImportScreenState.Error(MSG_UNREADABLE, canShowPage = true)
                    } else {
                        val families = catalog.materials.first().map { it.family }.distinctBy { it.uppercase() }
                        val choices = ImportScreenState.Choices(importableOrders(orders, inventory.orderNumbers(), families), 0)
                        lastChoices = choices
                        startTrackingPass(choices)
                        choices
                    }
                }
            }
        }
    }

    /** Fills tracking numbers in the background; the list is already on screen and only gains the note when done. */
    private fun startTrackingPass(choices: ImportScreenState.Choices) {
        trackingJob = viewModelScope.launch {
            val added = addTrackingNumbers { true }
            if (added > 0) {
                val updated = choices.copy(trackingAdded = added)
                if (lastChoices === choices) lastChoices = updated
                _state.update { if (it === choices) updated else it }
            }
        }
    }

    /**
     * Fills tracking numbers of waiting Amazon orders that pass [filter]. A failure here never breaks
     * the screen, and one order failing does not skip the others.
     */
    private suspend fun addTrackingNumbers(filter: (OrderEntity) -> Boolean): Int {
        var added = 0
        val waiting = try {
            inventory.ordersAwaitingTracking().filter(filter)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return 0
        }
        for (order in waiting) {
            val number = order.orderNumber ?: continue
            try {
                val ids = mutableListOf<String>()
                for (link in web.trackPackageLinks(number)) {
                    val read = web.readPage(link) { "Tracking ID" in it }
                    if (read is PageRead.Text) ids += parseTrackingIds(read.text)
                }
                val distinct = ids.distinct()
                if (distinct.isNotEmpty()) {
                    inventory.setTrackingNumber(order.id, distinct.joinToString(", "))
                    added++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Tracking numbers are a bonus; go on with the next order.
            }
        }
        return added
    }

    fun signOut() {
        loadJob?.cancel()
        trackingJob?.cancel()
        web.signOut()
        lastChoices = null
        _state.value = ImportScreenState.SignIn
    }

    fun selectOrder(order: AmazonOrder) {
        viewModelScope.launch {
            val products = catalog.products.first()
            val lists = CatalogLists(
                brands = catalog.brands.first(),
                families = catalog.materials.first().map { it.family }.distinctBy { it.uppercase() },
                colors = catalog.colors.first().map { it.name },
            )
            _state.value = ImportScreenState.Confirm(
                order, expectedDate(order.status, order.orderedOn, LocalDate.now()), buildCards(order, products, lists),
            )
        }
    }

    fun backToChoices() {
        lastChoices?.let { _state.value = it } ?: load()
    }

    fun setExpected(date: LocalDate?) = updateConfirm { it.copy(expectedAt = date) }

    fun removeCard(key: Int) = updateConfirm { c -> c.copy(cards = c.cards.filter { it.key != key }) }

    fun setRowQuantity(key: Int, row: Int, quantity: Int) =
        editCard(key) { card, products -> card.withRowQuantity(row, quantity, products) }

    fun renameRowColor(key: Int, row: Int, name: String) =
        editCard(key) { card, products -> card.withRowColor(row, name, products) }

    fun markColors(key: Int, names: List<String>) =
        editCard(key) { card, products -> card.markColors(names, products) }

    fun editSpec(key: Int, input: NewProductInput) = editCard(key) { card, products ->
        card.withSpec(input.brand, input.materialFamily, input.materialVariant, input.materialFinish, input.weightGrams, products)
    }

    private fun updateConfirm(change: (ImportScreenState.Confirm) -> ImportScreenState.Confirm) =
        _state.update { if (it is ImportScreenState.Confirm) change(it) else it }

    private fun editCard(key: Int, change: (ImportCard, List<ProductRow>) -> ImportCard) {
        viewModelScope.launch {
            val products = catalog.products.first()
            updateConfirm { c -> c.copy(cards = c.cards.map { if (it.key == key) change(it, products) else it }) }
        }
    }

    /** Ignores re-entry while a save runs, so a double tap cannot import the order twice. */
    fun save(onDone: () -> Unit) {
        val confirm = _state.value as? ImportScreenState.Confirm ?: return
        if (!_saving.compareAndSet(false, true)) return
        launchAction {
            var saved = false
            try {
                val lines = confirm.cards.flatMap { card ->
                    card.rows.map {
                        ImportLine(
                            it.productId, it.quantity, card.brand, card.family, card.variant, card.finish,
                            it.colorName, card.weightGrams,
                        )
                    }
                }
                val orderedAt = confirm.order.orderedOn.atStartOfDay(ZoneId.systemDefault()).toInstant()
                val orderId = inventory.importOrder(confirm.order.orderNumber, orderedAt, confirm.expectedAt, lines)
                saved = true
                onDone()
                // Best effort and outliving the screen; the next import screen fills tracking anyway.
                backgroundScope.launch { addTrackingNumbers { it.id == orderId } }
                null
            } finally {
                if (!saved) _saving.value = false // after success the screen closes; stay locked
            }
        }
    }
}
