package com.amir.filamenttracker.presentation.ams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.domain.resolveColorHex
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.ColorNameChip
import com.amir.filamenttracker.presentation.common.NoInsets
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmsScreen(onSpoolClick: (Long) -> Unit, vm: AmsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)
    var openSlot by rememberSaveable { mutableStateOf<Int?>(null) }

    Scaffold(contentWindowInsets = NoInsets, snackbarHost = { SnackbarHost(host) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.slots.forEachIndexed { index, spool ->
                SlotCard(slot = index + 1, spool = spool, onClick = { openSlot = index + 1 })
            }
        }
    }

    openSlot?.let { slot ->
        val spool = state.slots[slot - 1]
        ModalBottomSheet(onDismissRequest = { openSlot = null }) {
            SlotSheet(
                slot = slot,
                spool = spool,
                candidates = state.candidates,
                onLoad = { vm.load(it, slot); openSlot = null },
                onUnload = { spool?.let { vm.unload(it.spoolId) }; openSlot = null },
                onFinish = { spool?.let { vm.finish(it.spoolId) }; openSlot = null },
                onDetails = { spool?.let { onSpoolClick(it.spoolId) }; openSlot = null },
            )
        }
    }
}

@Composable
private fun SlotCard(slot: Int, spool: SpoolRow?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ColorDot(spool?.let { resolveColorHex(it.colorName, it.colorHex) }, size = 40.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("חריץ $slot", style = MaterialTheme.typography.labelMedium)
                Text(spool?.title ?: "ריק", style = MaterialTheme.typography.titleMedium)
                spool?.let { ColorNameChip(it.colorName, it.colorHex, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** Swapping is two taps: the slot, then a spool from the list. */
@Composable
private fun SlotSheet(
    slot: Int,
    spool: SpoolRow?,
    candidates: List<SpoolRow>,
    onLoad: (Long) -> Unit,
    onUnload: () -> Unit,
    onFinish: () -> Unit,
    onDetails: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        item {
            Text(
                spool?.let { "חריץ $slot: ${it.title}" } ?: "חריץ $slot ריק",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (spool != null) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onUnload) { Text("הוצא לאחסון") }
                    OutlinedButton(onClick = onFinish) { Text("הסתיים") }
                    TextButton(onClick = onDetails) { Text("פרטים") }
                }
            }
        }
        item { SectionHeader(if (spool == null) "טען לחריץ" else "החלף ב:") }
        if (candidates.isEmpty()) item { Text("אין סלילים זמינים", Modifier.padding(16.dp)) }
        items(candidates, key = { it.spoolId }) { c ->
            ListItem(
                headlineContent = { Text(c.title) },
                supportingContent = { Text(c.status.label()) },
                leadingContent = { ColorDot(resolveColorHex(c.colorName, c.colorHex)) },
                modifier = Modifier.clickable { onLoad(c.spoolId) },
            )
        }
    }
}
