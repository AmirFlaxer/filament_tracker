package com.amir.filamenttracker.presentation.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NewProductDialog
import com.amir.filamenttracker.presentation.common.ProductPickerDialog
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.Stepper
import com.amir.filamenttracker.presentation.common.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(onBack: () -> Unit, vm: NewOrderViewModel = hiltViewModel()) {
    val products by vm.products.collectAsStateWithLifecycle()
    val materials by vm.materials.collectAsStateWithLifecycle()
    val colors by vm.colors.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)

    var vendor by rememberSaveable { mutableStateOf("Amazon") }
    var orderNumber by rememberSaveable { mutableStateOf("") }
    var expectedAt by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var picker by remember { mutableStateOf(false) }
    var newProduct by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar("הזמנה חדשה", onBack) }, snackbarHost = { SnackbarHost(host) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(vendor, { vendor = it }, label = { Text("ספק") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(
                    orderNumber, { orderNumber = it },
                    label = { Text("מספר הזמנה, לא חובה") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { datePicker = true }) {
                        Text(expectedAt?.let { "צפי הגעה: ${it.formatDate()}" } ?: "צפי הגעה, לא חובה")
                    }
                    if (expectedAt != null) TextButton(onClick = { expectedAt = null }) { Text("נקה") }
                }
            }
            item { SectionHeader("פריטים", actionLabel = "הוסף מוצר", onAction = { picker = true }) }
            if (lines.isEmpty()) item { Text("עוד לא נבחרו מוצרים") }
            items(lines, key = { it.first.productId }) { (product, quantity) ->
                ListItem(
                    headlineContent = { Text(product.title) },
                    leadingContent = { ColorDot(product.colorHex) },
                    trailingContent = { Stepper(quantity, { vm.setQuantity(product.productId, it) }, min = 0, max = 50) },
                )
            }
            item {
                Button(
                    onClick = { vm.save(vendor, orderNumber, expectedAt, onBack) },
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("שמור הזמנה") }
            }
        }
    }

    if (datePicker) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker reports UTC midnight of the chosen day.
                    dateState.selectedDateMillis?.let {
                        expectedAt = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    datePicker = false
                }) { Text("אישור") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("ביטול") } },
        ) { DatePicker(state = dateState) }
    }
    if (picker) {
        ProductPickerDialog(
            products,
            onDismiss = { picker = false },
            onPick = { vm.add(it.productId); picker = false },
            onNewProduct = { picker = false; newProduct = true },
        )
    }
    if (newProduct) {
        NewProductDialog(materials, colors, onDismiss = { newProduct = false }) {
            vm.createProductAndAdd(it)
            newProduct = false
        }
    }
}
