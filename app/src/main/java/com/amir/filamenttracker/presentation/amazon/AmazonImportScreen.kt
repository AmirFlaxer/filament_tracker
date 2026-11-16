package com.amir.filamenttracker.presentation.amazon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.domain.amazon.AmazonOrder
import com.amir.filamenttracker.domain.amazon.AmazonItem
import com.amir.filamenttracker.domain.resolveColorHex
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NewProductDialog
import com.amir.filamenttracker.presentation.common.NewProductInput
import com.amir.filamenttracker.presentation.common.formatDate
import com.amir.filamenttracker.presentation.orders.ExpectedDateField

@Composable
fun AmazonImportScreen(onBack: () -> Unit, vm: AmazonImportViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)
    var menuOpen by remember { mutableStateOf(false) }
    var showPage by rememberSaveable { mutableStateOf(false) }

    val inConfirm = state is ImportScreenState.Confirm
    val pageShown = showPage && state is ImportScreenState.Error
    BackHandler(enabled = inConfirm || pageShown) { if (pageShown) showPage = false else vm.backToChoices() }

    Scaffold(
        topBar = {
            BackTopBar("ייבוא מאמזון", onBack = { if (pageShown) showPage = false else if (inConfirm) vm.backToChoices() else onBack() }) {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "תפריט") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("התנתק מאמזון") },
                        onClick = { menuOpen = false; showPage = false; vm.signOut() },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                ImportScreenState.Loading -> Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("קורא את ההזמנות מאמזון...")
                }
                ImportScreenState.SignIn -> AmazonSignInView(onSignedIn = { showPage = false; vm.load() }, modifier = Modifier.fillMaxSize())
                is ImportScreenState.Error ->
                    if (showPage) {
                        // Already signed in: no auto-reload here, or the page would flash and bounce back.
                        Column(Modifier.fillMaxSize()) {
                            AmazonSignInView(onSignedIn = {}, modifier = Modifier.weight(1f).fillMaxWidth())
                            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showPage = false }) { Text("סגור") }
                                Button(onClick = { showPage = false; vm.load() }) { Text("נסה שוב") }
                            }
                        }
                    } else {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(s.message)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = vm::load) { Text("נסה שוב") }
                                if (s.canShowPage) OutlinedButton(onClick = { showPage = true }) { Text("הצג את הדף") }
                            }
                        }
                    }
                is ImportScreenState.Choices -> ChoicesList(s, onPick = vm::selectOrder)
                is ImportScreenState.Confirm -> ConfirmContent(s, vm, saving, onSaved = onBack)
            }
        }
    }
}

@Composable
private fun ChoicesList(state: ImportScreenState.Choices, onPick: (AmazonOrder) -> Unit) {
    val orders = state.choices.orders
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (orders.isEmpty()) "אין הזמנות חדשות באמזון" else newOrdersHeader(orders.size),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (state.trackingAdded > 0) item { Text(trackingAddedLine(state.trackingAdded)) }
        items(orders, key = { it.orderNumber }) { order -> OrderChoiceCard(order) { onPick(order) } }
        if (state.choices.skippedTitles.isNotEmpty()) {
            item {
                Text(
                    "לא פילמנט, לא יובא: ${state.choices.skippedTitles.joinToString(", ") { it.substringBefore('|').trim() }}",
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OrderChoiceCard(order: AmazonOrder, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(order.orderedOn.formatDate(), style = MaterialTheme.typography.titleSmall)
                Text(
                    order.orderNumber,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            order.items.forEach { item: AmazonItem ->
                Text(
                    "${item.quantity} x ${item.title.substringBefore('|').trim()}",
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("צפי: ${expectedLabel(order)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun newOrdersHeader(count: Int): String =
    if (count == 1) "הזמנה חדשה אחת מ-3 החודשים האחרונים" else "$count הזמנות חדשות מ-3 החודשים האחרונים"

private fun trackingAddedLine(count: Int): String =
    if (count == 1) "נוסף מספר משלוח להזמנה אחת" else "נוסף מספר משלוח ל-$count הזמנות"

private fun missingLine(count: Int): String =
    if (count == 1) "חסר: צבעים או חומר בכרטיס אחד" else "חסר: צבעים או חומר ב-$count כרטיסים"

private fun dayMonth(date: java.time.LocalDate): String = "${date.dayOfMonth}/${date.monthValue}"

/** "24/11 עד 1/12" for a window, "24/11" for a single date; the raw status when it carries no arrival. */
private fun expectedLabel(order: AmazonOrder): String {
    val range = com.amir.filamenttracker.domain.amazon.expectedRange(order.status, order.orderedOn, java.time.LocalDate.now())
        ?: return order.status
    return if (range.start == range.end) dayMonth(range.end) else "${dayMonth(range.start)} עד ${dayMonth(range.end)}"
}

@Composable
private fun ConfirmContent(state: ImportScreenState.Confirm, vm: AmazonImportViewModel, saving: Boolean, onSaved: () -> Unit) {
    val materials by vm.materials.collectAsStateWithLifecycle()
    val colors by vm.colors.collectAsStateWithLifecycle()
    val brands by vm.brands.collectAsStateWithLifecycle()
    var editKey by remember { mutableStateOf<Int?>(null) }
    var markKey by remember { mutableStateOf<Int?>(null) }

    val incomplete = state.cards.count { it.colorsToMark > 0 || it.family.isBlank() }
    val spools = state.cards.spoolCount

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    state.order.orderNumber,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                )
            }
            item { ExpectedDateField(state.expectedAt, vm::setExpected) }
            items(state.cards, key = { it.key }) { card ->
                ImportCardView(
                    card,
                    onEdit = { editKey = card.key },
                    onRemove = { vm.removeCard(card.key) },
                    onMarkColors = { markKey = card.key },
                    onRowQuantity = { row, n -> vm.setRowQuantity(card.key, row, n) },
                    onRowRename = { row, name -> vm.renameRowColor(card.key, row, name) },
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (incomplete > 0) {
                Text(missingLine(incomplete), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { vm.save(onSaved) },
                enabled = incomplete == 0 && spools > 0 && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("שמור הזמנה ($spools סלילים)") }
        }
    }

    editKey?.let { key ->
        state.cards.firstOrNull { it.key == key }?.let { card ->
            NewProductDialog(
                materials, colors, brands,
                onDismiss = { editKey = null },
                onConfirm = { vm.editSpec(key, it); editKey = null },
                initial = NewProductInput(
                    card.brand, card.family, card.variant, card.finish, "", "", card.weightGrams, "",
                ),
                showColor = false,
            )
        }
    }
    markKey?.let { key ->
        state.cards.firstOrNull { it.key == key }?.let { card ->
            MarkColorsDialog(
                catalogColors = colors.map { it.name },
                max = card.colorsToMark,
                onDismiss = { markKey = null },
                onConfirm = { vm.markColors(key, it); markKey = null },
            )
        }
    }
}

/** Picks up to [max] colors from the catalog, or types a new one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkColorsDialog(catalogColors: List<String>, max: Int, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    val selected = remember { mutableStateListOf<String>() }
    var typed by remember { mutableStateOf("") }
    fun addTyped() {
        val name = typed.trim()
        if (name.isNotEmpty() && selected.none { it.equals(name, ignoreCase = true) } && selected.size < max) selected += name
        typed = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("סימון צבעים ($max לכל היותר)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (catalogColors + selected.filter { s -> catalogColors.none { it.equals(s, ignoreCase = true) } }).forEach { name ->
                        val on = selected.any { it.equals(name, ignoreCase = true) }
                        FilterChip(
                            selected = on,
                            onClick = {
                                if (on) selected.removeAll { it.equals(name, ignoreCase = true) }
                                else if (selected.size < max) selected += name
                            },
                            label = { Text(name) },
                            leadingIcon = { ColorDot(resolveColorHex(name, null), size = 14.dp) },
                        )
                    }
                }
                OutlinedTextField(typed, { typed = it }, label = { Text("צבע חדש") }, singleLine = true)
                TextButton(onClick = ::addTyped, enabled = typed.isNotBlank() && selected.size < max) { Text("הוסף צבע") }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) { Text("אישור") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
