package com.garagepi.telemetry.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.garagepi.telemetry.ui.dashboard.DashboardScreen
import com.garagepi.telemetry.ui.history.HistoryScreen
import com.garagepi.telemetry.ui.settings.SettingsScreen

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun GarageNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == ROUTE_DASHBOARD } == true,
                    onClick = { navController.navigate(ROUTE_DASHBOARD) { launchSingleTop = true } },
                    icon = { Icon(Icons.Filled.Speed, contentDescription = null) },
                    label = { Text("Live") },
                )
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == ROUTE_HISTORY } == true,
                    onClick = { navController.navigate(ROUTE_HISTORY) { launchSingleTop = true } },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text("History") },
                )
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == ROUTE_SETTINGS } == true,
                    onClick = { navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true } },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_DASHBOARD) { DashboardScreen() }
            composable(ROUTE_HISTORY) { HistoryScreen() }
            composable(ROUTE_SETTINGS) { SettingsScreen() }
        }
    }
}
