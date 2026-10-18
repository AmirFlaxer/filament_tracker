package com.amir.filamenttracker.presentation.catalog

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.data.db.ColorEntity
import com.amir.filamenttracker.data.db.MaterialEntity
import com.amir.filamenttracker.data.db.ProductRow
import com.amir.filamenttracker.data.db.title
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.CollectMessages
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.NewProductDialog
import com.amir.filamenttracker.presentation.common.RowActions
import com.amir.filamenttracker.presentation.common.SectionHeader
import com.amir.filamenttracker.presentation.common.TextFieldsDialog

private sealed interface CatalogDialog {
    data object NewProduct : CatalogDialog
    data object NewMaterial : CatalogDialog
    data object NewColor : CatalogDialog
    data class EditMaterial(val material: MaterialEntity) : CatalogDialog
    data class EditColor(val color: ColorEntity) : CatalogDialog
    data class EditProduct(val product: ProductRow) : CatalogDialog
}

@Composable
fun CatalogScreen(onBack: () -> Unit, vm: CatalogViewModel = hiltViewModel()) {
    val materials by vm.materials.collectAsStateWithLifecycle()
    val colors by vm.colors.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    CollectMessages(vm.messages, host)
    var dialog by remember { mutableStateOf<CatalogDialog?>(null) }

    Scaffold(
        topBar = { BackTopBar("קטלוג", onBack) },
        snackbarHost = { SnackbarHost(host) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = CatalogDialog.NewProduct },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("מוצר חדש") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            item { SectionHeader("מוצרים") }
            if (products.isEmpty()) item { Text("אין עדיין מוצרים", Modifier.padding(horizontal = 16.dp)) }
            items(products, key = { "p${it.productId}" }) { p ->
                ListItem(
                    headlineContent = { Text(p.title) },
                    supportingContent = {
                        Text(listOf("${p.weightGrams} ג'", p.note).filter { it.isNotBlank() }.joinToString(" · "))
                    },
                    leadingContent = { ColorDot(p.colorHex) },
                    trailingContent = {
                        RowActions(onEdit = { dialog = CatalogDialog.EditProduct(p) }, onRemove = { vm.removeProduct(p.productId) })
                    },
                )
            }
            item { SectionHeader("חומרים", actionLabel = "הוסף", onAction = { dialog = CatalogDialog.NewMaterial }) }
            items(materials, key = { "m${it.id}" }) { m ->
                ListItem(
                    headlineContent = { Text(m.name) },
                    trailingContent = {
                        RowActions(onEdit = { dialog = CatalogDialog.EditMaterial(m) }, onRemove = { vm.removeMaterial(m.id) })
                    },
                )
            }
            item { SectionHeader("צבעים", actionLabel = "הוסף", onAction = { dialog = CatalogDialog.NewColor }) }
            items(colors, key = { "c${it.id}" }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    leadingContent = { ColorDot(c.hex) },
                    trailingContent = {
                        RowActions(onEdit = { dialog = CatalogDialog.EditColor(c) }, onRemove = { vm.removeColor(c.id) })
                    },
                )
            }
        }
    }

    val close = { dialog = null }
    when (val d = dialog) {
        null -> Unit
        CatalogDialog.NewProduct -> NewProductDialog(materials, colors, onDismiss = close) {
            vm.createProduct(it)
            close()
        }
        CatalogDialog.NewMaterial -> TextFieldsDialog("חומר חדש", listOf("שם" to ""), close) { vm.addMaterial(it[0]) }
        CatalogDialog.NewColor -> TextFieldsDialog("צבע חדש", listOf("שם" to "", "גוון (למשל #1A2B3C)" to ""), close) {
            vm.addColor(it[0], it[1])
        }
        is CatalogDialog.EditMaterial -> TextFieldsDialog("עריכת חומר", listOf("שם" to d.material.name), close) {
            vm.renameMaterial(d.material.id, it[0])
        }
        is CatalogDialog.EditColor -> TextFieldsDialog(
            "עריכת צבע",
            listOf("שם" to d.color.name, "גוון (למשל #1A2B3C)" to (d.color.hex ?: "")),
            close,
        ) { vm.updateColor(d.color.id, it[0], it[1]) }
        is CatalogDialog.EditProduct -> TextFieldsDialog(
            "עריכת מוצר",
            listOf("מותג" to d.product.brand, "משקל בגרמים" to "${d.product.weightGrams}", "הערה" to d.product.note),
            close,
        ) { vm.updateProduct(d.product.productId, it[0], it[1], it[2]) }
    }
}
