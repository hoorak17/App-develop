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
import androidx.compose.material3.Surface
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
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val DAY_MIN = 24 * 60
private const val GAP_UNIT_MIN = 60

private val ROW_HEIGHT = 76.dp
private val MARKER_HEIGHT = 28.dp

sealed interface TimelineItem {
    data class BlockPart(
        val block: TimeBlock,
        val partStart: Int,
        val partEnd: Int,
        val isContinuation: Boolean
    ) : TimelineItem

    data class Gap(val start: Int, val end: Int) : TimelineItem

    data class TimeMarker(val minute: Int) : TimelineItem
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

private val PastIvory = Color(0xFFF3E9D6)
private val FutureSky = Color(0xFFD7EFFF)
private val GapGray = Color(0xFFE6E6E6)
private val NowIndicator = Color(0xFF2B6CB0)
private val MarkerGray = Color(0xFFF2F2F2)
private val MarkerText = Color(0xFF666666)

private enum class BlockStatus { PAST, NOW, FUTURE }

private fun progressOfBlock(nowMin: Int, start: Int, end: Int): Float {
    if (end <= DAY_MIN) {
        if (nowMin <= start) return 0f
        if (nowMin >= end) return 1f
        return (nowMin - start).toFloat() / (end - start).toFloat()
    }
    val e2 = end % DAY_MIN
    return when {
        nowMin >= start -> (nowMin - start).toFloat() / (DAY_MIN - start).toFloat()
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
    // ✅ 진행 표시 / 시간 표시를 자동 갱신
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }

    // ✅ 핵심: todayBlocks를 "상태로 읽기" (toList()를 먼저 떠서 안정화)
    val blocks: List<TimeBlock> = remember(ScheduleStore.todayBlocks, tick) {
        // remember에 list 자체를 넣는 게 아니라, 아래처럼 toList()로 상태 읽기만 해도 recomposition 발생합니다.
        ScheduleStore.todayBlocks.toList().sortedBy { it.startMinute }
    }

    val nowDateTime = ScheduleStore.nowDateTime()
    val nowDate = nowDateTime.toLocalDate()
    val todayDateText = remember(nowDate) { formatKoreanDate(nowDate) }
    val nowMin = ScheduleStore.nowMinuteOfDay()

    // ✅ 핵심: items 캐시 금지(또는 derivedStateOf). 즉시 갱신 보장.
    val timelineItems: List<TimelineItem> = remember(blocks, ScheduleStore.devMode) {
        buildTimelineItems(blocks, includeMarkers = ScheduleStore.devMode)
    }

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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

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

                    if (ScheduleStore.devMode) {
                        val fmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
                        Text("DEV: ${ScheduleStore.nowDateTime().format(fmt)}")
                    }
                }
            }

            Button(onClick = onGoSummary) { Text("오늘 요약") }
        }

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

            // ✅ 핵심: 반드시 key 부여 (피드백/메모 즉시 반영 안정화)
            items(
                items = timelineItems,
                key = { it.timelineKey() }
            ) { item ->
                when (item) {
                    is TimelineItem.TimeMarker -> {
                        TimeMarkerRow(minute = item.minute, nowMin = nowMin)
                    }

                    is TimelineItem.BlockPart -> {
                        val b = item.block
                        val status = statusOfBlock(nowMin, b.startMinute, b.endMinute)
                        val progress = if (status == BlockStatus.NOW) {
                            progressOfBlock(nowMin, b.startMinute, b.endMinute)
                        } else 0f

                        BlockCard(
                            block = b,
                            isContinuation = item.isContinuation,
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
            // ✅ 시트는 항상 최신 상태를 다시 조회
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

private fun TimelineItem.timelineKey(): String {
    return when (this) {
        is TimelineItem.TimeMarker -> "m_${minute}"
        is TimelineItem.Gap -> "g_${start}_${end}"
        is TimelineItem.BlockPart -> "b_${block.id}_${partStart}_${partEnd}_${isContinuation}"
    }
}

@Composable
private fun TimeMarkerRow(minute: Int, nowMin: Int) {
    val isCurrentHour = (nowMin / 60) == (minute / 60)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(MARKER_HEIGHT),
        color = MarkerGray
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = minute.toHHMM(), color = MarkerText, maxLines = 1)
            if (isCurrentHour) {
                Text(text = "NOW ${nowMin.toHHMM()}", color = MarkerText, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BlockCard(
    block: TimeBlock,
    isContinuation: Boolean,
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

    // ✅ 피드백/메모 1줄 요약 (있을 때만 표시)
    val hasFb = block.feedbackTags.isNotEmpty() || block.feedbackMemo.isNotBlank()
    val tagsText = if (block.feedbackTags.isEmpty()) "" else block.feedbackTags.joinToString(", ")
    val memoText = block.feedbackMemo.trim()

    val fbSummary = when {
        tagsText.isNotBlank() && memoText.isNotBlank() -> "피드백: $tagsText | 메모: $memoText"
        tagsText.isNotBlank() -> "피드백: $tagsText"
        memoText.isNotBlank() -> "메모: $memoText"
        else -> ""
    }

    val titleText = if (isContinuation) "↪ ${block.title}" else block.title

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(baseColor)) {

            if (status == BlockStatus.NOW) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxSize()
                        .background(PastIvory)
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {

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
                    Text(text = titleText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = formatRange(block.startMinute, block.endMinute), maxLines = 1)

                    if (hasFb) {
                        Text(text = fbSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun OneLineText(s: String) {
    Text(text = s, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
}

private fun buildTimelineItems(blocks: List<TimeBlock>, includeMarkers: Boolean): List<TimelineItem> {
    val parts = mutableListOf<TimelineItem.BlockPart>()

    for (b in blocks) {
        val start = b.startMinute.coerceIn(0, DAY_MIN)
        val end = b.endMinute

        if (end <= DAY_MIN) {
            parts.add(
                TimelineItem.BlockPart(
                    block = b,
                    partStart = start,
                    partEnd = end.coerceIn(0, DAY_MIN),
                    isContinuation = false
                )
            )
        } else {
            parts.add(
                TimelineItem.BlockPart(
                    block = b,
                    partStart = start,
                    partEnd = DAY_MIN,
                    isContinuation = false
                )
            )
            val e2 = end % DAY_MIN
            if (e2 > 0) {
                parts.add(
                    TimelineItem.BlockPart(
                        block = b,
                        partStart = 0,
                        partEnd = e2.coerceIn(0, DAY_MIN),
                        isContinuation = true
                    )
                )
            }
        }
    }

    val sortedParts = parts.sortedBy { it.partStart }

    val result = mutableListOf<TimelineItem>()
    var cursor = 0

    for (p in sortedParts) {
        val s = p.partStart.coerceIn(0, DAY_MIN)
        val e = p.partEnd.coerceIn(0, DAY_MIN)

        if (s > cursor) addGaps(result, cursor, s)
        result.add(p)
        cursor = maxOf(cursor, e)
    }
    if (cursor < DAY_MIN) addGaps(result, cursor, DAY_MIN)

    if (!includeMarkers) return result

    val markers = (0..23).map { h -> TimelineItem.TimeMarker(h * 60) }

    return (result + markers).sortedWith { a, b ->
        val ta = startKey(a)
        val tb = startKey(b)
        when {
            ta != tb -> ta - tb
            else -> typeRank(a) - typeRank(b) // marker 먼저
        }
    }
}

private fun startKey(item: TimelineItem): Int = when (item) {
    is TimelineItem.TimeMarker -> item.minute
    is TimelineItem.Gap -> item.start
    is TimelineItem.BlockPart -> item.partStart
}

private fun typeRank(item: TimelineItem): Int = when (item) {
    is TimelineItem.TimeMarker -> 0
    is TimelineItem.Gap -> 1
    is TimelineItem.BlockPart -> 2
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
