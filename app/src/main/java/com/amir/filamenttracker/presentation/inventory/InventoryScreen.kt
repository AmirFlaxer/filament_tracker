package com.amir.filamenttracker.presentation.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.AlertRow
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NoInsets
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.countLabel
import com.amir.filamenttracker.presentation.common.title

@Composable
fun InventoryScreen(
    onColorClick: (materialId: Long, colorId: Long) -> Unit,
    onAddExisting: () -> Unit,
    vm: InventoryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

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
            item { AlertsCard(state.alerts) }
            if (state.loaded && state.sections.isEmpty()) {
                item {
                    Text("אין עדיין סלילים. אפשר להתחיל בהוספת המלאי הקיים, בכפתור שלמטה.", Modifier.padding(16.dp))
                }
            }
            state.sections.forEach { section ->
                item(key = "m-${section.title}") { SectionHeader(section.title) }
                items(section.rows, key = { "c-${it.materialId}-${it.colorId}" }) { group ->
                    ListItem(
                        headlineContent = { Text(group.label) },
                        supportingContent = { Text(group.countsText()) },
                        leadingContent = { ColorDot(group.colorHex, size = 28.dp) },
                        modifier = Modifier.clickable { onColorClick(group.materialId, group.colorId) },
                    )
                }
            }
        }
    }
}

private val displayOrder = listOf(SpoolStatus.IN_USE, SpoolStatus.OPEN_STORED, SpoolStatus.SEALED, SpoolStatus.ORDERED)

private fun ListRow.countsText(): String =
    displayOrder.mapNotNull { status -> counts[status]?.let { countLabel(status, it) } }.joinToString(" · ")

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
                                Text("${alert.materialName} ${alert.colorName}")
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
