package com.example.plannermvp.feature.summary

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SummaryScreen(onGoPlan: () -> Unit) {
    Column {
        Text("Summary")
        Button(onClick = onGoPlan) {
            Text("Go Plan Next Day")
        }
    }
}
