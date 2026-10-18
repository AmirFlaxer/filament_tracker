package com.amir.filamenttracker.presentation.consumption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NoInsets

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsumptionScreen(vm: ConsumptionViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val range by vm.selectedRange.collectAsStateWithLifecycle()

    Scaffold(contentWindowInsets = NoInsets) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConsumptionRange.entries.forEach { r ->
                        FilterChip(selected = r == range, onClick = { vm.selectRange(r) }, label = { Text(r.label) })
                    }
                }
            }
            val list = rows
            if (list != null && list.isEmpty()) {
                item {
                    Text(
                        "עוד לא הסתיים אף סליל. התצרוכת תופיע כאן אחרי שתסמן סלילים שהסתיימו.",
                        Modifier.padding(16.dp),
                    )
                }
            }
            if (!list.isNullOrEmpty()) {
                item {
                    Text(
                        "הסתיימו בטווח: ${list.sumOf { it.finishedInRange }} סלילים",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(list.orEmpty(), key = { "${it.key.materialId}-${it.key.colorId}" }) { row ->
                ListItem(
                    headlineContent = { Text(row.title) },
                    leadingContent = { ColorDot(row.colorHex, size = 28.dp) },
                    supportingContent = { Text(listOfNotNull(row.rateText, row.forecastText).joinToString("\n")) },
                    trailingContent = { Text("${row.finishedInRange}", style = MaterialTheme.typography.titleLarge) },
                )
            }
        }
    }
}
