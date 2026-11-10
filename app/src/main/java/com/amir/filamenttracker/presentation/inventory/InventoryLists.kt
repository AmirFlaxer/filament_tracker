package com.amir.filamenttracker.presentation.inventory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.amir.filamenttracker.domain.model.SpoolStatus
import com.amir.filamenttracker.domain.lineName
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

/** The color chip, followed by the finish in quieter text when the stock line has one ("שחור Matte"). */
@Composable
internal fun ColorWithFinish(colorName: String, colorHex: String?, finish: String, style: TextStyle = LocalTextStyle.current) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorNameChip(colorName, colorHex, style = style)
        if (finish.isNotEmpty()) Text(finish, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/**
 * A color row is the chip and its finish; a by-status row is the stock line text followed by the chip;
 * anything else is plain text.
 */
@Composable
private fun RowHeadline(row: ListRow) {
    val color = row.colorName
    when {
        color == null -> Text(row.label)
        row.label == color -> ColorWithFinish(color, row.colorHex, row.finish)
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(lineName(row.family, row.finish))
            ColorNameChip(color, row.colorHex)
        }
    }
}

/** Lazy-list key: the section title and color alone repeat across stock lines, so the line is part of it. */
internal fun listRowKey(section: ListSection, row: ListRow) = "r-${section.title}-${row.family}-${row.finish}-${row.colorId}"

/** Each section header sticks to the top until the next one pushes it away, so it needs an opaque background. */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.listSections(sections: List<ListSection>, onRowClick: (ListRow) -> Unit) {
    sections.forEach { section ->
        stickyHeader(key = "h-${section.title}") {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                if (!section.isColor) {
                    SectionHeader(section.title)
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) {
                        ColorNameChip(section.title, section.colorHex, style = MaterialTheme.typography.titleMedium)
                    }
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
