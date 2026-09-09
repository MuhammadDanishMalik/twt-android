package com.talkswithtanha.twt.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.talkswithtanha.twt.core.designsystem.components.FloatingBottomNavigation
import com.talkswithtanha.twt.core.designsystem.components.NavigationItem
import com.talkswithtanha.twt.features.dashboard.ui.DashboardScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

    val items = listOf(
        NavigationItem("Home", Icons.Default.Home, "dashboard"),
        NavigationItem("Markets", Icons.Default.ShowChart, "markets"),
        NavigationItem("Signals", Icons.Default.WorkspacePremium, "signals"),
        NavigationItem("Profile", Icons.Default.Person, "profile")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("dashboard") {
                DashboardScreen()
            }
            composable("markets") {
                PlaceholderScreen("Markets")
            }
            composable("signals") {
                PlaceholderScreen("Signals")
            }
            composable("profile") {
                PlaceholderScreen("Profile")
            }
        }

        FloatingBottomNavigation(
            items = items,
            currentRoute = currentRoute,
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
    }
}
