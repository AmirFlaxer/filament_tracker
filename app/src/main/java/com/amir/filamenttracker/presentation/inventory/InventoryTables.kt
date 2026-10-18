package com.amir.filamenttracker.presentation.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.countLabel

private val NumberWidth = 44.dp
private val FamilyWidth = 88.dp
private val NameWidth = 140.dp

@Composable
private fun NumberCell(n: Int, bold: Boolean = false) {
    val dim = n == 0
    Text(
        if (dim) "-" else "$n",
        modifier = Modifier.width(NumberWidth),
        textAlign = TextAlign.Center,
        fontWeight = if (bold && !dim) FontWeight.Bold else null,
        color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

fun LazyListScope.statusTableItems(sections: List<StatusTableSection>, onRowClick: (StatusTableRow) -> Unit) {
    item(key = "st-header") {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("שם", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("בשימוש", "פתוח", "סגור", "בדרך", "סה\"כ").forEach { HeaderText(it, Modifier.width(NumberWidth)) }
            }
            HorizontalDivider()
        }
    }
    sections.forEach { section ->
        item(key = "sf-${section.family}") {
            Text(
                section.family,
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(section.rows, key = { "sr-${section.family}-${it.materialId}-${it.colorId}" }) { row ->
            Row(
                Modifier.fillMaxWidth().clickable { onRowClick(row) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorDot(row.colorHex, size = 20.dp)
                    Text(row.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                NumberCell(row.inUse)
                NumberCell(row.open)
                NumberCell(row.sealed)
                NumberCell(row.ordered)
                NumberCell(row.total, bold = true)
            }
        }
    }
}

private val cellOrder = listOf(SpoolStatus.IN_USE, SpoolStatus.OPEN_STORED, SpoolStatus.SEALED, SpoolStatus.ORDERED)

/** The cell breakdown, e.g. "1 פתוח · 3 סגורים". */
private fun MaterialCell.detail(): String =
    cellOrder.mapNotNull { status -> counts[status]?.let { countLabel(status, it) } }.joinToString(" · ")

@Composable
private fun MaterialTableCell(cell: MaterialCell?, onClick: () -> Unit) {
    val base = Modifier.width(FamilyWidth)
    val modifier = (if (cell != null) base.clickable(onClick = onClick) else base).padding(vertical = 4.dp, horizontal = 2.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            cell == null -> Text("-", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            cell.missing -> {
                Text("0", fontWeight = FontWeight.Bold)
                Text("חסר", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            else -> {
                Text("${cell.total}", fontWeight = FontWeight.Bold)
                Text(
                    cell.detail(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Fixed-width name column when the table scrolls, otherwise it takes the space the families leave. */
private fun RowScope.nameColumn(scrolls: Boolean): Modifier =
    if (scrolls) Modifier.width(NameWidth) else Modifier.weight(1f)

fun LazyListScope.materialTableItems(table: MaterialTable, onCell: (colorId: Long, cell: MaterialCell) -> Unit) {
    val scrolls = table.families.size > 3
    item(key = "mt-body") {
        val outer = if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()
        // Inside horizontalScroll the width is unbounded, so the scrolling table gets an explicit width for its dividers.
        val sized = if (scrolls) Modifier.width(NameWidth + FamilyWidth * table.families.size) else Modifier
        Column(outer.padding(horizontal = 16.dp).then(sized)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("צבע", nameColumn(scrolls), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                table.families.forEach { HeaderText(it, Modifier.width(FamilyWidth)) }
            }
            HorizontalDivider()
            table.rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // One line per color: a long name is cut with an ellipsis instead of wrapping; the spools screen shows it in full.
                    Row(nameColumn(scrolls), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ColorDot(row.colorHex, size = 16.dp)
                        Text(row.colorName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    row.cells.forEach { cell -> MaterialTableCell(cell) { cell?.let { onCell(row.colorId, it) } } }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

/** Lets the user pick among the materials that share one table cell. */
@Composable
fun MaterialPickDialog(materials: List<CellMaterial>, onPick: (CellMaterial) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("בחירת סוג") },
        text = {
            Column {
                materials.forEach { m ->
                    Text(
                        m.materialName,
                        Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
