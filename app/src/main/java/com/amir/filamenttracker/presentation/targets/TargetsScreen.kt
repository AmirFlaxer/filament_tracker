package com.amir.filamenttracker.presentation.targets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.Stepper

@Composable
fun TargetsScreen(onBack: () -> Unit, vm: TargetsViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)

    Scaffold(topBar = { BackTopBar("שומר במלאי", onBack) }, snackbarHost = { SnackbarHost(host) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "סמן את השילובים שחייבים להיות תמיד במלאי. המספר הוא כמה סלילים סגורים צריכים להישאר, ומתחתיו תופיע התראה.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (rows.isEmpty()) {
                item {
                    Text("אין עדיין מוצרים בקטלוג. כל מוצר שיתווסף יופיע כאן כשילוב של חומר וצבע.", Modifier.padding(16.dp))
                }
            }
            items(rows, key = { "${it.family.uppercase()}-${it.colorId}" }) { row ->
                val tracked = row.minSealed != null
                ListItem(
                    headlineContent = { Text("${row.family} ${row.colorName}") },
                    supportingContent = { if (tracked) Text(if (row.minSealed!! > 0) "התראה כשיש פחות מ-${row.minSealed} סגורים" else "התראה רק כשנגמר") },
                    leadingContent = { ColorDot(row.colorHex) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tracked) Stepper(row.minSealed!!, { vm.setMin(row, it) }, min = 0, max = 20)
                            Switch(checked = tracked, onCheckedChange = { vm.setTracked(row, it) })
                        }
                    },
                )
            }
        }
    }
}
