package com.example.plannermvp.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.plannermvp.data.formatRange

private sealed interface DialogMode {
    data object AddNew : DialogMode
    data class AddFromYesterday(val base: TimeBlock) : DialogMode
    data class EditTomorrow(val target: TimeBlock) : DialogMode
}

@Composable
fun PlanScreen(
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var dialogMode by remember { mutableStateOf<DialogMode>(DialogMode.AddNew) }

    val yesterday = ScheduleStore.yesterdayBlocks.toList()
    val tomorrow = ScheduleStore.tomorrowBlocks.sortedBy { it.startMinute }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("내일 계획 세우기")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("뒤로(오늘요약)")
            }
            Button(
                onClick = {
                    ScheduleStore.finalizeTomorrowToToday()
                    onDone()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("완료(홈)")
            }
        }

        // ✅ 스크롤 영역
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("어제 일정 (눌러서 시간 수정 후 추가)")
                        if (yesterday.isEmpty()) {
                            Text("어제 일정이 없습니다.")
                        } else {
                            yesterday.forEach { b ->
                                OutlinedButton(
                                    onClick = {
                                        dialogMode = DialogMode.AddFromYesterday(b)
                                        showDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${b.title}  ${formatRange(b.startMinute, b.endMinute)}")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        dialogMode = DialogMode.AddNew
                        showDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("새 일정 만들기") }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("내일 일정 (홈으로 가기 전까지 수정/삭제 가능)")
                        if (tomorrow.isEmpty()) {
                            Text("아직 없음")
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            items(tomorrow) { b ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("${b.title}  ${formatRange(b.startMinute, b.endMinute)}")

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    dialogMode = DialogMode.EditTomorrow(b)
                                    showDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("수정") }

                            OutlinedButton(
                                onClick = {
                                    // ✅ 여기! removeTomorrowBlock가 아니라 deleteTomorrowBlock
                                    ScheduleStore.deleteTomorrowBlock(b.id)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("삭제") }
                        }
                    }
                }
            }
        }
    }

    // ✅ 다이얼
    if (showDialog) {
        val (titleDefault, startDefault, endDefault) = when (val m = dialogMode) {
            DialogMode.AddNew -> Triple("", 9 * 60, 10 * 60)
            is DialogMode.AddFromYesterday -> Triple(m.base.title, m.base.startMinute, m.base.endMinute)
            is DialogMode.EditTomorrow -> Triple(m.target.title, m.target.startMinute, m.target.endMinute)
        }

        WheelTimeDialog(
            title = when (dialogMode) {
                DialogMode.AddNew -> "일정 추가"
                is DialogMode.AddFromYesterday -> "어제 일정 추가"
                is DialogMode.EditTomorrow -> "일정 수정"
            },
            titleDefault = titleDefault,
            startDefaultMinute = startDefault,
            endDefaultMinute = endDefault,
            onDismiss = { showDialog = false },
            onTrySave = { t, s, e ->
                when (val m = dialogMode) {
                    DialogMode.AddNew -> {
                        ScheduleStore.addTomorrowBlock(
                            title = t,
                            startMinute = s,
                            endMinute = e,
                            category = guessCategory(t)
                        )
                    }
                    is DialogMode.AddFromYesterday -> {
                        ScheduleStore.addTomorrowBlock(
                            title = t,
                            startMinute = s,
                            endMinute = e,
                            category = m.base.category
                        )
                    }
                    is DialogMode.EditTomorrow -> {
                        ScheduleStore.updateTomorrowBlock(
                            id = m.target.id,
                            title = t,
                            startMinute = s,
                            endMinute = e,
                            category = m.target.category
                        )
                    }
                }
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
