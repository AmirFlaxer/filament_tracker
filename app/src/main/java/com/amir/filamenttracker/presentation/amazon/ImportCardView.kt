package com.amir.filamenttracker.presentation.amazon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amir.filamenttracker.domain.composeMaterialName
import com.amir.filamenttracker.domain.resolveColorHex
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.Stepper
import com.amir.filamenttracker.presentation.common.TextFieldsDialog

/** One Amazon line on the confirm screen: its original title, the product specification and a row per color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportCardView(
    card: ImportCard,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMarkColors: () -> Unit,
    onRowQuantity: (row: Int, quantity: Int) -> Unit,
    onRowRename: (row: Int, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var titleOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Int?>(null) }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                card.sourceTitle,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (titleOpen) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { titleOpen = !titleOpen },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onEdit, label = { Text(card.brand.ifBlank { "מותג חסר" }) })
                AssistChip(
                    onClick = onEdit,
                    label = {
                        Text(if (card.family.isBlank()) "חומר חסר" else composeMaterialName(card.family, card.variant, card.finish))
                    },
                )
                AssistChip(onClick = onEdit, label = { Text("${card.weightGrams} ג'") })
            }
            Row {
                TextButton(onClick = onEdit) { Text("עריכה") }
                TextButton(onClick = onRemove) { Text("הסר") }
            }
            card.rows.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(resolveColorHex(row.colorName, null))
                    Text(
                        row.colorName,
                        Modifier.weight(1f).padding(horizontal = 8.dp).clickable { renaming = index },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        if (row.productId != null) "קיים" else "חדש",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.productId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                    Stepper(row.quantity, { onRowQuantity(index, it) }, min = 0, max = 50)
                }
            }
            if (card.colorsToMark > 0) {
                Text("הצבעים לא מופיעים בשם", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onMarkColors) { Text("סמן ${card.colorsToMark} צבעים") }
            }
        }
    }

    renaming?.let { index ->
        card.rows.getOrNull(index)?.let { row ->
            TextFieldsDialog(
                title = "שם הצבע",
                fields = listOf("צבע" to row.colorName),
                onDismiss = { renaming = null },
                onConfirm = { onRowRename(index, it.first()) },
            )
        }
    }
}
