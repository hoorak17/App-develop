package com.example.plannermvp.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.plannermvp.data.Category
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.TimeBlock
import com.example.plannermvp.data.toHHMM

@Composable
fun PlanScreen(onDone: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf("09:00") }
    var endText by remember { mutableStateOf("10:00") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("계획하기 (내일)")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("어제 수행한 일정목록 (눌러서 내일로 추가)")
                ScheduleStore.yesterdayBlocks.forEach { block ->
                    YesterdayBlockRow(block = block, onClick = { ScheduleStore.copyYesterdayToTomorrow(block) })
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("새로 만들기")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("일정 이름") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it },
                        label = { Text("시작 (HH:MM)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text("끝 (HH:MM)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val s = parseHHMM(startText) ?: return@Button
                        val e = parseHHMM(endText) ?: return@Button
                        if (title.isBlank()) return@Button
                        if (e <= s) return@Button

                        ScheduleStore.addTomorrowBlock(title, s, e, guessCategory(title))
                        title = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("추가하기")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("내일 일정 (편집 중)")
                if (ScheduleStore.tomorrowBlocks.isEmpty()) {
                    Text("아직 없음")
                } else {
                    ScheduleStore.tomorrowBlocks.forEach { b ->
                        Text("${b.title}  ${b.startMinute.toHHMM()}-${b.endMinute.toHHMM()}")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
            Text("그냥 홈으로")
        }
    }
}

@Composable
private fun YesterdayBlockRow(block: TimeBlock, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("${block.title}  ${block.startMinute.toHHMM()}-${block.endMinute.toHHMM()}")
    }
}

private fun parseHHMM(text: String): Int? {
    val t = text.trim()
    val parts = t.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23) return null
    if (m !in 0..59) return null
    return h * 60 + m
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
