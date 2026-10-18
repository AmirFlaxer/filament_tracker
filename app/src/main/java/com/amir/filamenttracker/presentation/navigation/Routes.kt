package com.amir.filamenttracker.presentation.navigation

import android.net.Uri

object Routes {
    const val INVENTORY = "inventory"
    const val AMS = "ams"
    const val ORDERS = "orders"
    const val CONSUMPTION = "consumption"
    const val CATALOG = "catalog"
    const val TARGETS = "targets"
    const val ADD_EXISTING = "add_existing"
    const val NEW_ORDER = "new_order"

    const val SPOOL = "spool/{spoolId}"
    fun spool(id: Long) = "spool/$id"

    const val ORDER = "order/{orderId}"
    fun order(id: Long) = "order/$id"

    const val COLOR_SPOOLS = "color/{family}/{colorId}"
    fun colorSpools(family: String, colorId: Long) = "color/${Uri.encode(family)}/$colorId"
}
