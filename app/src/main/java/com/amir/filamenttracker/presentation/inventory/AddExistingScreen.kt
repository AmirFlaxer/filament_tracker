package com.amir.filamenttracker.presentation.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.amir.filamenttracker.domain.SpoolPlanner
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NewProductDialog
import com.amir.filamenttracker.presentation.common.ProductPickerDialog
import com.amir.filamenttracker.presentation.common.Stepper
import com.amir.filamenttracker.presentation.common.label

private val initialStatuses = listOf(SpoolStatus.SEALED, SpoolStatus.IN_USE, SpoolStatus.OPEN_STORED)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExistingScreen(onBack: () -> Unit, vm: AddExistingViewModel = hiltViewModel()) {
    val products by vm.products.collectAsStateWithLifecycle()
    val materials by vm.materials.collectAsStateWithLifecycle()
    val colors by vm.colors.collectAsStateWithLifecycle()
    val brands by vm.brands.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val occupants by vm.occupants.collectAsStateWithLifecycle()
    val adding by vm.adding.collectAsStateWithLifecycle()
    val lastAdded by vm.lastAdded.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)

    var status by rememberSaveable { mutableStateOf(SpoolStatus.SEALED) }
    var quantity by rememberSaveable { mutableIntStateOf(1) }
    var slot by rememberSaveable { mutableStateOf<Int?>(null) }
    var picker by remember { mutableStateOf(false) }
    var newProduct by remember { mutableStateOf(false) }
    val inUse = status == SpoolStatus.IN_USE
    val canAdd = !adding && selected != null && (!inUse || slot != null)

    Scaffold(topBar = { BackTopBar("הוספת סליל קיים", onBack) }, snackbarHost = { SnackbarHost(host) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            lastAdded?.let { text ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Text(text)
                    }
                }
            }

            Text("מוצר", style = MaterialTheme.typography.titleSmall)
            OutlinedCard(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ColorDot(selected?.colorHex)
                    Text(selected?.title ?: "בחר מוצר")
                }
            }

            Text("מצב", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                initialStatuses.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = status == s,
                        onClick = { status = s },
                        shape = SegmentedButtonDefaults.itemShape(i, initialStatuses.size),
                    ) { Text(s.label()) }
                }
            }

            if (inUse) {
                Text("חריץ", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..SpoolPlanner.SLOT_COUNT).forEach { n ->
                        FilterChip(selected = slot == n, onClick = { slot = n }, label = { Text("$n") })
                    }
                }
                slot?.let { n ->
                    occupants[n]?.let { title ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "בחריץ $n נמצא $title. הוא יעבור לאחסון כשתוסיף.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                Text("כמות", style = MaterialTheme.typography.titleSmall)
                Stepper(quantity, { quantity = it }, min = 1, max = 50)
            }

            Button(
                onClick = {
                    vm.add(if (inUse) 1 else quantity, status, if (inUse) slot else null) {
                        quantity = 1
                        slot = null
                    }
                },
                enabled = canAdd,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("הוסף") }
            Text("אפשר להמשיך ולהוסיף עוד סלילים. כפתור החזרה מסיים.", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (picker) {
        ProductPickerDialog(
            products,
            onDismiss = { picker = false },
            onPick = { vm.select(it.productId); picker = false },
            onNewProduct = { picker = false; newProduct = true },
        )
    }
    if (newProduct) {
        NewProductDialog(materials, colors, brands, onDismiss = { newProduct = false }) {
            vm.createProduct(it)
            newProduct = false
        }
    }
}
