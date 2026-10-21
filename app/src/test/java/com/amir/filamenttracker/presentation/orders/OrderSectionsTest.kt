package com.amir.filamenttracker.presentation.orders

import com.amir.filamenttracker.data.db.OrderSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class OrderSectionsTest {
    private fun order(id: Long, spools: Int, pending: Int, archived: Boolean = false) =
        OrderSummary(id, "Amazon", Instant.ofEpochSecond(1000 - id), null, null, spools, pending, archived)

    @Test
    fun `waiting orders go first, arrived next, archived last, each keeping the newest-first order`() {
        val s = orderSections(
            listOf(
                order(1, spools = 2, pending = 0),
                order(2, spools = 3, pending = 3),
                order(3, spools = 1, pending = 0, archived = true),
                order(4, spools = 3, pending = 1),
                order(5, spools = 2, pending = 0),
            ),
        )
        assertEquals(listOf(2L, 4L), s.waiting.map { it.id })
        assertEquals(listOf(1L, 5L), s.arrived.map { it.id })
        assertEquals(listOf(3L), s.archived.map { it.id })
    }

    @Test
    fun `an empty order counts as arrived, since nothing is on the way`() {
        val s = orderSections(listOf(order(1, spools = 0, pending = 0)))
        assertEquals(listOf(1L), s.arrived.map { it.id })
    }
}
