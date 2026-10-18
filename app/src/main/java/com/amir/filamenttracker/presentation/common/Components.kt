package com.amir.filamenttracker.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

/** Tab screens sit inside the shell Scaffold, whose bars already handle the system insets. */
val NoInsets = WindowInsets(0, 0, 0, 0)

/** Never throws: anything that is not exactly six hex digits gives null. */
fun parseHexOrNull(hex: String?): Color? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    if (digits.length != 6 || !digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return Color(0xFF000000 or digits.toLong(16))
}

@Composable
fun ColorDot(hex: String?, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val fill = parseHexOrNull(hex) ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
            }
        },
        actions = actions,
    )
}

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun RowActions(onEdit: () -> Unit, onRemove: () -> Unit) {
    Row {
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "עריכה") }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "הסרה") }
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 0, max: Int = 50) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(value - 1) }, enabled = value > min) {
            Icon(Icons.Default.Remove, contentDescription = "פחות")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < max) {
            Icon(Icons.Default.Add, contentDescription = "יותר")
        }
    }
}

@Composable
fun ConfirmDialog(text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** A dialog of labelled single-line fields. [fields] is label to initial value. */
@Composable
fun TextFieldsDialog(
    title: String,
    fields: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val values = remember { fields.map { it.second }.toMutableStateList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { i, (label, _) ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { values[i] = it },
                        label = { Text(label) },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(values.toList()); onDismiss() }) { Text("שמור") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
fun CollectMessages(messages: Flow<String>, host: SnackbarHostState) {
    LaunchedEffect(messages) { messages.collect { host.showSnackbar(it) } }
}
