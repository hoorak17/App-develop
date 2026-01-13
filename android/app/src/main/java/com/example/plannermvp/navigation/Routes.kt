package com.example.plannermvp.navigation

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Today : Route("today")
    data object Summary : Route("summary")
    data object PlanNextDay : Route("plan_next_day")
}
