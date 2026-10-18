package com.amir.filamenttracker.presentation.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.domain.resolveColorHex
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.ConfirmDialog
import com.amir.filamenttracker.presentation.common.formatDate
import com.amir.filamenttracker.presentation.common.statusText

@Composable
fun OrderDetailScreen(onBack: () -> Unit, onSpoolClick: (Long) -> Unit, vm: OrderDetailViewModel = hiltViewModel()) {
    val order by vm.order.collectAsStateWithLifecycle()
    val spools by vm.spools.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)
    var confirmDeleteOrder by remember { mutableStateOf(false) }
    var removeSpoolId by remember { mutableStateOf<Long?>(null) }
    val nothingArrived = spools.all { it.status == SpoolStatus.ORDERED }

    Scaffold(
        topBar = {
            BackTopBar("הזמנה", onBack, actions = {
                if (order != null && nothingArrived) {
                    IconButton(onClick = { confirmDeleteOrder = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "מחק הזמנה")
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            order?.let { o ->
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("${o.vendor} · ${o.orderedAt.formatDate()}", style = MaterialTheme.typography.titleMedium)
                        o.orderNumber?.let { Text("מספר הזמנה: $it") }
                        o.expectedAt?.let { Text("צפי הגעה: ${it.formatDate()}") }
                    }
                }
            }
            if (spools.any { it.status == SpoolStatus.ORDERED }) {
                item {
                    Button(onClick = vm::arriveAll, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Text("הכל הגיע")
                    }
                }
            }
            items(spools, key = { it.spoolId }) { s ->
                ListItem(
                    headlineContent = { Text(s.title) },
                    supportingContent = { Text(s.statusText()) },
                    leadingContent = { ColorDot(resolveColorHex(s.colorName, s.colorHex)) },
                    trailingContent = {
                        if (s.status == SpoolStatus.ORDERED) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { vm.arrive(s.spoolId) }) { Text("הגיע") }
                                IconButton(onClick = { removeSpoolId = s.spoolId }) {
                                    Icon(Icons.Default.Delete, contentDescription = "הסר מההזמנה")
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable { onSpoolClick(s.spoolId) },
                )
            }
        }
    }

    if (confirmDeleteOrder) {
        ConfirmDialog(
            text = "למחוק את ההזמנה ואת כל הסלילים שבה?",
            confirmLabel = "מחק",
            onConfirm = { vm.deleteOrder(onBack) },
            onDismiss = { confirmDeleteOrder = false },
        )
    }
    removeSpoolId?.let { id ->
        ConfirmDialog(
            text = "להסיר את הסליל מההזמנה? כלומר, הוא לא יגיע.",
            confirmLabel = "הסר",
            onConfirm = { vm.removeSpool(id) },
            onDismiss = { removeSpoolId = null },
        )
    }
}
