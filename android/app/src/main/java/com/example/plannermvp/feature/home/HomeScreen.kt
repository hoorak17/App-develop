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
import com.example.plannermvp.data.Category
import com.example.plannermvp.data.Feedback
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.TimeBlock
import com.example.plannermvp.data.toHHMM
import com.example.plannermvp.feature.plan.WheelTimeDialog

private const val GAP_UNIT_MIN = 60

sealed interface TimelineItem {
    data class Block(val block: TimeBlock) : TimelineItem
    data class Gap(val start: Int, val end: Int) : TimelineItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onGoSummary: () -> Unit
) {
    // 피드백 시트
    var selectedBlock by remember { mutableStateOf<TimeBlock?>(null) }
    var showFeedbackSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 빈 시간 클릭 → 일정 추가 다이얼(휠)
    var showAddDialog by remember { mutableStateOf(false) }
    var addStart by remember { mutableStateOf(9 * 60) }
    var addEnd by remember { mutableStateOf(10 * 60) }

    val blocks = ScheduleStore.todayBlocks.sortedBy { it.startMinute }
    val total = blocks.size
    val reviewed = blocks.count { it.feedback != null }

    val items = remember(blocks) { buildTimelineItems(blocks) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column {
                    Text("홈 (오늘)")
                    Text("일정 ${total}개 / 피드백 ${reviewed}개")
                }
            }
            Button(onClick = onGoSummary) { Text("오늘 요약") }
        }

        Text("타임라인 (블록=1칸, 빈 시간=1시간 칸)")

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                when (item) {
                    is TimelineItem.Block -> {
                        BlockCard(
                            block = item.block,
                            onClick = {
                                selectedBlock = item.block
                                showFeedbackSheet = true
                            }
                        )
                    }
                    is TimelineItem.Gap -> {
                        GapCard(
                            start = item.start,
                            end = item.end,
                            onClick = {
                                addStart = item.start
                                addEnd = (item.start + 60).coerceAtMost(item.end)
                                showAddDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // 빈 시간 일정 추가(휠 타임피커)
    if (showAddDialog) {
        WheelTimeDialog(
            title = "일정 추가",
            titleDefault = "",
            startDefaultMinute = addStart,
            endDefaultMinute = addEnd,
            onDismiss = { showAddDialog = false },
            onSave = { t, s, e ->
                ScheduleStore.addTodayBlock(
                    title = t,
                    startMinute = s,
                    endMinute = e,
                    category = guessCategory(t)
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
            FeedbackSheet(
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
private fun BlockCard(block: TimeBlock, onClick: () -> Unit) {
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

@Composable
private fun GapCard(start: Int, end: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Text("${start.toHHMM()} - ${end.toHHMM()}")
            Text("빈 시간 (눌러서 일정 추가)")
        }
    }
}

@Composable
private fun FeedbackSheet(
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

private fun buildTimelineItems(blocks: List<TimeBlock>): List<TimelineItem> {
    val sorted = blocks.sortedBy { it.startMinute }
    val result = mutableListOf<TimelineItem>()

    var cursor = 0
    for (b in sorted) {
        // 블록 앞 갭
        if (b.startMinute > cursor) {
            addGaps(result, cursor, b.startMinute)
        }
        // 블록은 1칸
        result.add(TimelineItem.Block(b))
        cursor = maxOf(cursor, b.endMinute)
    }
    // 끝 갭
    if (cursor < 24 * 60) {
        addGaps(result, cursor, 24 * 60)
    }
    return result
}

private fun addGaps(out: MutableList<TimelineItem>, start: Int, end: Int) {
    var s = start
    while (s < end) {
        val e = minOf(s + GAP_UNIT_MIN, end)
        out.add(TimelineItem.Gap(s, e))
        s = e
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
