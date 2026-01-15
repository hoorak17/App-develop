package com.example.plannermvp.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.TimeBlock
import com.example.plannermvp.data.toHHMM
import com.example.plannermvp.feature.plan.WheelTimeDialog

private const val GAP_UNIT_MIN = 60

sealed interface TimelineItem {
    data class Block(val block: TimeBlock) : TimelineItem
    data class Gap(val start: Int, val end: Int) : TimelineItem
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onGoSummary: () -> Unit
) {
    // 피드백 시트
    var selectedBlock by remember { mutableStateOf<TimeBlock?>(null) }
    var showFeedbackSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 롱프레스 메뉴(수정/삭제)
    var longPressedBlock by remember { mutableStateOf<TimeBlock?>(null) }
    var showEditMenu by remember { mutableStateOf(false) }

    // 홈에서 일정 수정 다이얼(휠)
    var showEditDialog by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }
    var editStart by remember { mutableStateOf(9 * 60) }
    var editEnd by remember { mutableStateOf(10 * 60) }
    var editCategory by remember { mutableStateOf(Category.ETC) }

    // 빈 시간 클릭 → 일정 추가
    var showAddDialog by remember { mutableStateOf(false) }
    var addStart by remember { mutableStateOf(9 * 60) }
    var addEnd by remember { mutableStateOf(10 * 60) }

    val blocks = ScheduleStore.todayBlocks.sortedBy { it.startMinute }
    val total = blocks.size
    val reviewed = blocks.count { it.feedbackTags.isNotEmpty() }
    val items = remember(blocks) { buildTimelineItems(blocks) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column {
                    Text("홈 (오늘)")
                    Text("일정 ${total}개 / 피드백 입력 ${reviewed}개")
                }
            }
            Button(onClick = onGoSummary) { Text("오늘 요약") }
        }

        Text("타임라인 (탭=피드백, 길게=수정/삭제)")

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
                            onLongPress = {
                                longPressedBlock = item.block
                                showEditMenu = true
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

    // 빈 시간: 일정 추가
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

    // 롱프레스 메뉴 다이얼(수정/삭제 선택)
    if (showEditMenu && longPressedBlock != null) {
        AlertDialog(
            onDismissRequest = { showEditMenu = false },
            title = { Text("일정 관리") },
            text = { Text("${longPressedBlock!!.title}  ${longPressedBlock!!.startMinute.toHHMM()}-${longPressedBlock!!.endMinute.toHHMM()}") },
            confirmButton = {
                Button(onClick = {
                    // 수정 다이얼 열기
                    val b = longPressedBlock!!
                    editId = b.id
                    editTitle = b.title
                    editStart = b.startMinute
                    editEnd = b.endMinute
                    editCategory = b.category
                    showEditMenu = false
                    showEditDialog = true
                }) { Text("수정") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        // 삭제
                        ScheduleStore.deleteTodayBlock(longPressedBlock!!.id)
                        showEditMenu = false
                    }) { Text("삭제") }
                    OutlinedButton(onClick = { showEditMenu = false }) { Text("취소") }
                }
            }
        )
    }

    // 홈에서 일정 수정(휠)
    if (showEditDialog) {
        WheelTimeDialog(
            title = "일정 수정",
            titleDefault = editTitle,
            startDefaultMinute = editStart,
            endDefaultMinute = editEnd,
            onDismiss = { showEditDialog = false },
            onSave = { t, s, e ->
                ScheduleStore.updateTodayBlock(
                    id = editId,
                    title = t,
                    startMinute = s,
                    endMinute = e,
                    category = editCategory
                )
                showEditDialog = false
            }
        )
    }

    // 피드백 BottomSheet(복수 선택)
    if (showFeedbackSheet && selectedBlock != null) {
        ModalBottomSheet(
            onDismissRequest = { showFeedbackSheet = false },
            sheetState = sheetState
        ) {
            FeedbackSheetMulti(
                block = selectedBlock!!,
                onSave = { tags ->
                    ScheduleStore.updateTodayFeedbackTags(selectedBlock!!.id, tags)
                    showFeedbackSheet = false
                },
                onClear = {
                    ScheduleStore.updateTodayFeedbackTags(selectedBlock!!.id, emptySet())
                    showFeedbackSheet = false
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockCard(
    block: TimeBlock,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val time = "${block.startMinute.toHHMM()} - ${block.endMinute.toHHMM()}"
    val tags = if (block.feedbackTags.isEmpty()) "NONE" else block.feedbackTags.joinToString(", ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Column {
            Text(block.title)
            Text(time)
            Text("피드백: $tags")
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
private fun FeedbackSheetMulti(
    block: TimeBlock,
    onSave: (Set<String>) -> Unit,
    onClear: () -> Unit
) {
    val options = ScheduleStore.feedbackOptionsFor(block.category)
    var selected by remember(block.id) { mutableStateOf(block.feedbackTags.toMutableSet()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("피드백(복수 선택 가능)")
        Text("${block.title}  (${block.startMinute.toHHMM()} - ${block.endMinute.toHHMM()})")

        // 버튼 토글들 (MVP: OutlinedButton로 토글)
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tag ->
                    val picked = selected.contains(tag)
                    OutlinedButton(
                        onClick = {
                            selected = selected.toMutableSet().apply {
                                if (picked) remove(tag) else add(tag)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (picked) "✓ $tag" else tag)
                    }
                }
            }
        }

        Button(onClick = { onSave(selected.toSet()) }, modifier = Modifier.fillMaxWidth()) {
            Text("저장")
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
        if (b.startMinute > cursor) addGaps(result, cursor, b.startMinute)
        result.add(TimelineItem.Block(b)) // 블록은 1칸
        cursor = maxOf(cursor, b.endMinute)
    }
    if (cursor < 24 * 60) addGaps(result, cursor, 24 * 60)
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
