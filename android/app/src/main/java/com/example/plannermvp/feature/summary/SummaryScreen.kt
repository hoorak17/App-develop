package com.example.plannermvp.feature.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plannermvp.data.formatRange

@Composable
fun SummaryScreen(
    onBack: () -> Unit,
    onGoPlan: () -> Unit,
    viewModel: SummaryViewModel = viewModel()
) {
    val blocks = viewModel.todayBlocks.sortedBy { it.startMinute }
    val (total, reviewed) = viewModel.summaryCounts(blocks)

    var showForceCloseConfirm by remember { mutableStateOf(false) }

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
                viewModel.preparePlanningNextDay()
                onGoPlan()
            }) { Text("내일 계획 세우기") }
        }

        // (G) 오늘 강제 종료
        OutlinedButton(onClick = { showForceCloseConfirm = true }, modifier = Modifier.fillMaxWidth()) {
            Text("오늘 강제 종료")
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

    if (showForceCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showForceCloseConfirm = false },
            title = { Text("오늘 강제 종료") },
            text = { Text("오늘을 종료하고 다음 날로 넘깁니다. 진행하시겠습니까?") },
            confirmButton = {
                Button(onClick = {
                    val requiresPlan = viewModel.manualForceCloseTodayRequiresPlan()
                    showForceCloseConfirm = false
                    if (requiresPlan) onGoPlan()
                }) { Text("종료") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showForceCloseConfirm = false }) { Text("취소") }
            }
        )
    }
}
