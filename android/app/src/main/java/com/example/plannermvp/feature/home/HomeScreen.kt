@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.plannermvp.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.plannermvp.data.Category
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.TimeBlock
import com.example.plannermvp.data.formatRange
import com.example.plannermvp.data.toHHMM
import com.example.plannermvp.feature.plan.WheelTimeDialog
import java.time.LocalDate

private const val DAY_MIN = 24 * 60
private const val GAP_UNIT_MIN = 60

sealed interface TimelineItem {
    data class Block(val block: TimeBlock) : TimelineItem
    data class Gap(val start: Int, val end: Int) : TimelineItem
}

private fun formatKoreanDate(d: LocalDate): String {
    val dow = when (d.dayOfWeek.value) {
        1 -> "월"
        2 -> "화"
        3 -> "수"
        4 -> "목"
        5 -> "금"
        6 -> "토"
        else -> "일"
    }
    return "${d.monthValue}월 ${d.dayOfMonth}일 ${dow}요일"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onGoSummary: () -> Unit
) {
    val todayDateText = remember { formatKoreanDate(LocalDate.now()) }

    var selectedBlock by remember { mutableStateOf<TimeBlock?>(null) }
    var showFeedbackSheet by remember { mutableStateOf(false) }
    val feedbackSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var longPressedBlock by remember { mutableStateOf<TimeBlock?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    val manageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showAddDialog by remember { mutableStateOf(false) }
    var addStart by remember { mutableStateOf(9 * 60) }
    var addEnd by remember { mutableStateOf(10 * 60) }

    var showEditDialog by remember { mutableStateOf(false) }

    val blocks = ScheduleStore.todayBlocks.sortedBy { it.startMinute }
    val items = remember(blocks) { buildTimelineItems(blocks) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text(todayDateText)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("홈 (오늘)")
                    Text("일정 ${blocks.size}개")
                }
            }
            Button(onClick = onGoSummary) { Text("오늘 요약") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                when (item) {
                    is TimelineItem.Block -> {
                        BlockCard(
                            block = item.block,
                            onClick = {
                                selectedBlock = item.block
                                showFeedbackSheet = true
                            },
                            onLongClick = {
                                longPressedBlock = item.block
                                showManageSheet = true
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

    if (showAddDialog) {
        WheelTimeDialog(
            title = "일정 추가",
            titleDefault = "",
            startDefaultMinute = addStart,
            endDefaultMinute = addEnd,
            onDismiss = { showAddDialog = false },
            onTrySave = { t, s, e ->
                ScheduleStore.addTodayBlock(
                    title = t,
                    startMinute = s,
                    endMinute = e,
                    category = guessCategory(t)
                )
            }
        )
    }

    if (showFeedbackSheet && selectedBlock != null) {
        ModalBottomSheet(
            onDismissRequest = { showFeedbackSheet = false },
            sheetState = feedbackSheetState
        ) {
            FeedbackSheet(
                block = selectedBlock!!,
                onSave = { tags, memo ->
                    ScheduleStore.updateTodayFeedback(selectedBlock!!.id, tags, memo)
                    showFeedbackSheet = false
                },
                onClear = {
                    ScheduleStore.clearTodayFeedback(selectedBlock!!.id)
                    showFeedbackSheet = false
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showManageSheet && longPressedBlock != null) {
        ModalBottomSheet(
            onDismissRequest = { showManageSheet = false },
            sheetState = manageSheetState
        ) {
            val b = longPressedBlock!!
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("일정 관리")
                Text("${b.title} (${formatRange(b.startMinute, b.endMinute)})")

                Button(
                    onClick = {
                        showManageSheet = false
                        showEditDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("수정") }

                OutlinedButton(
                    onClick = {
                        ScheduleStore.deleteTodayBlock(b.id)
                        showManageSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("삭제") }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showEditDialog && longPressedBlock != null) {
        val b = longPressedBlock!!
        WheelTimeDialog(
            title = "일정 수정",
            titleDefault = b.title,
            startDefaultMinute = b.startMinute,
            endDefaultMinute = b.endMinute,
            onDismiss = { showEditDialog = false },
            onTrySave = { t, s, e ->
                ScheduleStore.updateTodayBlock(
                    id = b.id,
                    title = t,
                    startMinute = s,
                    endMinute = e,
                    category = b.category
                )
            }
        )
    }
}

@Composable
private fun BlockCard(
    block: TimeBlock,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column {
            Text(block.title)
            Text(formatRange(block.startMinute, block.endMinute))
        }
    }
}

@Composable
private fun GapCard(start: Int, end: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
    ) {
        Column {
            Text("${start.toHHMM()} - ${end.toHHMM()}")
            Text("빈 시간 (눌러서 추가)")
        }
    }
}

@Composable
private fun FeedbackSheet(
    block: TimeBlock,
    onSave: (Set<String>, String) -> Unit,
    onClear: () -> Unit
) {
    var memo by remember { mutableStateOf(block.feedbackMemo) }
    var tags by remember { mutableStateOf(block.feedbackTags) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("피드백")
        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            label = { Text("메모") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(tags, memo) }) { Text("저장") }
            OutlinedButton(onClick = onClear) { Text("초기화") }
        }
    }
}

private fun buildTimelineItems(blocks: List<TimeBlock>): List<TimelineItem> {
    val result = mutableListOf<TimelineItem>()
    var cursor = 0

    for (b in blocks) {
        val start = b.startMinute.coerceIn(0, DAY_MIN)
        val end = b.endMinute.coerceAtMost(DAY_MIN)

        if (start > cursor) addGaps(result, cursor, start)
        result.add(TimelineItem.Block(b))
        cursor = maxOf(cursor, end)
    }
    if (cursor < DAY_MIN) addGaps(result, cursor, DAY_MIN)
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
        "수면" in t -> Category.SLEEP
        "운동" in t -> Category.EXERCISE
        "공부" in t -> Category.STUDY
        else -> Category.ETC
    }
}
