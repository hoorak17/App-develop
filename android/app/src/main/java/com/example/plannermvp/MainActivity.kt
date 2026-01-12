package com.example.plannermvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.plannermvp.ui.theme.PlannerMVPTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlannerMVPTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNav(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AppNav(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(onGoToday = { navController.navigate("today") })
        }
        composable("today") {
            TodayScreen(onGoSummary = { navController.navigate("summary") })
        }
        composable("summary") {
            SummaryScreen(onGoPlan = { navController.navigate("plan") })
        }
        composable("plan") {
            PlanNextDayScreen(onGoHome = { navController.navigate("home") })
        }
    }
}

@Composable
fun HomeScreen(onGoToday: () -> Unit) {
    Column {
        Text("Home")
        Button(onClick = onGoToday) {
            Text("Go Today")
        }
    }
}

@Composable
fun TodayScreen(onGoSummary: () -> Unit) {
    Column {
        Text("Today")
        Button(onClick = onGoSummary) {
            Text("Go Summary")
        }
    }
}

@Composable
fun SummaryScreen(onGoPlan: () -> Unit) {
    Column {
        Text("Summary")
        Button(onClick = onGoPlan) {
            Text("Go Plan Next Day")
        }
    }
}

@Composable
fun PlanNextDayScreen(onGoHome: () -> Unit) {
    Column {
        Text("Plan Next Day")
        Button(onClick = onGoHome) {
            Text("Back to Home")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHome() {
    PlannerMVPTheme {
        HomeScreen {}
    }
}
