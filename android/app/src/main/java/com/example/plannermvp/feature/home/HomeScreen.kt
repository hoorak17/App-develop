package com.example.plannermvp.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.plannermvp.data.ScheduleStore

@Composable
fun HomeScreen(
    onGoToday: () -> Unit,
    onGoPlan: () -> Unit,
    onGoSummary: () -> Unit
) {
    val total = ScheduleStore.todayBlocks.size
    val reviewed = ScheduleStore.todayBlocks.count { it.feedback != null }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("홈")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("오늘 요약")
                Text(text = "일정 ${total}개 중 ${reviewed}개 피드백 완료")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onGoToday, modifier = Modifier.fillMaxWidth()) {
            Text("오늘 보기")
        }
        Button(onClick = onGoPlan, modifier = Modifier.fillMaxWidth()) {
            Text("내일 계획 세우기")
        }
        Button(onClick = onGoSummary, modifier = Modifier.fillMaxWidth()) {
            Text("하루 요약 보기")
        }
    }
}
