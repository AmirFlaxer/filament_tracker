package com.amir.filamenttracker.presentation.spool

import androidx.lifecycle.SavedStateHandle
import com.amir.filamenttracker.data.InventoryRepository
import com.amir.filamenttracker.data.db.SpoolEventEntity
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class SpoolDetailState(
    val loaded: Boolean = false,
    val spool: SpoolRow? = null,
    val events: List<SpoolEventEntity> = emptyList(),
    /** Other spools in slots, to warn before displacing one. */
    val occupants: Map<Int, String> = emptyMap(),
) {
    // The creation event (no fromStatus) cannot be undone.
    val canUndo: Boolean get() = events.firstOrNull()?.fromStatus != null

    // An ordered spool is removed with "מחק, לא יגיע" instead.
    val canDelete: Boolean get() = spool != null && spool.status != SpoolStatus.ORDERED
}

fun deleteConfirmText(status: SpoolStatus, slot: Int?, orderId: Long?): String = buildString {
    append("למחוק את הסליל? כל ההיסטוריה שלו תימחק, והוא לא ייספר בתצרוכת. אי אפשר לבטל את זה.")
    if (status == SpoolStatus.IN_USE && slot != null) append(" חריץ $slot יתפנה.")
    if (orderId != null) append(" הוא יוסר גם מההזמנה.")
}

@HiltViewModel
class SpoolDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val inventory: InventoryRepository,
) : BaseViewModel() {
    private val spoolId: Long = checkNotNull(handle.get<Long>("spoolId"))

    val state: StateFlow<SpoolDetailState> =
        combine(inventory.spool(spoolId), inventory.events(spoolId), inventory.spools) { spool, events, all ->
            SpoolDetailState(
                loaded = true,
                spool = spool,
                events = events,
                occupants = all
                    .filter { it.status == SpoolStatus.IN_USE && it.amsSlot != null && it.spoolId != spoolId }
                    .associate { it.amsSlot!! to it.title },
            )
        }.asState(SpoolDetailState())

    fun arrive() = launchAction { inventory.markArrived(listOf(spoolId)); "סומן שהגיע" }
    fun load(slot: Int) = launchAction { inventory.loadIntoSlot(spoolId, slot); "נטען לחריץ $slot" }
    fun unload() = launchAction { inventory.unload(spoolId); "הוצא לאחסון" }
    fun finish() = launchAction { inventory.finish(spoolId); "סומן שהסתיים" }
    fun undo() = launchAction { inventory.undoLast(spoolId); "הפעולה האחרונה בוטלה" }

    fun deleteSpool(onDone: () -> Unit) = launchAction {
        inventory.deleteSpool(spoolId)
        onDone()
        null
    }

    fun removeOrdered(onDone: () -> Unit) = launchAction {
        inventory.removeOrderedSpool(spoolId)
        onDone()
        null
    }
}
