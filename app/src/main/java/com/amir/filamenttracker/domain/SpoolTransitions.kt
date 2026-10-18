package com.amir.filamenttracker.domain

import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.model.SpoolStatus.FINISHED
import com.amir.filamenttracker.domain.model.SpoolStatus.IN_USE
import com.amir.filamenttracker.domain.model.SpoolStatus.OPEN_STORED
import com.amir.filamenttracker.domain.model.SpoolStatus.ORDERED
import com.amir.filamenttracker.domain.model.SpoolStatus.SEALED

/** The allowed status transitions, spec section 4. */
object SpoolTransitions {
    private val allowed: Map<SpoolStatus, Set<SpoolStatus>> = mapOf(
        ORDERED to setOf(SEALED),
        SEALED to setOf(IN_USE),
        IN_USE to setOf(OPEN_STORED, FINISHED),
        OPEN_STORED to setOf(IN_USE, FINISHED),
        FINISHED to emptySet(),
    )

    fun targets(from: SpoolStatus): Set<SpoolStatus> = allowed.getValue(from)

    fun isAllowed(from: SpoolStatus, to: SpoolStatus): Boolean = to in targets(from)
}
