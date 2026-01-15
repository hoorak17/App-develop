package com.example.plannermvp.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.plannermvp.data.Feedback
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.TimeBlock
import com.example.plannermvp.data.toHHMM

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onBack: () -> Unit,
    onGoPlan: () -> Unit
) {
    var selected by remember { mutableStateOf<TimeBlock?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("뒤로") }
            Button(onClick = onGoPlan) { Text("내일 계획") }
        }

        Text("Today")

        ScheduleStore.todayBlocks
            .sortedBy { it.startMinute }
            .forEach { block ->
                TimeBlockCard(
                    block = block,
                    onClick = {
                        selected = block
                        showSheet = true
                    }
                )
            }
    }

    if (showSheet && selected != null) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            FeedbackSheetContent(
                block = selected!!,
                onPick = { fb ->
                    ScheduleStore.updateTodayFeedback(selected!!.id, fb)
                    showSheet = false
                },
                onClear = {
                    ScheduleStore.updateTodayFeedback(selected!!.id, null)
                    showSheet = false
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TimeBlockCard(block: TimeBlock, onClick: () -> Unit) {
    val time = "${block.startMinute.toHHMM()} - ${block.endMinute.toHHMM()}"
    val fb = block.feedback?.name ?: "NONE"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(block.title)
            Text(time)
            Text("피드백: $fb")
        }
    }
}

@Composable
private fun FeedbackSheetContent(
    block: TimeBlock,
    onPick: (Feedback) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("피드백")
        Text("${block.title}  (${block.startMinute.toHHMM()} - ${block.endMinute.toHHMM()})")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPick(Feedback.GOOD) }) { Text("GOOD") }
            Button(onClick = { onPick(Feedback.OKAY) }) { Text("OKAY") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPick(Feedback.BAD) }) { Text("BAD") }
            Button(onClick = { onPick(Feedback.FAIL) }) { Text("FAIL") }
        }

        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text("피드백 지우기")
        }
    }
}
