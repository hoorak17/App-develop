package com.example.plannermvp.feature.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun PlanNextDayScreen(onGoHome: () -> Unit) {
    Column {
        Text("Plan Next Day")
        Button(onClick = onGoHome) {
            Text("Back to Home")
        }
    }
}
