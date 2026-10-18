package com.amir.filamenttracker.presentation.spool

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.SpoolEventEntity
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.SpoolPlanner
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.ConfirmDialog
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.formatDateTime
import com.amir.filamenttracker.presentation.common.label
import com.amir.filamenttracker.presentation.common.statusText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpoolDetailScreen(onBack: () -> Unit, vm: SpoolDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)
    var slotPicker by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val spool = state.spool
    val context = LocalContext.current

    Scaffold(topBar = { BackTopBar(spool?.title ?: "סליל", onBack) }, snackbarHost = { SnackbarHost(host) }) { padding ->
        if (spool == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loaded) Text("הסליל לא נמצא")
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ColorDot(spool.colorHex, size = 48.dp)
                    Column {
                        Text(spool.statusText(), style = MaterialTheme.typography.titleMedium)
                        Text("${spool.weightGrams} ג'", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (spool.status) {
                        SpoolStatus.ORDERED -> {
                            Button(onClick = vm::arrive) { Text("הגיע") }
                            OutlinedButton(onClick = { confirmRemove = true }) { Text("מחק, לא יגיע") }
                        }
                        SpoolStatus.SEALED -> Button(onClick = { slotPicker = true }) { Text("טען לחריץ") }
                        SpoolStatus.OPEN_STORED -> {
                            Button(onClick = { slotPicker = true }) { Text("טען לחריץ") }
                            OutlinedButton(onClick = vm::finish) { Text("הסתיים") }
                        }
                        SpoolStatus.IN_USE -> {
                            Button(onClick = vm::unload) { Text("הוצא לאחסון") }
                            OutlinedButton(onClick = vm::finish) { Text("הסתיים") }
                        }
                        SpoolStatus.FINISHED -> Unit
                    }
                }
            }
            item { OutlinedButton(onClick = vm::undo, enabled = state.canUndo) { Text("בטל פעולה אחרונה") } }
            if (state.canDelete) {
                item { OutlinedButton(onClick = { confirmDelete = true }) { Text("מחק סליל") } }
            }
            item { SectionHeader("היסטוריה") }
            items(state.events, key = { it.id }) { e ->
                Text("${e.at.formatDateTime()} · ${e.describe()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (slotPicker) {
        SlotPickerDialog(state.occupants, onDismiss = { slotPicker = false }) {
            vm.load(it)
            slotPicker = false
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            text = state.spool?.let { deleteConfirmText(it.status, it.amsSlot, it.orderId) }.orEmpty(),
            confirmLabel = "מחק",
            onConfirm = {
                confirmDelete = false
                vm.deleteSpool {
                    Toast.makeText(context, "הסליל נמחק", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmRemove) {
        ConfirmDialog(
            text = "למחוק את הסליל מההזמנה? אי אפשר לבטל את זה.",
            confirmLabel = "מחק",
            onConfirm = { vm.removeOrdered(onBack) },
            onDismiss = { confirmRemove = false },
        )
    }
}

// Words, not arrows: arrows flip in RTL text.
private fun SpoolEventEntity.describe(): String {
    val slotText = toSlot?.let { " (חריץ $it)" } ?: ""
    val from = fromStatus ?: return "נקלט במצב ${toStatus.label()}$slotText"
    return "מ-${from.label()} ל-${toStatus.label()}$slotText"
}

@Composable
private fun SlotPickerDialog(occupants: Map<Int, String>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("לאיזה חריץ?") },
        text = {
            Column {
                (1..SpoolPlanner.SLOT_COUNT).forEach { n ->
                    ListItem(
                        headlineContent = { Text("חריץ $n") },
                        supportingContent = { Text(occupants[n]?.let { "$it, יעבור לאחסון" } ?: "ריק") },
                        modifier = Modifier.clickable { onPick(n) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
