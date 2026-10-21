package com.amir.filamenttracker.presentation.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.OrderEntity
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.resolveColorHex
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.ConfirmDialog
import com.amir.filamenttracker.presentation.common.NewProductDialog
import com.amir.filamenttracker.presentation.common.ProductPickerDialog
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.Stepper
import com.amir.filamenttracker.presentation.common.countLabel
import com.amir.filamenttracker.presentation.common.formatDate
import com.amir.filamenttracker.presentation.common.statusText
import java.time.LocalDate

/** What a picked product is for: new spools in the order, or the right product for one spool. */
private sealed interface PickFor {
    data object Add : PickFor
    data class Replace(val spoolId: Long) : PickFor
}

@Composable
fun OrderDetailScreen(onBack: () -> Unit, onSpoolClick: (Long) -> Unit, vm: OrderDetailViewModel = hiltViewModel()) {
    val order by vm.order.collectAsStateWithLifecycle()
    val spools by vm.spools.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val materials by vm.materials.collectAsStateWithLifecycle()
    val colors by vm.colors.collectAsStateWithLifecycle()
    val brands by vm.brands.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)
    var confirmDeleteOrder by remember { mutableStateOf(false) }
    var editDetails by remember { mutableStateOf(false) }
    var removeSpoolId by remember { mutableStateOf<Long?>(null) }
    var pickFor by remember { mutableStateOf<PickFor?>(null) }
    var newProductFor by remember { mutableStateOf<PickFor?>(null) }
    var quantityFor by remember { mutableStateOf<Long?>(null) }
    val nothingArrived = spools.all { it.status == SpoolStatus.ORDERED }
    // Spools come sorted by material and color, so each product's spools are already together.
    val groups = spools.groupBy { it.productId }.values.toList()

    fun picked(target: PickFor, productId: Long) = when (target) {
        PickFor.Add -> quantityFor = productId
        is PickFor.Replace -> vm.changeProduct(target.spoolId, productId)
    }

    Scaffold(
        topBar = {
            BackTopBar("הזמנה", onBack, actions = {
                if (order != null) {
                    IconButton(onClick = { editDetails = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "ערוך פרטי הזמנה")
                    }
                }
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
            item { SectionHeader("פריטים", actionLabel = "הוסף סליל", onAction = { pickFor = PickFor.Add }) }
            groups.forEach { group ->
                val first = group.first()
                item(key = "product-${first.productId}") {
                    ListItem(
                        headlineContent = { Text(first.title, style = MaterialTheme.typography.titleSmall) },
                        supportingContent = { Text(groupSummary(group)) },
                        leadingContent = { ColorDot(resolveColorHex(first.colorName, first.colorHex)) },
                        trailingContent = {
                            IconButton(onClick = { vm.addSpools(first.productId, 1) }) {
                                Icon(Icons.Default.Add, contentDescription = "עוד סליל כזה")
                            }
                        },
                    )
                }
                items(group, key = { it.spoolId }) { s ->
                    ListItem(
                        headlineContent = { Text(s.statusText()) },
                        trailingContent = {
                            if (s.status == SpoolStatus.ORDERED) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { vm.arrive(s.spoolId) }) { Text("הגיע") }
                                    IconButton(onClick = { pickFor = PickFor.Replace(s.spoolId) }) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = "החלף מוצר")
                                    }
                                    IconButton(onClick = { removeSpoolId = s.spoolId }) {
                                        Icon(Icons.Default.Delete, contentDescription = "הסר מההזמנה")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.padding(start = 40.dp).clickable { onSpoolClick(s.spoolId) },
                    )
                }
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
    if (editDetails) {
        order?.let { o ->
            EditOrderDialog(o, onDismiss = { editDetails = false }) { vendor, number, expected ->
                vm.updateDetails(vendor, number, expected)
                editDetails = false
            }
        }
    }
    pickFor?.let { target ->
        ProductPickerDialog(
            products,
            onDismiss = { pickFor = null },
            onPick = { pickFor = null; picked(target, it.productId) },
            onNewProduct = { pickFor = null; newProductFor = target },
        )
    }
    newProductFor?.let { target ->
        NewProductDialog(materials, colors, brands, onDismiss = { newProductFor = null }) {
            vm.createProduct(it) { id -> picked(target, id) }
            newProductFor = null
        }
    }
    quantityFor?.let { productId ->
        AddQuantityDialog(
            product = products.firstOrNull { it.productId == productId },
            onDismiss = { quantityFor = null },
        ) { quantity ->
            vm.addSpools(productId, quantity)
            quantityFor = null
        }
    }
}

/** "5 סגורים · 1 בדרך" for one product's spools in the order. */
private fun groupSummary(group: List<SpoolRow>): String =
    group.groupingBy { it.status }.eachCount().toSortedMap()
        .map { (status, n) -> countLabel(status, n) }
        .joinToString(" · ")

@Composable
private fun AddQuantityDialog(product: ProductRow?, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var quantity by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("כמה סלילים להוסיף?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                product?.let { Text(it.title) }
                Stepper(quantity, { quantity = it }, min = 1, max = 50)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(quantity) }) { Text("הוסף") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
private fun EditOrderDialog(
    order: OrderEntity,
    onDismiss: () -> Unit,
    onConfirm: (vendor: String, orderNumber: String, expectedAt: LocalDate?) -> Unit,
) {
    var vendor by remember { mutableStateOf(order.vendor) }
    var orderNumber by remember { mutableStateOf(order.orderNumber.orEmpty()) }
    var expectedAt by remember { mutableStateOf(order.expectedAt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("פרטי ההזמנה") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(vendor, { vendor = it }, label = { Text("ספק") }, singleLine = true)
                OutlinedTextField(orderNumber, { orderNumber = it }, label = { Text("מספר הזמנה, לא חובה") }, singleLine = true)
                ExpectedDateField(expectedAt) { expectedAt = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(vendor, orderNumber, expectedAt) }) { Text("שמור") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
