package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.Spool
import com.amir.filamenttracker.domain.model.SpoolChange
import com.amir.filamenttracker.domain.model.SpoolEvent
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SpoolPlannerTest {
    private val t = Instant.parse("2026-10-01T10:00:00Z")

    @Test
    fun `load sealed spool into empty slot`() {
        val changes = SpoolPlanner.load(Spool(1, SEALED, null), 2, inUse = emptyList())
        assertEquals(listOf(SpoolChange(1, SEALED, IN_USE, null, 2)), changes)
    }

    @Test
    fun `load into occupied slot moves the occupant to storage first`() {
        val occupant = Spool(9, IN_USE, 2)
        val changes = SpoolPlanner.load(Spool(1, OPEN_STORED, null), 2, inUse = listOf(occupant))
        assertEquals(
            listOf(
                SpoolChange(9, IN_USE, OPEN_STORED, 2, null),
                SpoolChange(1, OPEN_STORED, IN_USE, null, 2),
            ),
            changes,
        )
    }

    @Test
    fun `loading a spool into the slot it is already in does nothing`() {
        val spool = Spool(1, IN_USE, 3)
        assertTrue(SpoolPlanner.load(spool, 3, inUse = listOf(spool)).isEmpty())
    }

    @Test(expected = IllegalTransitionException::class)
    fun `ordered spool cannot be loaded`() {
        SpoolPlanner.load(Spool(1, ORDERED, null), 1, emptyList())
    }

    @Test(expected = IllegalTransitionException::class)
    fun `finished spool cannot be loaded`() {
        SpoolPlanner.load(Spool(1, FINISHED, null), 1, emptyList())
    }

    @Test(expected = IllegalTransitionException::class)
    fun `spool in one slot cannot jump to another slot`() {
        val spool = Spool(1, IN_USE, 1)
        SpoolPlanner.load(spool, 2, listOf(spool))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `slot zero is rejected`() {
        SpoolPlanner.load(Spool(1, SEALED, null), 0, emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `slot five is rejected`() {
        SpoolPlanner.load(Spool(1, SEALED, null), 5, emptyList())
    }

    @Test
    fun `vacate returns null for an empty slot`() {
        assertNull(SpoolPlanner.vacate(4, listOf(Spool(1, IN_USE, 1))))
    }

    @Test
    fun `move out of a slot clears the slot`() {
        assertEquals(SpoolChange(1, IN_USE, OPEN_STORED, 3, null), SpoolPlanner.move(Spool(1, IN_USE, 3), OPEN_STORED))
        assertEquals(SpoolChange(1, IN_USE, FINISHED, 3, null), SpoolPlanner.move(Spool(1, IN_USE, 3), FINISHED))
    }

    @Test
    fun `arrival moves ordered to sealed`() {
        assertEquals(SpoolChange(1, ORDERED, SEALED, null, null), SpoolPlanner.move(Spool(1, ORDERED, null), SEALED))
    }

    @Test(expected = IllegalTransitionException::class)
    fun `sealed spool cannot be finished without being opened`() {
        SpoolPlanner.move(Spool(1, SEALED, null), FINISHED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `move cannot target IN_USE`() {
        SpoolPlanner.move(Spool(1, SEALED, null), IN_USE)
    }

    @Test
    fun `undo of unload puts the spool back in its slot`() {
        val spool = Spool(1, OPEN_STORED, null)
        val last = SpoolEvent(5, 1, IN_USE, OPEN_STORED, 2, null, t)
        assertEquals(SpoolChange(1, OPEN_STORED, IN_USE, null, 2), SpoolPlanner.undo(spool, last, emptyList()))
    }

    @Test
    fun `undo of finish restores in-use and slot`() {
        val spool = Spool(1, FINISHED, null)
        val last = SpoolEvent(5, 1, IN_USE, FINISHED, 4, null, t)
        assertEquals(SpoolChange(1, FINISHED, IN_USE, null, 4), SpoolPlanner.undo(spool, last, emptyList()))
    }

    @Test(expected = SlotOccupiedException::class)
    fun `undo is blocked when the old slot is now taken`() {
        val spool = Spool(1, OPEN_STORED, null)
        val last = SpoolEvent(5, 1, IN_USE, OPEN_STORED, 2, null, t)
        SpoolPlanner.undo(spool, last, inUse = listOf(Spool(7, IN_USE, 2)))
    }

    @Test(expected = UndoNotPossibleException::class)
    fun `undo is refused when the spool status no longer matches the event`() {
        val spool = Spool(1, IN_USE, 2)
        SpoolPlanner.undo(spool, SpoolEvent(5, 1, IN_USE, OPEN_STORED, 2, null, t), emptyList())
    }

    @Test(expected = UndoNotPossibleException::class)
    fun `creation event cannot be undone`() {
        val spool = Spool(1, SEALED, null)
        SpoolPlanner.undo(spool, SpoolEvent(5, 1, null, SEALED, null, null, t), emptyList())
    }
}
