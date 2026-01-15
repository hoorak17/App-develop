package com.example.plannermvp.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.plannermvp.data.Category
import com.example.plannermvp.data.Feedback
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.TimeBlock
import com.example.plannermvp.data.toHHMM

private const val SLOT_MIN = 30 // 30분 단위

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onGoPlan: () -> Unit) {
    // 피드백 시트
    var selectedBlock by remember { mutableStateOf<TimeBlock?>(null) }
    var showFeedbackSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 빈 시간 클릭 → 일정 추가 다이얼
    var showAddDialog by remember { mutableStateOf(false) }
    var addDefaultStart by remember { mutableStateOf(9 * 60) } // 클릭 슬롯 시작
    var addDefaultEnd by remember { mutableStateOf(10 * 60) }

    // 요약
    val total = ScheduleStore.todayBlocks.size
    val reviewed = ScheduleStore.todayBlocks.count { it.feedback != null }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column {
                    Text("오늘 요약")
                    Text("일정 ${total}개 / 피드백 ${reviewed}개")
                }
            }
            Button(onClick = onGoPlan) { Text("내일 계획") }
        }

        Text("오늘 타임라인 (빈 칸 클릭 → 일정 추가)")

        val slots = remember { buildSlots() }
        val blocks = ScheduleStore.todayBlocks.sortedBy { it.startMinute }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(slots) { slotStart ->
                val slotEnd = (slotStart + SLOT_MIN).coerceAtMost(24 * 60)
                val occupying = blocks.firstOrNull { b ->
                    // 슬롯이 블록 범위에 "겹치면" 점유로 간주
                    slotStart < b.endMinute && slotEnd > b.startMinute
                }

                if (occupying == null) {
                    EmptySlotRow(
                        startMinute = slotStart,
                        endMinute = slotEnd,
                        onClick = {
                            addDefaultStart = slotStart
                            addDefaultEnd = (slotStart + 60).coerceAtMost(24 * 60) // 기본 1시간
                            showAddDialog = true
                        }
                    )
                } else {
                    BlockRow(
                        block = occupying,
                        onClick = {
                            selectedBlock = occupying
                            showFeedbackSheet = true
                        }
                    )
                }
            }
        }
    }

    // 빈칸 추가 다이얼
    if (showAddDialog) {
        AddOrEditBlockDialog(
            titleDefault = "",
            startDefaultMinute = addDefaultStart,
            endDefaultMinute = addDefaultEnd,
            onDismiss = { showAddDialog = false },
            onSave = { title, startMin, endMin ->
                ScheduleStore.addTodayBlock(
                    title = title,
                    startMinute = startMin,
                    endMinute = endMin,
                    category = guessCategory(title)
                )
                showAddDialog = false
            }
        )
    }

    // 피드백 BottomSheet
    if (showFeedbackSheet && selectedBlock != null) {
        ModalBottomSheet(
            onDismissRequest = { showFeedbackSheet = false },
            sheetState = sheetState
        ) {
            FeedbackSheetContent(
                block = selectedBlock!!,
                onPick = { fb ->
                    ScheduleStore.updateTodayFeedback(selectedBlock!!.id, fb)
                    showFeedbackSheet = false
                },
                onClear = {
                    ScheduleStore.updateTodayFeedback(selectedBlock!!.id, null)
                    showFeedbackSheet = false
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmptySlotRow(startMinute: Int, endMinute: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Text("${startMinute.toHHMM()} - ${endMinute.toHHMM()}")
            Text("빈 시간 (눌러서 일정 추가)")
        }
    }
}

@Composable
private fun BlockRow(block: TimeBlock, onClick: () -> Unit) {
    val time = "${block.startMinute.toHHMM()} - ${block.endMinute.toHHMM()}"
    val fb = block.feedback?.name ?: "NONE"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Text(block.title)
            Text(time)
            Text("피드백: $fb")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOrEditBlockDialog(
    titleDefault: String,
    startDefaultMinute: Int,
    endDefaultMinute: Int,
    onDismiss: () -> Unit,
    onSave: (title: String, startMin: Int, endMin: Int) -> Unit
) {
    var title by remember { mutableStateOf(titleDefault) }

    // TimePicker는 hour/minute를 상태로 가진다
    val startState = rememberTimePickerState(
        initialHour = startDefaultMinute / 60,
        initialMinute = startDefaultMinute % 60,
        is24Hour = true
    )
    val endState = rememberTimePickerState(
        initialHour = endDefaultMinute / 60,
        initialMinute = endDefaultMinute % 60,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일정 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("일정 이름") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("시작 시간")
                TimePicker(state = startState)

                Text("종료 시간")
                TimePicker(state = endState)
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = startState.hour * 60 + startState.minute
                val e = endState.hour * 60 + endState.minute
                if (title.isBlank()) return@Button
                if (e <= s) return@Button
                onSave(title.trim(), s, e)
            }) { Text("저장") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("취소") }
        }
    )
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

private fun buildSlots(): List<Int> {
    val list = mutableListOf<Int>()
    var t = 0
    while (t < 24 * 60) {
        list.add(t)
        t += SLOT_MIN
    }
    return list
}

private fun guessCategory(title: String): Category {
    val t = title.lowercase()
    return when {
        "수면" in t || "sleep" in t -> Category.SLEEP
        "운동" in t || "workout" in t || "gym" in t -> Category.EXERCISE
        "공부" in t || "study" in t -> Category.STUDY
        else -> Category.ETC
    }
}
