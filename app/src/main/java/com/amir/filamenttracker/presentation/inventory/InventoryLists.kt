package com.amir.filamenttracker.presentation.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.resolveColorHex
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.ColorNameChip
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.countLabel

private val displayOrder = listOf(SpoolStatus.IN_USE, SpoolStatus.OPEN_STORED, SpoolStatus.SEALED, SpoolStatus.ORDERED)

private fun ListRow.supportingText(): String {
    val text = displayOrder.mapNotNull { status -> counts[status]?.let { countLabel(status, it) } }.joinToString(" · ")
    return if (slots.isEmpty()) text else "$text · חריץ ${slots.joinToString(", ")}"
}

/** A color row is the chip; a by-status row is the family text followed by the chip; anything else is plain text. */
@Composable
private fun RowHeadline(row: ListRow) {
    val color = row.colorName
    when {
        color == null -> Text(row.label)
        row.label == color -> ColorNameChip(color, row.colorHex)
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.family)
            ColorNameChip(color, row.colorHex)
        }
    }
}

/** Lazy-list key: the section title and color alone repeat across families, so the family is part of it. */
internal fun listRowKey(section: ListSection, row: ListRow) = "r-${section.title}-${row.family}-${row.colorId}"

fun LazyListScope.listSections(sections: List<ListSection>, onRowClick: (ListRow) -> Unit) {
    sections.forEach { section ->
        item(key = "h-${section.title}") {
            if (!section.isColor) {
                SectionHeader(section.title)
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) {
                    ColorNameChip(section.title, section.colorHex, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        items(section.rows, key = { listRowKey(section, it) }) { row ->
            ListItem(
                headlineContent = { RowHeadline(row) },
                supportingContent = { Text(row.supportingText()) },
                // A row that shows its color as a chip needs no dot; a family row under a color header keeps one.
                leadingContent = if (row.colorName == null) {
                    { ColorDot(resolveColorHex(section.title, row.colorHex), size = 28.dp) }
                } else {
                    null
                },
                modifier = Modifier.clickable { onRowClick(row) },
            )
        }
    }
}
