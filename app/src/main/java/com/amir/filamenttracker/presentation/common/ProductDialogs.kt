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
import com.amir.filamenttracker.domain.resolveColorHex
import com.amir.filamenttracker.data.db.ColorEntity
import com.amir.filamenttracker.data.db.MaterialEntity
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.domain.COMMON_FINISH_TERMS
import com.amir.filamenttracker.domain.ColorHex
import com.amir.filamenttracker.domain.MORE_FINISH_TERMS
import com.amir.filamenttracker.domain.MULTI_COLOR_TERMS
import com.amir.filamenttracker.domain.colorParts
import com.amir.filamenttracker.domain.toggleColorPart
import com.amir.filamenttracker.domain.VARIANT_TERMS
import com.amir.filamenttracker.domain.hasTerm
import com.amir.filamenttracker.domain.isKnownTermsOnly
import com.amir.filamenttracker.domain.toggleTerm

data class NewProductInput(
    val brand: String,
    val materialFamily: String,
    val materialVariant: String,
    val materialFinish: String,
    val colorName: String,
    val colorHex: String,
    val weightGrams: Int,
    val note: String,
)

/**
 * Main material, sub-type, finish and color are typed as names: existing ones can be picked from
 * chips, and a new name creates a new material or color (CatalogRepository merges ones that already
 * exist). Sub-type and finish chips add or remove one known word, so several can be combined.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProductDialog(
    materials: List<MaterialEntity>,
    colors: List<ColorEntity>,
    brands: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (NewProductInput) -> Unit,
) {
    var brand by rememberSaveable { mutableStateOf("") }
    var family by rememberSaveable { mutableStateOf(materials.firstOrNull()?.family ?: "") }
    var variant by rememberSaveable { mutableStateOf("") }
    var finish by rememberSaveable { mutableStateOf("") }
    var moreFinishes by rememberSaveable { mutableStateOf(false) }
    var color by rememberSaveable { mutableStateOf("") }
    var hex by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("1000") }
    var note by rememberSaveable { mutableStateOf("") }

    val families = materials.map { it.family }.distinctBy { it.uppercase() }
    // Known words have their own chips; a free sub-type or finish already used in this family gets one too.
    val inFamily = materials.filter { it.family.equals(family.trim(), ignoreCase = true) }
    val freeVariants = inFamily.map { it.variant }.filter { it.isNotBlank() && !isKnownTermsOnly(it) }.distinctBy { it.uppercase() }
    val freeFinishes = inFamily.map { it.finish }.filter { it.isNotBlank() && !isKnownTermsOnly(it) }.distinctBy { it.uppercase() }
    val finishChips = COMMON_FINISH_TERMS + MORE_FINISH_TERMS.filter { moreFinishes || hasTerm(finish, it) }
    // A dual or tri color spool is one combined color: each chip adds or removes a part.
    val multiColor = MULTI_COLOR_TERMS.any { hasTerm(finish, it) }
    val colorChips = if (multiColor) colors.filter { colorParts(it.name).size == 1 } else colors
    fun toggleColor(c: ColorEntity) {
        color = toggleColorPart(color, c.name, colorChips.map { it.name })
        // The hue of each part, when every part has one; otherwise the name decides.
        val hexes = colorParts(color).map { part ->
            colorChips.firstOrNull { it.name.equals(part, ignoreCase = true) }.let { resolveColorHex(part, it?.hex) }
        }
        hex = if (hexes.size > 1 && hexes.all { it != null }) hexes.joinToString(" ") else ""
    }

    val weightValue = weight.toIntOrNull() ?: 0
    val hexValid = runCatching { ColorHex.normalize(hex) }.isSuccess
    // A free sub-type only makes sense inside its family: when the family changes, drop one the new family lacks.
    fun changeFamily(newFamily: String) {
        if (!isKnownTermsOnly(variant) && !newFamily.trim().equals(family.trim(), ignoreCase = true) &&
            materials.none {
                it.family.equals(newFamily.trim(), ignoreCase = true) && it.variant.equals(variant.trim(), ignoreCase = true)
            }
        ) {
            variant = ""
        }
        family = newFamily
    }
    val canSave = brand.isNotBlank() && family.isNotBlank() && color.isNotBlank() && weightValue > 0 && hexValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("מוצר חדש") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(brand, { brand = it }, label = { Text("מותג") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    brands.forEach { b ->
                        FilterChip(selected = brand == b, onClick = { brand = b }, label = { Text(b) })
                    }
                }
                OutlinedTextField(family, { changeFamily(it) }, label = { Text("חומר ראשי") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    families.forEach { f ->
                        FilterChip(selected = family.trim().equals(f, ignoreCase = true), onClick = { changeFamily(f) }, label = { Text(f) })
                    }
                }
                OutlinedTextField(variant, { variant = it }, label = { Text("תת-סוג, לא חובה") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    VARIANT_TERMS.forEach { t ->
                        FilterChip(selected = hasTerm(variant, t), onClick = { variant = toggleTerm(variant, t) }, label = { Text(t) })
                    }
                    freeVariants.forEach { v ->
                        FilterChip(selected = variant.trim().equals(v, ignoreCase = true), onClick = { variant = v }, label = { Text(v) })
                    }
                }
                OutlinedTextField(finish, { finish = it }, label = { Text("גימור, לא חובה") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    finishChips.forEach { t ->
                        FilterChip(selected = hasTerm(finish, t), onClick = { finish = toggleTerm(finish, t) }, label = { Text(t) })
                    }
                    freeFinishes.forEach { f ->
                        FilterChip(selected = finish.trim().equals(f, ignoreCase = true), onClick = { finish = f }, label = { Text(f) })
                    }
                    TextButton(onClick = { moreFinishes = !moreFinishes }) { Text(if (moreFinishes) "פחות גימורים" else "עוד גימורים") }
                }
                OutlinedTextField(
                    color, { color = it },
                    label = { Text(if (multiColor) "צבעים (סמן את כל הצבעים בסליל)" else "צבע") },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    colorChips.forEach { c ->
                        FilterChip(
                            selected = if (multiColor) colorParts(color).any { it.equals(c.name, ignoreCase = true) } else color == c.name,
                            onClick = {
                                if (multiColor) {
                                    toggleColor(c)
                                } else {
                                    color = c.name
                                    if (hex.isBlank()) hex = c.hex ?: ""
                                }
                            },
                            label = { Text(c.name) },
                            leadingIcon = { ColorDot(resolveColorHex(c.name, c.hex), size = 14.dp) },
                        )
                    }
                }
                OutlinedTextField(
                    hex, { hex = it },
                    label = { Text("גוון, לא חובה (למשל #1A2B3C)") },
                    singleLine = true,
                    isError = !hexValid,
                    trailingIcon = { ColorDot(resolveColorHex(color, hex)) },
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
                onClick = { onConfirm(NewProductInput(brand, family, variant, finish, color, hex, weightValue, note)) },
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
                        leadingContent = { ColorDot(resolveColorHex(p.colorName, p.colorHex)) },
                        modifier = Modifier.clickable { onPick(p) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
