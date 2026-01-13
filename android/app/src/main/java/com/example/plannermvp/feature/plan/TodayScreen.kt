package com.example.plannermvp.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun TodayScreen(onGoSummary: () -> Unit) {
    Column {
        Text("Today")
        Button(onClick = onGoSummary) {
            Text("Go Summary")
        }
    }
}
