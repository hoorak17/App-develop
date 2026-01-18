package com.example.plannermvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.navigation.AppNavHost
import com.example.plannermvp.ui.theme.PlannerMVPTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 앱 시작 시 저장소 초기화 (load + seed + devMode 대응)
        ScheduleStore.initPersistence(applicationContext)

        enableEdgeToEdge()
        setContent {
            PlannerMVPTheme {
                Scaffold { innerPadding ->
                    AppNavHost(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
