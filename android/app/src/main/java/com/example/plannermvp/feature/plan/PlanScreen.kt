@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.plannermvp.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.plannermvp.data.Category
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.toHHMM

@Composable
fun PlanScreen(onDone: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogStart by remember { mutableStateOf(9 * 60) }
    var dialogEnd by remember { mutableStateOf(10 * 60) }
    var dialogCategory by remember { mutableStateOf(Category.ETC) }
    var dialogOnSave by remember { mutableStateOf<(String, Int, Int) -> Unit>({ _, _, _ -> }) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("내일 계획 세우기")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("어제 일정 목록 (눌러서 시간 수정 후 추가)")
                ScheduleStore.yesterdayBlocks.forEach { b ->
                    OutlinedButton(
                        onClick = {
                            dialogTitle = b.title
                            dialogStart = b.startMinute
                            dialogEnd = b.endMinute
                            dialogCategory = b.category
                            dialogOnSave = { title, s, e ->
                                ScheduleStore.addTomorrowBlock(title, s, e, dialogCategory)
                            }
                            showDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${b.title}  ${b.startMinute.toHHMM()}-${b.endMinute.toHHMM()}")
                    }
                }
            }
        }

        Button(
            onClick = {
                dialogTitle = ""
                dialogStart = 9 * 60
                dialogEnd = 10 * 60
                dialogCategory = Category.ETC
                dialogOnSave = { title, s, e ->
                    ScheduleStore.addTomorrowBlock(title, s, e, guessCategory(title))
                }
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("새 일정 만들기")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("내일 일정 (편집 중)")
                if (ScheduleStore.tomorrowBlocks.isEmpty()) {
                    Text("아직 없음")
                } else {
                    ScheduleStore.tomorrowBlocks.forEach { b ->
                        Text("- ${b.title}  ${b.startMinute.toHHMM()}-${b.endMinute.toHHMM()}")
                    }
                }
            }
        }

        Button(
            onClick = {
                ScheduleStore.finalizeTomorrowToToday()
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("완료하기 (홈으로)")
        }

        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("취소하고 홈으로")
        }
    }

    if (showDialog) {
        PlanTimeDialog(
            titleDefault = dialogTitle,
            startDefaultMinute = dialogStart,
            endDefaultMinute = dialogEnd,
            onDismiss = { showDialog = false },
            onSave = { title, s, e ->
                dialogOnSave(title, s, e)
                showDialog = false
            }
        )
    }
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

@Composable
private fun PlanTimeDialog(
    titleDefault: String,
    startDefaultMinute: Int,
    endDefaultMinute: Int,
    onDismiss: () -> Unit,
    onSave: (title: String, startMin: Int, endMin: Int) -> Unit
) {
    var title by remember { mutableStateOf(titleDefault) }

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
        title = { Text("시간 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("일정 이름") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("시작")
                TimePicker(state = startState)
                Text("종료")
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
