package com.amir.filamenttracker.presentation.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import com.amir.filamenttracker.presentation.scan.FoundCard
import com.amir.filamenttracker.presentation.scan.ScanMatch
import com.amir.filamenttracker.presentation.scan.ScanMessage
import com.amir.filamenttracker.presentation.scan.ScanUiState
import com.amir.filamenttracker.presentation.scan.rememberLabelCapture
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.OrderSummary
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.NoInsets
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(onOrderClick: (Long) -> Unit, onNewOrder: () -> Unit, vm: OrdersViewModel = hiltViewModel()) {
    val sections by vm.sections.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)
    var showArchive by rememberSaveable { mutableStateOf(false) }
    val scan by vm.scan.collectAsStateWithLifecycle()
    val orderById by vm.orderById.collectAsStateWithLifecycle()
    val startCapture = rememberLabelCapture(vm::onPhoto)

    Scaffold(
        contentWindowInsets = NoInsets,
        snackbarHost = { SnackbarHost(host) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewOrder,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("הזמנה חדשה") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            val s = sections ?: return@LazyColumn
            item(key = "scan-shipment") {
                OutlinedButton(onClick = startCapture, enabled = scan != ScanUiState.Reading, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("סרוק משלוח")
                }
            }
            if (s.waiting.isEmpty() && s.arrived.isEmpty() && s.archived.isEmpty()) {
                item { Text("אין עדיין הזמנות.", Modifier.padding(16.dp)) }
            }
            orderSection("ממתינות", s.waiting, onOrderClick)
            orderSection("הגיעו", s.arrived, onOrderClick) { order ->
                TextButton(onClick = { vm.archive(order.id) }) { Text("לארכיון") }
            }
            if (s.archived.isNotEmpty()) {
                item(key = "archive-toggle") {
                    ListItem(
                        headlineContent = { Text("ארכיון (${s.archived.size})") },
                        trailingContent = {
                            Icon(
                                if (showArchive) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showArchive) "סגור ארכיון" else "פתח ארכיון",
                            )
                        },
                        modifier = Modifier.clickable { showArchive = !showArchive },
                    )
                }
                if (showArchive) {
                    orderSection(null, s.archived, onOrderClick) { order ->
                        TextButton(onClick = { vm.restore(order.id) }) { Text("החזר") }
                    }
                }
            }
        }
    }

    if (scan != ScanUiState.Idle) {
        ModalBottomSheet(
            onDismissRequest = vm::clearScan,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                when (val state = scan) {
                    ScanUiState.Idle -> {}
                    ScanUiState.Reading -> Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("קורא את המדבקה...")
                    }
                    is ScanUiState.Result -> when (val match = state.match) {
                        // An ordered spool can carry the generic brand: shown as an unconfirmed match, with no retag here.
                        is ScanMatch.Found, is ScanMatch.GenericSuggestion -> FoundCard(
                            match = (match as? ScanMatch.GenericSuggestion)
                                ?.let { ScanMatch.Found(it.spools, false, it.labelBrand) } ?: (match as ScanMatch.Found),
                            actionLabel = "הגיע",
                            subtitle = { spool ->
                                orderById[spool.orderId]?.let { "${it.vendor} ${it.orderNumber.orEmpty()}".trim() } ?: ""
                            },
                            onPick = { vm.confirmArrived(it.spoolId) },
                            onDismiss = vm::clearScan,
                        )
                        is ScanMatch.KnownProductNoSpool ->
                            ScanMessage("זוהה: ${state.productTitle ?: "מוצר"}, אבל אין סליל שלו במשלוח ממתין")
                        ScanMatch.None -> ScanMessage("לא נמצאה התאמה")
                    }
                }
            }
        }
    }
}

private fun LazyListScope.orderSection(
    title: String?,
    orders: List<OrderSummary>,
    onOrderClick: (Long) -> Unit,
    action: (@Composable (OrderSummary) -> Unit)? = null,
) {
    if (orders.isEmpty()) return
    title?.let { item(key = "header-$it") { SectionHeader(it) } }
    items(orders, key = { it.id }) { order ->
        ListItem(
            headlineContent = { Text("${order.vendor} · ${order.orderedAt.formatDate()}") },
            supportingContent = { Text(order.statusText()) },
            trailingContent = action?.let { { it(order) } },
            modifier = Modifier.clickable { onOrderClick(order.id) },
        )
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
