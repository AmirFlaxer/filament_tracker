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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.countLabel

private val displayOrder = listOf(SpoolStatus.IN_USE, SpoolStatus.OPEN_STORED, SpoolStatus.SEALED, SpoolStatus.ORDERED)

private fun ListRow.supportingText(): String {
    val text = displayOrder.mapNotNull { status -> counts[status]?.let { countLabel(status, it) } }.joinToString(" · ")
    return if (slots.isEmpty()) text else "$text · חריץ ${slots.joinToString(", ")}"
}

/** Lazy-list key: the section title and color alone repeat across families, so the family is part of it. */
internal fun listRowKey(section: ListSection, row: ListRow) = "r-${section.title}-${row.family}-${row.colorId}"

fun LazyListScope.listSections(sections: List<ListSection>, onRowClick: (ListRow) -> Unit) {
    sections.forEach { section ->
        item(key = "h-${section.title}") {
            if (section.colorHex == null) {
                SectionHeader(section.title)
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ColorDot(section.colorHex, size = 20.dp)
                    Text(section.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(section.rows, key = { listRowKey(section, it) }) { row ->
            ListItem(
                headlineContent = { Text(row.label) },
                supportingContent = { Text(row.supportingText()) },
                leadingContent = { ColorDot(row.colorHex, size = 28.dp) },
                modifier = Modifier.clickable { onRowClick(row) },
            )
        }
    }
}
