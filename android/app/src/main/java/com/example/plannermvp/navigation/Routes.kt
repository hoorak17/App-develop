package com.example.plannermvp.navigation

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Today : Route("today")
    data object Plan : Route("plan")
    data object Summary : Route("summary")
}
