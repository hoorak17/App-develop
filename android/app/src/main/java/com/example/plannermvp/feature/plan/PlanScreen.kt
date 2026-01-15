package com.example.plannermvp.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.example.plannermvp.data.toHHMM

@Composable
fun PlanScreen(
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    var dialogTitle by remember { mutableStateOf("") }
    var dialogStart by remember { mutableStateOf(9 * 60) }
    var dialogEnd by remember { mutableStateOf(10 * 60) }
    var dialogCategory by remember { mutableStateOf(Category.ETC) }
    var dialogOnSave by remember { mutableStateOf<(String, Int, Int) -> Unit>({ _, _, _ -> }) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("내일 계획 세우기")

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("뒤로(오늘요약)")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("어제 일정 (눌러서 시간 수정 후 추가)")
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
                Text("내일 일정(편집 중)")
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
    }

    if (showDialog) {
        WheelTimeDialog(
            title = "시간 설정",
            titleDefault = dialogTitle,
            startDefaultMinute = dialogStart,
            endDefaultMinute = dialogEnd,
            onDismiss = { showDialog = false },
            onSave = { t, s, e ->
                dialogOnSave(t, s, e)
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
