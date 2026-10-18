package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoolTransitionsTest {
    private val specTransitions = setOf(
        ORDERED to SEALED,
        SEALED to IN_USE,
        IN_USE to OPEN_STORED,
        OPEN_STORED to IN_USE,
        IN_USE to FINISHED,
        OPEN_STORED to FINISHED,
    )

    @Test
    fun `exactly the six spec transitions are allowed`() {
        for (from in SpoolStatus.entries) {
            for (to in SpoolStatus.entries) {
                assertEquals("$from to $to", (from to to) in specTransitions, SpoolTransitions.isAllowed(from, to))
            }
        }
    }

    @Test
    fun `finished is terminal`() {
        assertTrue(SpoolTransitions.targets(FINISHED).isEmpty())
    }
}
