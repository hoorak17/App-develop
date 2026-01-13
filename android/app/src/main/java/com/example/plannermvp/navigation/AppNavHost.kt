package com.example.plannermvp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.plannermvp.feature.home.HomeScreen
import com.example.plannermvp.feature.plan.PlanNextDayScreen
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
            HomeScreen(onGoToday = { navController.navigate(Route.Today.path) })
        }
        composable(Route.Today.path) {
            TodayScreen(onGoSummary = { navController.navigate(Route.Summary.path) })
        }
        composable(Route.Summary.path) {
            SummaryScreen(onGoPlan = { navController.navigate(Route.PlanNextDay.path) })
        }
        composable(Route.PlanNextDay.path) {
            PlanNextDayScreen(
                onGoHome = {
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.Home.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
