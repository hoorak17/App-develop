package com.example.plannermvp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.plannermvp.feature.home.HomeScreen
import com.example.plannermvp.feature.plan.PlanScreen
import com.example.plannermvp.feature.summary.SummaryScreen
import com.example.plannermvp.feature.today.TodayScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.Home.path,
        modifier = modifier
    ) {
        composable(Route.Home.path) {
            HomeScreen(
                onGoToday = { navController.navigate(Route.Today.path) },
                onGoPlan = { navController.navigate(Route.Plan.path) },
                onGoSummary = { navController.navigate(Route.Summary.path) }
            )
        }
        composable(Route.Today.path) {
            TodayScreen(
                onBack = { navController.popBackStack() },
                onGoPlan = { navController.navigate(Route.Plan.path) }
            )
        }
        composable(Route.Plan.path) {
            PlanScreen(
                onDone = {
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.Home.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Route.Summary.path) {
            SummaryScreen(
                onGoPlan = { navController.navigate(Route.Plan.path) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
