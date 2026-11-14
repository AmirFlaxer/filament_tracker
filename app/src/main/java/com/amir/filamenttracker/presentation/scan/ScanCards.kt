package com.amir.filamenttracker.presentation.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.lineName
import com.amir.filamenttracker.domain.resolveColorHex
import com.amir.filamenttracker.presentation.common.ColorDot

private val CardPadding = 16.dp

@Composable
private fun ScanCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun SpoolLine(spool: SpoolRow, subtitle: String, modifier: Modifier = Modifier, selected: Boolean = false) {
    ListItem(
        headlineContent = { Text(spool.title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { ColorDot(resolveColorHex(spool.colorName, spool.colorHex)) },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        modifier = modifier,
    )
}

@Composable
fun FoundCard(
    match: ScanMatch.Found,
    actionLabel: String,
    subtitle: (SpoolRow) -> String,
    onPick: (SpoolRow) -> Unit,
    onDismiss: () -> Unit,
) {
    ScanCard {
        Text("זוהה מהמדבקה", style = MaterialTheme.typography.titleSmall)
        if (!match.brandConfirmed) {
            Text(
                match.labelBrand?.let { "זוהה לפי חומר וצבע, המותג במדבקה: $it" } ?: "זוהה לפי חומר וצבע",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        match.spools.forEach { spool ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SpoolLine(spool, subtitle(spool), Modifier.weight(1f))
                OutlinedButton(onClick = { onPick(spool) }) { Text(actionLabel) }
            }
        }
        TextButton(onClick = onDismiss) { Text("לא זה") }
    }
}

@Composable
fun GenericSuggestionCard(
    match: ScanMatch.GenericSuggestion,
    slot: Int,
    onRetagAndLoad: (spoolId: Long, brand: String) -> Unit,
    onLoadOnly: (spoolId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var brand by remember { mutableStateOf(match.labelBrand) }
    var selectedId by remember { mutableLongStateOf(match.spools.first().spoolId) }
    val chosen = match.spools.firstOrNull { it.spoolId == selectedId } ?: match.spools.first()
    ScanCard {
        Text(
            "המדבקה: ${match.labelBrand} ${lineName(chosen.materialFamily, chosen.materialFinish)} ${chosen.colorName}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text("אין מוצר של ${match.labelBrand}, אבל יש סליל דומה:")
        match.spools.forEach { spool ->
            SpoolLine(
                spool, spool.brand,
                Modifier.clickable { selectedId = spool.spoolId },
                selected = match.spools.size > 1 && spool.spoolId == chosen.spoolId,
            )
        }
        OutlinedTextField(
            value = brand,
            onValueChange = { brand = it },
            label = { Text("יצרן") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("לעדכן את הסליל הזה ל-$brand ולטעון לחריץ $slot?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onRetagAndLoad(chosen.spoolId, brand) }) { Text("עדכן וטען") }
            OutlinedButton(onClick = { onLoadOnly(chosen.spoolId) }) { Text("טען בלי לעדכן") }
        }
        TextButton(onClick = onDismiss) { Text("לא זה") }
    }
}

@Composable
fun ScanMessage(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
}
