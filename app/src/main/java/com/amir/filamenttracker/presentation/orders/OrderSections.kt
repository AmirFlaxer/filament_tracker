package com.amir.filamenttracker.presentation.orders

import com.amir.filamenttracker.data.db.OrderSummary

/** The orders list split for display: what is still on the way first, the archive last. */
data class OrderSections(
    val waiting: List<OrderSummary>,
    val arrived: List<OrderSummary>,
    val archived: List<OrderSummary>,
)

/** Keeps the incoming (newest-first) order inside each section. */
fun orderSections(orders: List<OrderSummary>): OrderSections {
    val (archived, active) = orders.partition { it.archived }
    val (waiting, arrived) = active.partition { it.pendingCount > 0 }
    return OrderSections(waiting, arrived, archived)
}
