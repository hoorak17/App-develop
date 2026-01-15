package com.example.plannermvp.feature.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.plannermvp.data.Feedback
import com.example.plannermvp.data.ScheduleStore

@Composable
fun SummaryScreen(
    onGoPlan: () -> Unit,
    onBack: () -> Unit
) {
    val blocks = ScheduleStore.todayBlocks
    val total = blocks.size
    val good = blocks.count { it.feedback == Feedback.GOOD }
    val okay = blocks.count { it.feedback == Feedback.OKAY }
    val bad = blocks.count { it.feedback == Feedback.BAD }
    val fail = blocks.count { it.feedback == Feedback.FAIL }
    val none = blocks.count { it.feedback == null }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("뒤로") }
            Button(onClick = onGoPlan) { Text("다음 일정 계획하기") }
        }

        Text("하루 요약")
        Text("총 일정: $total")
        Text("GOOD: $good / OKAY: $okay / BAD: $bad / FAIL: $fail / NONE: $none")

        Text("오늘 블록 목록")
        blocks.sortedBy { it.startMinute }.forEach { b ->
            val fb = b.feedback?.name ?: "NONE"
            Text("- ${b.title} : $fb")
        }
    }
}
