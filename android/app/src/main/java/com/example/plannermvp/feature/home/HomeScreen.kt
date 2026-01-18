@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.plannermvp.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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

// ✅ 고정 높이(모든 블록/빈칸 동일 사이즈)
private val ROW_HEIGHT = 76.dp

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

// 색(단순 2톤 + 보조)
private val PastIvory = Color(0xFFF3E9D6)
private val FutureSky = Color(0xFFD7EFFF)
private val GapGray = Color(0xFFE6E6E6)
private val NowIndicator = Color(0xFF2B6CB0) // 진행중 표시용 진한색

private enum class BlockStatus { PAST, NOW, FUTURE }

private fun progressOfBlock(nowMin: Int, start: Int, end: Int): Float {
    if (end <= DAY_MIN) {
        if (nowMin <= start) return 0f
        if (nowMin >= end) return 1f
        return (nowMin - start).toFloat() / (end - start).toFloat()
    }
    val e2 = end % DAY_MIN
    return when {
        nowMin >= start -> {
            (nowMin - start).toFloat() / (DAY_MIN - start).toFloat()
        }
        nowMin < e2 -> {
            val total = (DAY_MIN - start) + e2
            val passed = (DAY_MIN - start) + nowMin
            passed.toFloat() / total.toFloat()
        }
        else -> 0f
    }.coerceIn(0f, 1f)
}

private fun statusOfBlock(nowMin: Int, start: Int, end: Int): BlockStatus {
    if (end <= DAY_MIN) {
        return when {
            nowMin < start -> BlockStatus.FUTURE
            nowMin >= end -> BlockStatus.PAST
            else -> BlockStatus.NOW
        }
    } else {
        val e2 = end % DAY_MIN
        val inNow = (nowMin in start until DAY_MIN) || (nowMin in 0 until e2)
        return when {
            inNow -> BlockStatus.NOW
            nowMin < start && nowMin >= e2 -> BlockStatus.FUTURE
            else -> BlockStatus.PAST
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onGoSummary: () -> Unit
) {
    val nowDate = ScheduleStore.nowDateTime().toLocalDate()
    val todayDateText = remember(nowDate) { formatKoreanDate(nowDate) }

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
    val nowMin = ScheduleStore.nowMinuteOfDay()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            // ✅ 상단 블럭: 날짜 + 줄바꿈 + 일정 개수
            Card(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { /* hidden */ },
                        onLongClick = { ScheduleStore.toggleDevMode() }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(todayDateText)
                    Text("일정 ${blocks.size}개")
                }
            }

            Button(onClick = onGoSummary) { Text("오늘 요약") }
        }

        // ✅ 개발자 모드 UI: 버튼이 줄바꿈 안 되도록 2열 배치 + 텍스트 고정
        if (ScheduleStore.devMode) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("시간 조정")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { ScheduleStore.devAdjustMinutes(-60) },
                            modifier = Modifier.weight(1f)
                        ) { OneLineText("-1h") }

                        OutlinedButton(
                            onClick = { ScheduleStore.devAdjustMinutes(+60) },
                            modifier = Modifier.weight(1f)
                        ) { OneLineText("+1h") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { ScheduleStore.devAdjustMinutes(-10) },
                            modifier = Modifier.weight(1f)
                        ) { OneLineText("-10m") }

                        OutlinedButton(
                            onClick = { ScheduleStore.devAdjustMinutes(+10) },
                            modifier = Modifier.weight(1f)
                        ) { OneLineText("+10m") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { ScheduleStore.devAdjustDays(-1) },
                            modifier = Modifier.weight(1f)
                        ) { OneLineText("-1d") }

                        OutlinedButton(
                            onClick = { ScheduleStore.devAdjustDays(+1) },
                            modifier = Modifier.weight(1f)
                        ) { OneLineText("+1d") }
                    }

                    Text("Dev 모드 종료(롤백): 상단 날짜 카드 롱프레스")
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                when (item) {
                    is TimelineItem.Block -> {
                        val b = item.block
                        val status = statusOfBlock(nowMin, b.startMinute, b.endMinute)
                        val progress = if (status == BlockStatus.NOW) progressOfBlock(nowMin, b.startMinute, b.endMinute) else 0f

                        BlockCard(
                            block = b,
                            status = status,
                            progress = progress,
                            onClick = {
                                selectedBlock = b
                                showFeedbackSheet = true
                            },
                            onLongClick = {
                                longPressedBlock = b
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
            // ✅ 저장 직후도 즉시 보이도록 최신 block을 다시 조회해서 Sheet에 반영
            val latest = ScheduleStore.todayBlocks.firstOrNull { it.id == selectedBlock!!.id } ?: selectedBlock!!

            FeedbackSheet(
                block = latest,
                onSave = { tags, memo ->
                    ScheduleStore.updateTodayFeedback(latest.id, tags, memo)
                    showFeedbackSheet = false
                },
                onClear = {
                    ScheduleStore.clearTodayFeedback(latest.id)
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(12.dp)) {
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
    status: BlockStatus,
    progress: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val baseColor = when (status) {
        BlockStatus.PAST -> PastIvory
        BlockStatus.FUTURE -> FutureSky
        BlockStatus.NOW -> FutureSky
    }

    // ✅ 피드백/메모는 "항상 한 줄 요약"으로 보여주되, 없으면 숨김
    val hasFb = block.feedbackTags.isNotEmpty() || block.feedbackMemo.isNotBlank()
    val tagsText = if (block.feedbackTags.isEmpty()) "" else block.feedbackTags.joinToString(", ")
    val memoText = block.feedbackMemo.trim()

    val fbSummary = when {
        tagsText.isNotBlank() && memoText.isNotBlank() -> "피드백: $tagsText | 메모: $memoText"
        tagsText.isNotBlank() -> "피드백: $tagsText"
        memoText.isNotBlank() -> "메모: $memoText"
        else -> ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(baseColor)) {

            // ✅ 진행중: 하늘색 위에 상아색이 진행률만큼 덮이는 방식
            if (status == BlockStatus.NOW) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxSize()
                        .background(PastIvory)
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {

                // 진행중 왼쪽 인디케이터
                if (status == BlockStatus.NOW) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .background(NowIndicator)
                            .padding(horizontal = 2.dp)
                    )
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                }

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = block.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatRange(block.startMinute, block.endMinute),
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )

                    // ✅ 여기서 바로 보이게: 1줄 요약(너무 길면 …)
                    if (hasFb) {
                        Text(
                            text = fbSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GapCard(start: Int, end: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .combinedClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GapGray)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("${start.toHHMM()} - ${end.toHHMM()}", maxLines = 1)
            Text("빈 시간 (눌러서 추가)", maxLines = 1, overflow = TextOverflow.Ellipsis)
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

    LaunchedEffect(block.id, block.feedbackMemo, block.feedbackTags) {
        memo = block.feedbackMemo
        tags = block.feedbackTags
    }

    val options = remember(block.category) { ScheduleStore.feedbackOptionsFor(block.category) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(12.dp)) {
        Text("피드백")
        Text("${block.title} (${formatRange(block.startMinute, block.endMinute)})")

        val half = (options.size + 1) / 2
        val row1 = options.take(half)
        val row2 = options.drop(half)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row1.forEach { label -> ToggleTagButton(label = label, current = tags) { tags = it } }
        }
        if (row2.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row2.forEach { label -> ToggleTagButton(label = label, current = tags) { tags = it } }
            }
        }

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

@Composable
private fun ToggleTagButton(
    label: String,
    current: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    val selected = label in current
    val text = if (selected) "[$label]" else label

    OutlinedButton(onClick = {
        onChange(if (selected) current - label else current + label)
    }) { OneLineText(text) }
}

// ✅ 버튼 텍스트 줄바꿈 방지
@Composable
private fun OneLineText(s: String) {
    Text(text = s, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
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
