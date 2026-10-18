package com.amir.filamenttracker.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amir.filamenttracker.presentation.ams.AmsScreen
import com.amir.filamenttracker.presentation.catalog.CatalogScreen
import com.amir.filamenttracker.presentation.common.NoInsets
import com.amir.filamenttracker.presentation.inventory.AddExistingScreen
import com.amir.filamenttracker.presentation.inventory.ColorSpoolsScreen
import com.amir.filamenttracker.presentation.inventory.InventoryScreen
import com.amir.filamenttracker.presentation.spool.SpoolDetailScreen
import com.amir.filamenttracker.presentation.targets.TargetsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.INVENTORY, "מלאי", Icons.Outlined.Inventory2),
    Tab(Routes.AMS, "AMS", Icons.Outlined.ViewModule),
    Tab(Routes.ORDERS, "הזמנות", Icons.Outlined.LocalShipping),
    Tab(Routes.CONSUMPTION, "תצרוכת", Icons.Outlined.BarChart),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentTab = tabs.firstOrNull { it.route == backStack?.destination?.route }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        // Secondary screens bring their own Scaffold and insets. Here only the bars take insets.
        contentWindowInsets = NoInsets,
        topBar = {
            if (currentTab != null) {
                TopAppBar(
                    title = { Text(currentTab.label) },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "תפריט")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("קטלוג") },
                                onClick = { menuOpen = false; nav.navigate(Routes.CATALOG) },
                            )
                            DropdownMenuItem(
                                text = { Text("שומר במלאי") },
                                onClick = { menuOpen = false; nav.navigate(Routes.TARGETS) },
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.INVENTORY, modifier = Modifier.padding(padding)) {
            composable(Routes.INVENTORY) {
                InventoryScreen(
                    onColorClick = { materialId, colorId -> nav.navigate(Routes.colorSpools(materialId, colorId)) },
                    onAddExisting = { nav.navigate(Routes.ADD_EXISTING) },
                )
            }
            composable(Routes.AMS) { AmsScreen(onSpoolClick = { nav.navigate(Routes.spool(it)) }) }
            composable(Routes.ORDERS) { PlaceholderScreen("הזמנות") }
            composable(Routes.CONSUMPTION) { PlaceholderScreen("תצרוכת") }
            composable(Routes.CATALOG) { CatalogScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.TARGETS) { TargetsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.ADD_EXISTING) { AddExistingScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.NEW_ORDER) { PlaceholderScreen("הזמנה חדשה") }
            composable(
                Routes.SPOOL,
                arguments = listOf(navArgument("spoolId") { type = NavType.LongType }),
            ) { SpoolDetailScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.ORDER,
                arguments = listOf(navArgument("orderId") { type = NavType.LongType }),
            ) { PlaceholderScreen("הזמנה") }
            composable(
                Routes.COLOR_SPOOLS,
                arguments = listOf(
                    navArgument("materialId") { type = NavType.LongType },
                    navArgument("colorId") { type = NavType.LongType },
                ),
            ) { ColorSpoolsScreen(onBack = { nav.popBackStack() }, onSpoolClick = { nav.navigate(Routes.spool(it)) }) }
        }
    }
}

/** Removed in Task 15, once every route has its real screen. */
@Composable
private fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title) }
}
