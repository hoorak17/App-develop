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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plannermvp.data.Category
import com.example.plannermvp.data.TimeBlock
import com.example.plannermvp.data.formatRange
import com.example.plannermvp.data.toHHMM
import com.example.plannermvp.feature.plan.WheelTimeDialog
import java.time.LocalDate
import java.time.LocalDateTime

private const val DAY_MIN = 24 * 60
private const val GAP_UNIT_MIN = 60
private val ROW_MIN_HEIGHT = 76.dp

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

private fun formatHHMM(dt: LocalDateTime): String = "%02d:%02d".format(dt.hour, dt.minute)

private val PastIvory = Color(0xFFF3E9D6)
private val FutureSky = Color(0xFFD7EFFF)
private val GapGray = Color(0xFFE6E6E6)
private val NowIndicator = Color(0xFF2B6CB0)

private enum class BlockStatus { PAST, NOW, FUTURE }

private fun progressOfBlock(nowMin: Int, start: Int, end: Int): Float {
    // 일반(자정 안 넘김)
    if (end <= DAY_MIN) {
        if (nowMin <= start) return 0f
        if (nowMin >= end) return 1f
        return (nowMin - start).toFloat() / (end - start).toFloat()
    }
    // 자정 넘김(end > 1440)
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

private fun remainingMinutesOfBlock(nowMin: Int, start: Int, end: Int): Int {
    if (end <= DAY_MIN) {
        return (end - nowMin).coerceAtLeast(0)
    }
    val e2 = end % DAY_MIN
    return when {
        nowMin >= start -> (DAY_MIN - nowMin) + e2
        nowMin < e2 -> (e2 - nowMin)
        else -> 0
    }.coerceAtLeast(0)
}

private fun formatRemain(mins: Int): String {
    val m = mins.coerceAtLeast(0)
    val h = m / 60
    val r = m % 60
    return when {
        h > 0 && r > 0 -> "${h}h ${r}m 남음"
        h > 0 -> "${h}h 남음"
        else -> "${r}m 남음"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onGoSummary: () -> Unit,
    onGoPlan: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        if (!viewModel.devMode) viewModel.checkRolloverOnAppOpen()
    }
    LaunchedEffect(viewModel.devMode) {
        if (!viewModel.devMode) viewModel.checkRolloverOnAppOpen()
    }

    val nowDateTime = viewModel.nowDateTime()
    val nowDate = nowDateTime.toLocalDate()
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

    val blocks = viewModel.todayBlocks.sortedBy { it.startMinute }
    val items = remember(blocks) { buildTimelineItems(blocks) }
    val nowMin = viewModel.nowMinuteOfDay()

    var askReset1 by remember { mutableStateOf(false) }
    var askReset2 by remember { mutableStateOf(false) }

    if (viewModel.pendingRollover && !viewModel.devMode) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("어제 기록이 남아 있어요") },
            text = { Text("어제 기록을 마무리할까요?") },
            confirmButton = {
                Button(onClick = { viewModel.keepRolloverPending(); onGoSummary() }) { Text("마무리") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.confirmRolloverSkip() }) { Text("넘기기") }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = { viewModel.toggleDevMode() }
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

        if (!viewModel.devMode && viewModel.isPlanMissing()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "계획이 비어 있어요",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedButton(onClick = onGoPlan) { Text("계획 세우기") }
                }
            }
        }

        if (viewModel.devMode) {
            val devNow = viewModel.nowDateTime()
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${formatKoreanDate(devNow.toLocalDate())}  ${formatHHMM(devNow)}")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { viewModel.devAdjustMinutes(-60) }, modifier = Modifier.weight(1f)) {
                            OneLineText("-1h")
                        }
                        OutlinedButton(onClick = { viewModel.devAdjustMinutes(+60) }, modifier = Modifier.weight(1f)) {
                            OneLineText("+1h")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { viewModel.devAdjustMinutes(-10) }, modifier = Modifier.weight(1f)) {
                            OneLineText("-10m")
                        }
                        OutlinedButton(onClick = { viewModel.devAdjustMinutes(+10) }, modifier = Modifier.weight(1f)) {
                            OneLineText("+10m")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { viewModel.devAdjustDays(-1) }, modifier = Modifier.weight(1f)) {
                            OneLineText("-1d")
                        }
                        OutlinedButton(onClick = { viewModel.devAdjustDays(+1) }, modifier = Modifier.weight(1f)) {
                            OneLineText("+1d")
                        }
                    }

                    OutlinedButton(
                        onClick = { askReset1 = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("전체 초기화") }
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = items, key = {
                when (it) {
                    is TimelineItem.Block -> it.block.id
                    is TimelineItem.Gap -> "gap_${it.start}_${it.end}"
                }
            }) { item ->
                when (item) {
                    is TimelineItem.Block -> {
                        val b = item.block
                        val status = statusOfBlock(nowMin, b.startMinute, b.endMinute)
                        val progress = if (status == BlockStatus.NOW) progressOfBlock(nowMin, b.startMinute, b.endMinute) else 0f

                        BlockCard(
                            block = b,
                            status = status,
                            progress = progress,
                            nowMin = nowMin,
                            onClick = { selectedBlock = b; showFeedbackSheet = true },
                            onLongClick = { longPressedBlock = b; showManageSheet = true }
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

    if (askReset1) {
        AlertDialog(
            onDismissRequest = { askReset1 = false },
            title = { Text("전체 초기화") },
            text = { Text("모든 기록이 삭제됩니다.") },
            confirmButton = { Button(onClick = { askReset1 = false; askReset2 = true }) { Text("계속") } },
            dismissButton = { OutlinedButton(onClick = { askReset1 = false }) { Text("취소") } }
        )
    }
    if (askReset2) {
        AlertDialog(
            onDismissRequest = { askReset2 = false },
            title = { Text("정말 초기화할까요?") },
            text = { Text("되돌릴 수 없습니다.") },
            confirmButton = { Button(onClick = { askReset2 = false; viewModel.resetAll() }) { Text("초기화") } },
            dismissButton = { OutlinedButton(onClick = { askReset2 = false }) { Text("취소") } }
        )
    }

    if (showAddDialog) {
        WheelTimeDialog(
            title = "일정 추가",
            titleDefault = "",
            startDefaultMinute = addStart,
            endDefaultMinute = addEnd,
            onDismiss = { showAddDialog = false },
            onTrySave = { t, s, e ->
                viewModel.addTodayBlock(
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
            val latest = viewModel.latestBlock(selectedBlock!!.id) ?: selectedBlock!!
            FeedbackSheet(
                block = latest,
                options = viewModel.feedbackOptionsFor(latest.category),
                onSave = { tags, memo ->
                    viewModel.updateTodayFeedback(latest.id, tags, memo)
                    showFeedbackSheet = false
                },
                onClear = {
                    viewModel.clearTodayFeedback(latest.id)
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
                    onClick = { showManageSheet = false; showEditDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("수정") }

                OutlinedButton(
                    onClick = { viewModel.deleteTodayBlock(b.id); showManageSheet = false },
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
                viewModel.updateTodayBlock(
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
    nowMin: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val baseColor = when (status) {
        BlockStatus.PAST -> PastIvory
        BlockStatus.FUTURE -> FutureSky
        BlockStatus.NOW -> FutureSky
    }

    val hasFb = block.feedbackTags.isNotEmpty() || block.feedbackMemo.isNotBlank()
    val tagsText = if (block.feedbackTags.isEmpty()) "" else block.feedbackTags.joinToString(", ")
    val memoText = block.feedbackMemo.trim()
    val fbSummary = when {
        tagsText.isNotBlank() && memoText.isNotBlank() -> "$tagsText | $memoText"
        tagsText.isNotBlank() -> tagsText
        memoText.isNotBlank() -> memoText
        else -> ""
    }

    val remainText = if (status == BlockStatus.NOW) {
        formatRemain(remainingMinutesOfBlock(nowMin, block.startMinute, block.endMinute))
    } else ""

    val frac = progress.coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // ✅ 레이아웃에 의존하지 않고, 실제 픽셀 폭으로 사각형을 직접 그림 → “안 차오름” 재발 방지
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor)
                .drawBehind {
                    if (status == BlockStatus.NOW && frac > 0f) {
                        val w = size.width * frac
                        drawRect(
                            color = PastIvory,
                            size = Size(w, size.height)
                        )
                    }
                }
        ) {
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
                    Text(block.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatRange(block.startMinute, block.endMinute), maxLines = 1, overflow = TextOverflow.Clip)

                    if (status == BlockStatus.NOW) {
                        Text(remainText, maxLines = 1, overflow = TextOverflow.Clip)
                    }

                    if (hasFb) {
                        Text(fbSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            .height(ROW_MIN_HEIGHT)
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
            Text("빈 시간", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FeedbackSheet(
    block: TimeBlock,
    options: List<String>,
    onSave: (Set<String>, String) -> Unit,
    onClear: () -> Unit
) {
    var memo by remember { mutableStateOf(block.feedbackMemo) }
    var tags by remember { mutableStateOf(block.feedbackTags) }

    LaunchedEffect(block.id, block.feedbackMemo, block.feedbackTags) {
        memo = block.feedbackMemo
        tags = block.feedbackTags
    }

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
