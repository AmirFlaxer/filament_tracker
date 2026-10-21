package com.amir.filamenttracker.presentation.orders

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.amir.filamenttracker.presentation.common.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** "Expected arrival" button with a date picker and a clear action. Optional, so null is a valid value. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpectedDateField(expectedAt: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { open = true }) {
            Text(expectedAt?.let { "צפי הגעה: ${it.formatDate()}" } ?: "צפי הגעה, לא חובה")
        }
        if (expectedAt != null) TextButton(onClick = { onChange(null) }) { Text("נקה") }
    }
    if (open) {
        // The picker works in UTC midnight of the chosen day, both ways.
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = expectedAt?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("אישור") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("ביטול") } },
        ) { DatePicker(state = dateState) }
    }
}
