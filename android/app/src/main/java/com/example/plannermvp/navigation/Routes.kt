package com.example.plannermvp.navigation

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Summary : Route("summary")
    data object Plan : Route("plan")
}
