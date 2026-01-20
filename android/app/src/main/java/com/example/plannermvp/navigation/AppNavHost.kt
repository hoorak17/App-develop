package com.example.plannermvp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.feature.home.HomeScreen
import com.example.plannermvp.feature.plan.PlanScreen
import com.example.plannermvp.feature.summary.SummaryScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // ✅ 앱 시작 시 persistence 초기화 (1회만 수행됨)
    LaunchedEffect(Unit) {
        ScheduleStore.initPersistence(context)
    }

    NavHost(
        navController = navController,
        startDestination = Route.Home.path,
        modifier = modifier
    ) {
        composable(Route.Home.path) {
            HomeScreen(
                onGoSummary = { navController.navigate(Route.Summary.path) }
            )
        }
        composable(Route.Summary.path) {
            SummaryScreen(
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
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
