package com.amir.filamenttracker.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.amir.filamenttracker.data.db.ColorEntity
import com.amir.filamenttracker.data.db.MaterialEntity
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.ColorHex

data class NewProductInput(
    val brand: String,
    val materialName: String,
    val colorName: String,
    val colorHex: String,
    val weightGrams: Int,
    val note: String,
)

/**
 * Material and color are typed as names: existing ones can be picked from chips, and a new
 * name creates a new material or color (CatalogRepository merges names that already exist).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProductDialog(
    materials: List<MaterialEntity>,
    colors: List<ColorEntity>,
    onDismiss: () -> Unit,
    onConfirm: (NewProductInput) -> Unit,
) {
    var brand by rememberSaveable { mutableStateOf("") }
    var material by rememberSaveable { mutableStateOf(materials.firstOrNull()?.name ?: "") }
    var color by rememberSaveable { mutableStateOf("") }
    var hex by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("1000") }
    var note by rememberSaveable { mutableStateOf("") }

    val weightValue = weight.toIntOrNull() ?: 0
    val hexValid = runCatching { ColorHex.normalize(hex) }.isSuccess
    val canSave = brand.isNotBlank() && material.isNotBlank() && color.isNotBlank() && weightValue > 0 && hexValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("מוצר חדש") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(brand, { brand = it }, label = { Text("מותג") }, singleLine = true)
                OutlinedTextField(material, { material = it }, label = { Text("חומר") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    materials.forEach { m ->
                        FilterChip(selected = material == m.name, onClick = { material = m.name }, label = { Text(m.name) })
                    }
                }
                OutlinedTextField(color, { color = it }, label = { Text("צבע") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    colors.forEach { c ->
                        FilterChip(
                            selected = color == c.name,
                            onClick = { color = c.name; if (hex.isBlank()) hex = c.hex ?: "" },
                            label = { Text(c.name) },
                            leadingIcon = { ColorDot(c.hex, size = 14.dp) },
                        )
                    }
                }
                OutlinedTextField(
                    hex, { hex = it },
                    label = { Text("גוון, לא חובה (למשל #1A2B3C)") },
                    singleLine = true,
                    isError = !hexValid,
                    trailingIcon = { ColorDot(hex) },
                )
                OutlinedTextField(
                    weight, { weight = it.filter(Char::isDigit) },
                    label = { Text("משקל בגרמים") },
                    singleLine = true,
                    isError = weightValue <= 0,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(note, { note = it }, label = { Text("הערה") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onConfirm(NewProductInput(brand, material, color, hex, weightValue, note)) },
            ) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
fun ProductPickerDialog(
    products: List<ProductRow>,
    onDismiss: () -> Unit,
    onPick: (ProductRow) -> Unit,
    onNewProduct: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("בחירת מוצר") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("+ מוצר חדש") },
                        modifier = Modifier.clickable(onClick = onNewProduct),
                    )
                }
                items(products, key = { it.productId }) { p ->
                    ListItem(
                        headlineContent = { Text(p.title) },
                        supportingContent = { Text("${p.weightGrams} ג'") },
                        leadingContent = { ColorDot(p.colorHex) },
                        modifier = Modifier.clickable { onPick(p) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
