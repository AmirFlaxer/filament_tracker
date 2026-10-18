package com.amir.filamenttracker.presentation.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amir.filamenttracker.presentation.common.BackTopBar
import com.amir.filamenttracker.presentation.common.ColorDot
import com.amir.filamenttracker.presentation.common.statusText

@Composable
fun ColorSpoolsScreen(onBack: () -> Unit, onSpoolClick: (Long) -> Unit, vm: ColorSpoolsViewModel = hiltViewModel()) {
    val spools by vm.spools.collectAsStateWithLifecycle()
    val title = spools.firstOrNull()?.let { "${it.materialFamily} ${it.colorName}" } ?: "סלילים"

    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(spools, key = { it.spoolId }) { spool ->
                ListItem(
                    headlineContent = { Text("${spool.brand} · ${spool.materialName}") },
                    supportingContent = { Text(spool.statusText()) },
                    leadingContent = { ColorDot(spool.colorHex) },
                    modifier = Modifier.clickable { onSpoolClick(spool.spoolId) },
                )
            }
        }
    }
}
