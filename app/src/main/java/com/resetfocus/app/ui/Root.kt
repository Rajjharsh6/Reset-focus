package com.resetfocus.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.resetfocus.app.MainViewModel

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val dests = listOf(
    Dest("dashboard", "Home", Icons.Default.Dashboard),
    Dest("focus", "Focus", Icons.Default.Timer),
    Dest("blocking", "Blocking", Icons.Default.Block),
    Dest("analytics", "Usage", Icons.Default.BarChart),
    Dest("recovery", "Recovery", Icons.Default.SelfImprovement),
)

@Composable
fun Root(vm: MainViewModel) {
    val prefs by vm.prefs.collectAsState()
    val p = prefs ?: return
    if (!p.onboarded) { OnboardingScreen(vm); return }

    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route != "settings") NavigationBar {
                dests.forEach { d ->
                    NavigationBarItem(
                        selected = route == d.route,
                        onClick = {
                            nav.navigate(d.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(d.icon, d.label) }, label = { Text(d.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, "dashboard", Modifier.padding(pad)) {
            composable("dashboard") { DashboardScreen(vm, nav) }
            composable("focus") { FocusScreen(vm) }
            composable("blocking") { BlockingScreen(vm) }
            composable("analytics") { AnalyticsScreen(vm) }
            composable("recovery") { RecoveryScreen(vm) }
            composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
        }
    }
}
