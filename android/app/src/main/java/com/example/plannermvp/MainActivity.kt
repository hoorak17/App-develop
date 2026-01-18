package com.example.plannermvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.plannermvp.navigation.AppNavHost
import com.example.plannermvp.ui.theme.PlannerMVPTheme
import com.example.plannermvp.data.ScheduleStore


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ScheduleStore.initPersistence(applicationContext) // ✅ 이 줄

        enableEdgeToEdge()
        setContent {
            PlannerMVPTheme {
                Scaffold { innerPadding ->
                    AppNavHost(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

}

