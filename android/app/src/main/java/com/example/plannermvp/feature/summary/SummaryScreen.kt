package com.example.plannermvp.feature.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.formatRange

@Composable
fun SummaryScreen(
    onBack: () -> Unit,
    onGoPlan: () -> Unit
) {
    val blocks = ScheduleStore.todayBlocks.sortedBy { it.startMinute }
    val total = blocks.size
    val reviewed = blocks.count { it.feedbackTags.isNotEmpty() || it.feedbackMemo.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("뒤로") }

            Button(onClick = {
                ScheduleStore.preparePlanningNextDay()
                onGoPlan()
            }) { Text("내일 계획 세우기") }
        }

        Text("오늘 요약")
        Text("일정 ${total}개 / 피드백 입력 ${reviewed}개")

        Text("일정별 피드백/메모")
        if (blocks.isEmpty()) {
            Text("오늘 일정이 없습니다.")
        } else {
            blocks.forEach { b ->
                val tags = if (b.feedbackTags.isEmpty()) "NONE" else b.feedbackTags.joinToString(", ")
                val memo = if (b.feedbackMemo.isBlank()) "없음" else b.feedbackMemo

                Text("- ${b.title} (${formatRange(b.startMinute, b.endMinute)})", modifier = Modifier.fillMaxWidth())
                Text("  태그: $tags")
                Text("  메모: $memo")
            }
        }
    }
}
