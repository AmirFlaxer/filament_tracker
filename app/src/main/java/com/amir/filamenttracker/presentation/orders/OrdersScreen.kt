package com.amir.filamenttracker.presentation.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.OrderSummary
import com.amir.filamenttracker.presentation.common.NoInsets
import com.amir.filamenttracker.presentation.common.formatDate

@Composable
fun OrdersScreen(onOrderClick: (Long) -> Unit, onNewOrder: () -> Unit, vm: OrdersViewModel = hiltViewModel()) {
    val orders by vm.orders.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = NoInsets,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewOrder,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("הזמנה חדשה") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (orders?.isEmpty() == true) item { Text("אין עדיין הזמנות.", Modifier.padding(16.dp)) }
            items(orders.orEmpty(), key = { it.id }) { order ->
                ListItem(
                    headlineContent = { Text("${order.vendor} · ${order.orderedAt.formatDate()}") },
                    supportingContent = { Text(order.statusText()) },
                    modifier = Modifier.clickable { onOrderClick(order.id) },
                )
            }
        }
    }
}

private fun OrderSummary.statusText(): String {
    val arrived = spoolCount - pendingCount
    val state = when {
        spoolCount == 0 -> "ריקה"
        pendingCount == 0 -> "הגיעה"
        arrived == 0 -> "ממתינה"
        else -> "ממתינה, הגיעו $arrived מתוך $spoolCount"
    }
    val expected = expectedAt?.takeIf { pendingCount > 0 }?.let { " · צפי ${it.formatDate()}" } ?: ""
    return "$spoolCount סלילים · $state$expected"
}
