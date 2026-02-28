package com.wirewhisper.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.wirewhisper.ui.detail.FlowDetailScreen
import com.wirewhisper.ui.history.HistoryScreen
import com.wirewhisper.ui.now.NowScreen
import com.wirewhisper.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

// ── Route definitions ──────────────────────────────────────────

@Serializable object NowRoute
@Serializable object HistoryRoute
@Serializable object SettingsRoute
@Serializable data class FlowDetailRoute(val flowId: Long)

enum class TopLevelRoute(
    val label: String,
    val icon: ImageVector,
    val route: Any,
) {
    Now("Now", Icons.Default.Sensors, NowRoute),
    History("History", Icons.Default.History, HistoryRoute),
    Settings("Settings", Icons.Default.Settings, SettingsRoute),
}

// ── Navigation host ────────────────────────────────────────────

@Composable
fun WireWhisperNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelRoute.entries.forEach { route ->
                    NavigationBarItem(
                        icon = { Icon(route.icon, contentDescription = route.label) },
                        label = { Text(route.label) },
                        selected = currentDestination?.hasRoute(route.route::class) == true,
                        onClick = {
                            navController.navigate(route.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NowRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<NowRoute> {
                NowScreen(
                    onFlowClick = { flowId ->
                        navController.navigate(FlowDetailRoute(flowId))
                    }
                )
            }
            composable<HistoryRoute> {
                HistoryScreen(
                    onFlowClick = { flowId ->
                        navController.navigate(FlowDetailRoute(flowId))
                    }
                )
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
            composable<FlowDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<FlowDetailRoute>()
                FlowDetailScreen(
                    flowId = route.flowId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
