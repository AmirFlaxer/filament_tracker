package com.amir.filamenttracker.presentation.inventory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.ColorNameChip
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

internal fun statusRowKey(section: StatusTableSection, row: StatusTableRow) = "sr-${section.family}-${row.colorId}"

/** The column names, kept above the list so they stay in place while the rows scroll. */
@Composable
fun StatusTableHeader() {
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

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.statusTableItems(sections: List<StatusTableSection>, onRowClick: (StatusTableRow) -> Unit) {
    sections.forEach { section ->
        stickyHeader(key = "sf-${section.family}") {
            Text(
                section.family,
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(section.rows, key = { statusRowKey(section, it) }) { row ->
            Row(
                Modifier.fillMaxWidth().clickable { onRowClick(row) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    ColorNameChip(row.label, row.colorHex)
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

/** The cell breakdown, one status per line, e.g. "1 פתוח" and "3 סגורים". */
private fun MaterialCell.details(): List<String> =
    cellOrder.mapNotNull { status -> counts[status]?.let { countLabel(status, it) } }

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
                cell.details().forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Fixed-width name column when the table scrolls, otherwise it takes the space the families leave. */
private fun RowScope.nameColumn(scrolls: Boolean): Modifier =
    if (scrolls) Modifier.width(NameWidth) else Modifier.weight(1f)

private val MaterialTable.scrolls: Boolean get() = families.size > 3

/** The header and the body each get this frame with the same [scroll], so the family names move sideways with their columns. */
private fun MaterialTable.frame(scroll: ScrollState): Modifier {
    val outer = if (scrolls) Modifier.horizontalScroll(scroll) else Modifier.fillMaxWidth()
    // Inside horizontalScroll the width is unbounded, so the scrolling table gets an explicit width for its dividers.
    val sized = if (scrolls) Modifier.width(NameWidth + FamilyWidth * families.size) else Modifier
    return outer.padding(horizontal = 16.dp).then(sized)
}

/** The family names, kept above the list so they stay in place while the colors scroll. */
@Composable
fun MaterialTableHeader(table: MaterialTable, scroll: ScrollState) {
    Column(table.frame(scroll)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("צבע", nameColumn(table.scrolls), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            table.families.forEach { HeaderText(it, Modifier.width(FamilyWidth)) }
        }
        HorizontalDivider()
    }
}

fun LazyListScope.materialTableItems(
    table: MaterialTable,
    scroll: ScrollState,
    onCell: (family: String, colorId: Long, cell: MaterialCell) -> Unit,
) {
    val scrolls = table.scrolls
    item(key = "mt-body") {
        Column(table.frame(scroll)) {
            table.rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // One line per color: a long name is cut with an ellipsis instead of wrapping; the spools screen shows it in full.
                    Row(nameColumn(scrolls), verticalAlignment = Alignment.CenterVertically) {
                        ColorNameChip(row.colorName, row.colorHex, style = MaterialTheme.typography.bodyMedium)
                    }
                    row.cells.forEachIndexed { i, cell -> MaterialTableCell(cell) { cell?.let { onCell(table.families[i], row.colorId, it) } } }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}
