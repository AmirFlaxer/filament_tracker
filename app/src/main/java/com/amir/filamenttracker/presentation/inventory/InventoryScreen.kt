package com.amir.filamenttracker.presentation.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.AlertRow
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.model.InventoryView
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NoInsets
import com.amir.filamenttracker.presentation.common.title

@Composable
fun InventoryScreen(
    onColorClick: (family: String, colorId: Long) -> Unit,
    onAddExisting: () -> Unit,
    vm: InventoryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val view = state.view
    val spools = state.spools
    // Grouping is computed once per data or view change, not on every recomposition.
    val content = remember(spools, state.alerts, view) { view?.let { buildContent(it, spools, state.alerts) } }

    Scaffold(
        contentWindowInsets = NoInsets,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExisting,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("הוסף סליל קיים") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (view != null && content != null) {
                item(key = "alerts") { AlertsCard(state.alerts) }
                item(key = "view-switcher") { ViewSwitcher(view, vm::select) }
                if (content.isEmpty(spools)) {
                    item {
                        Text("אין עדיין סלילים. אפשר להתחיל בהוספת המלאי הקיים, בכפתור שלמטה.", Modifier.padding(16.dp))
                    }
                } else when (content) {
                    is ViewContent.Lists -> listSections(content.sections) { onColorClick(it.family, it.colorId) }
                    is ViewContent.StatusTable -> statusTableItems(content.sections) { onColorClick(it.family, it.colorId) }
                    is ViewContent.MaterialTableContent -> materialTableItems(content.table) { family, colorId, cell ->
                        // A missing-only cell has no spools to open.
                        if (!cell.missing) onColorClick(family, colorId)
                    }
                }
            }
        }
    }
}

private sealed interface ViewContent {
    data class Lists(val sections: List<ListSection>) : ViewContent
    data class StatusTable(val sections: List<StatusTableSection>) : ViewContent
    data class MaterialTableContent(val table: MaterialTable) : ViewContent
}

/** The empty text shows only when this view has nothing to show; the material table can still hold missing rows. */
private fun ViewContent.isEmpty(spools: List<SpoolRow>): Boolean = when (this) {
    is ViewContent.MaterialTableContent -> table.rows.isEmpty()
    else -> spools.none { it.status != SpoolStatus.FINISHED }
}

private fun buildContent(view: InventoryView, spools: List<SpoolRow>, alerts: List<AlertRow>): ViewContent = when (view) {
    InventoryView.BY_MATERIAL -> ViewContent.Lists(byMaterial(spools))
    InventoryView.BY_COLOR -> ViewContent.Lists(byColor(spools))
    InventoryView.BY_STATUS -> ViewContent.Lists(byStatus(spools))
    InventoryView.TABLE_STATUS -> ViewContent.StatusTable(statusTable(spools))
    InventoryView.TABLE_MATERIAL -> ViewContent.MaterialTableContent(materialTable(spools, alerts))
}

private fun InventoryView.label(): String = when (this) {
    InventoryView.BY_MATERIAL -> "לפי חומר"
    InventoryView.BY_COLOR -> "לפי צבע"
    InventoryView.BY_STATUS -> "לפי מצב"
    InventoryView.TABLE_STATUS -> "טבלה לפי מצב"
    InventoryView.TABLE_MATERIAL -> "טבלה לפי חומר"
}

@Composable
private fun ViewSwitcher(selected: InventoryView, onSelect: (InventoryView) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InventoryView.entries.forEach { v ->
            FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(v.label()) })
        }
    }
}

@Composable
private fun AlertsCard(alerts: List<AlertRow>) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (alerts.isEmpty()) {
                Text("המלאי תקין", style = MaterialTheme.typography.titleMedium)
            } else {
                AlertKind.entries.forEach { kind ->
                    val group = alerts.filter { it.kind == kind }
                    if (group.isNotEmpty()) {
                        Text(kind.title(), style = MaterialTheme.typography.titleSmall, color = kind.color())
                        group.forEach { alert ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColorDot(alert.colorHex, size = 14.dp)
                                Text("${alert.family} ${alert.colorName}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertKind.color(): Color = when (this) {
    AlertKind.MISSING -> MaterialTheme.colorScheme.error
    AlertKind.RUNNING_LOW -> MaterialTheme.colorScheme.tertiary
    AlertKind.ON_THE_WAY -> MaterialTheme.colorScheme.primary
}
