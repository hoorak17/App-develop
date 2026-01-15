package com.example.plannermvp.feature.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.plannermvp.data.Category
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.TimeBlock
import com.example.plannermvp.data.toHHMM

@Composable
fun PlanScreen(
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    // 다이얼 대상(추가 or 수정)
    var editingId by remember { mutableStateOf<String?>(null) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogStart by remember { mutableStateOf(9 * 60) }
    var dialogEnd by remember { mutableStateOf(10 * 60) }
    var dialogCategory by remember { mutableStateOf(Category.ETC) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("내일 계획 세우기")

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("뒤로(오늘요약)")
        }

        // 어제 목록: 눌러서 시간 수정 후 추가
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("어제 일정 (눌러서 시간 수정 후 추가)")
                ScheduleStore.yesterdayBlocks.forEach { b ->
                    OutlinedButton(
                        onClick = {
                            // '추가' 다이얼(시간/이름 수정 가능)
                            editingId = null
                            dialogTitle = b.title
                            dialogStart = b.startMinute
                            dialogEnd = b.endMinute
                            dialogCategory = b.category
                            showDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${b.title}  ${b.startMinute.toHHMM()}-${b.endMinute.toHHMM()}")
                    }
                }
            }
        }

        // 새 일정 만들기
        Button(
            onClick = {
                editingId = null
                dialogTitle = ""
                dialogStart = 9 * 60
                dialogEnd = 10 * 60
                dialogCategory = Category.ETC
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("새 일정 만들기") }

        // 내일 일정(편집 가능: 탭=수정, 삭제)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("내일 일정 (홈으로 가기 전까지 수정/삭제 가능)")
                if (ScheduleStore.tomorrowBlocks.isEmpty()) {
                    Text("아직 없음")
                } else {
                    ScheduleStore.tomorrowBlocks
                        .sortedBy { it.startMinute }
                        .forEach { b ->
                            TomorrowRow(
                                block = b,
                                onEdit = {
                                    editingId = b.id
                                    dialogTitle = b.title
                                    dialogStart = b.startMinute
                                    dialogEnd = b.endMinute
                                    dialogCategory = b.category
                                    showDialog = true
                                },
                                onDelete = { ScheduleStore.deleteTomorrowBlock(b.id) }
                            )
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
    }

    if (showDialog) {
        WheelTimeDialog(
            title = if (editingId == null) "일정 추가" else "일정 수정",
            titleDefault = dialogTitle,
            startDefaultMinute = dialogStart,
            endDefaultMinute = dialogEnd,
            onDismiss = { showDialog = false },
            onSave = { t, s, e ->
                val cat = if (editingId == null) guessCategory(t) else dialogCategory
                if (editingId == null) {
                    ScheduleStore.addTomorrowBlock(t, s, e, cat)
                } else {
                    ScheduleStore.updateTomorrowBlock(editingId!!, t, s, e, cat)
                }
                showDialog = false
            }
        )
    }
}

@Composable
private fun TomorrowRow(
    block: TimeBlock,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val time = "${block.startMinute.toHHMM()}-${block.endMinute.toHHMM()}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("• ${block.title}  $time", modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onDelete) { Text("삭제") }
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
