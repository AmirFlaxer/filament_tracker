package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.Spool
import com.amir.filamenttracker.domain.model.SpoolChange
import com.amir.filamenttracker.domain.model.SpoolEvent
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED

/**
 * Turns user actions into [SpoolChange] lists. Pure: the data layer applies the
 * changes, in list order, inside one transaction.
 */
object SpoolPlanner {
    const val SLOT_COUNT = 4

    /** The change that empties [slot], or null when it is already empty (or holds [keepSpoolId]). */
    fun vacate(slot: Int, inUse: List<Spool>, keepSpoolId: Long? = null): SpoolChange? =
        inUse.firstOrNull { it.amsSlot == slot && it.id != keepSpoolId }
            ?.let { SpoolChange(it.id, IN_USE, OPEN_STORED, slot, null) }

    /** Put [target] into [slot]. A different spool in that slot goes to storage first. */
    fun load(target: Spool, slot: Int, inUse: List<Spool>): List<SpoolChange> {
        require(slot in 1..SLOT_COUNT) { "slot must be 1..$SLOT_COUNT, was $slot" }
        if (target.status == IN_USE && target.amsSlot == slot) return emptyList()
        if (!SpoolTransitions.isAllowed(target.status, IN_USE)) {
            throw IllegalTransitionException(target.status, IN_USE)
        }
        return listOfNotNull(
            vacate(slot, inUse, keepSpoolId = target.id),
            SpoolChange(target.id, target.status, IN_USE, target.amsSlot, slot),
        )
    }

    /** Any transition except into a slot (use [load] for that). Leaving IN_USE clears the slot. */
    fun move(target: Spool, to: SpoolStatus): SpoolChange {
        require(to != IN_USE) { "use load() to put a spool into a slot" }
        if (!SpoolTransitions.isAllowed(target.status, to)) {
            throw IllegalTransitionException(target.status, to)
        }
        return SpoolChange(target.id, target.status, to, target.amsSlot, null)
    }

    /**
     * Reverse [last], the newest event of [target]. Skips the transition table on purpose:
     * undoing a mistaken "finished" must be able to go back to IN_USE.
     */
    fun undo(target: Spool, last: SpoolEvent, inUse: List<Spool>): SpoolChange {
        require(last.spoolId == target.id) { "event ${last.id} belongs to spool ${last.spoolId}" }
        val previous = last.fromStatus
            ?: throw UndoNotPossibleException("זו הפעולה הראשונה של הסליל, ואין מה לבטל")
        if (target.status != last.toStatus) {
            throw UndoNotPossibleException("מצב הסליל השתנה מאז הפעולה האחרונה, אי אפשר לבטל")
        }
        val previousSlot = last.fromSlot
        if (previous == IN_USE && previousSlot != null &&
            inUse.any { it.amsSlot == previousSlot && it.id != target.id }
        ) {
            throw SlotOccupiedException(previousSlot)
        }
        return SpoolChange(target.id, target.status, previous, target.amsSlot, previousSlot)
    }
}
