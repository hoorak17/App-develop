package com.example.plannermvp.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun HomeScreen(onGoToday: () -> Unit) {
    Column {
        Text("Home")
        Button(onClick = onGoToday) {
            Text("Go Today")
        }
    }
}
