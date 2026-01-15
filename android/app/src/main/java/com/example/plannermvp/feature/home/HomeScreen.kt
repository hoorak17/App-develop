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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.plannermvp.data.Category
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.TimeBlock
import com.example.plannermvp.data.formatRange
import com.example.plannermvp.data.toHHMM
import com.example.plannermvp.feature.plan.WheelTimeDialog

private const val DAY_MIN = 24 * 60
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
    val reviewed = blocks.count { it.feedbackTags.isNotEmpty() || it.feedbackMemo.isNotBlank() }

    val items = remember(blocks) { buildTimelineItems(blocks) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("홈 (오늘)")
                    Text("일정 ${total}개 / 피드백 입력 ${reviewed}개")
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

    // 피드백 BottomSheet (여기서는 기존 방식 유지: tags/memo는 다른 화면에서 구현중이면 연동)
    if (showFeedbackSheet && selectedBlock != null) {
        ModalBottomSheet(
            onDismissRequest = { showFeedbackSheet = false },
            sheetState = sheetState
        ) {
            SimpleFeedbackSheet(
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
}

@Composable
private fun BlockCard(block: TimeBlock, onClick: () -> Unit) {
    val time = formatRange(block.startMinute, block.endMinute)
    val tags = if (block.feedbackTags.isEmpty()) "NONE" else block.feedbackTags.joinToString(", ")
    val memo = if (block.feedbackMemo.isBlank()) "없음" else block.feedbackMemo

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(block.title)
            Text(time)
            Text("태그: $tags")
            Text("메모: $memo")
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
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("${start.toHHMM()} - ${end.toHHMM()}")
            Text("빈 시간 (눌러서 일정 추가)")
        }
    }
}

/**
 * 최소 구현용: tags/memo 입력 UI
 * (추후 일정별 버튼 세트를 바꿀 예정이면 이 컴포저블만 확장하면 됨)
 */
@Composable
private fun SimpleFeedbackSheet(
    block: TimeBlock,
    onSave: (tags: Set<String>, memo: String) -> Unit,
    onClear: () -> Unit
) {
    var memo by remember { mutableStateOf(block.feedbackMemo) }
    var tags by remember { mutableStateOf(block.feedbackTags) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("피드백")
        Text("${block.title}  (${formatRange(block.startMinute, block.endMinute)})")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleTagButton("GOOD", tags) { tags = it }
            ToggleTagButton("OKAY", tags) { tags = it }
            ToggleTagButton("BAD", tags) { tags = it }
            ToggleTagButton("FAIL", tags) { tags = it }
        }

        androidx.compose.material3.OutlinedTextField(
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

@Composable
private fun ToggleTagButton(
    label: String,
    current: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    val selected = label in current
    val text = if (selected) "[$label]" else label
    OutlinedButton(onClick = {
        onChange(
            if (selected) current - label else current + label
        )
    }) { Text(text) }
}

/** 오늘 타임라인은 0:00~24:00만 그린다. 자정 넘김은 24:00까지만 채움 */
private fun buildTimelineItems(blocks: List<TimeBlock>): List<TimelineItem> {
    val sorted = blocks.sortedBy { it.startMinute }
    val result = mutableListOf<TimelineItem>()

    var cursor = 0
    for (b in sorted) {
        val blockStart = b.startMinute.coerceIn(0, DAY_MIN)
        val blockEndToday = b.endMinute.coerceAtMost(DAY_MIN) // ✅ 자정 넘김은 오늘 구간만

        // 블록 앞 갭
        if (blockStart > cursor) {
            addGaps(result, cursor, blockStart)
        }
        // 블록은 1칸(카드 1개)
        result.add(TimelineItem.Block(b))

        // cursor는 오늘 범위까지만 전진
        cursor = maxOf(cursor, blockEndToday)
    }
    // 끝 갭
    if (cursor < DAY_MIN) {
        addGaps(result, cursor, DAY_MIN)
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
