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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plannermvp.data.Category
import com.example.plannermvp.data.TimeBlock
import com.example.plannermvp.data.formatRange

private sealed interface DialogMode {
    data object AddNew : DialogMode
    data class EditTomorrow(val target: TimeBlock) : DialogMode
}

private enum class AddSourceTab { YESTERDAY, NEW, LOAD }

@Composable
fun PlanScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: PlanViewModel = viewModel()
) {
    var showDialog by remember { mutableStateOf(false) }
    var dialogMode by remember { mutableStateOf<DialogMode>(DialogMode.AddNew) }

    // ✅ 탭 순서/용어 = 어제일정 / 신규일정 / 불러오기
    var tab by remember { mutableStateOf(AddSourceTab.YESTERDAY) }

    val yesterday = viewModel.yesterdayBlocks.toList()
    val tomorrow = viewModel.tomorrowBlocks.sortedBy { it.startMinute }

    // ✅ 히스토리: remember로 굳히지 말고 매번 최신 상태 반영
    val historyDates = viewModel.historyDayList(limit = 14)
    var selectedHistoryDate by remember { mutableStateOf<String?>(null) }

    // ✅ 목록 변화 시 선택값 보정
    LaunchedEffect(historyDates) {
        selectedHistoryDate = when {
            historyDates.isEmpty() -> null
            selectedHistoryDate == null -> historyDates.first()
            selectedHistoryDate !in historyDates -> historyDates.first()
            else -> selectedHistoryDate
        }
    }

    val historyBlocks =
        if (selectedHistoryDate == null) emptyList()
        else viewModel.blocksOfHistoryDay(selectedHistoryDate!!)

    // 즉시 추가 실패 메시지(겹침 등)
    var quickAddError by remember { mutableStateOf("") }

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
                    viewModel.finalizeTomorrowToToday()
                    onDone()
                },
                modifier = Modifier.weight(1f)
            ) { Text("완료(홈)") }
        }

        // 탭
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { tab = AddSourceTab.YESTERDAY; quickAddError = "" },
                modifier = Modifier.weight(1f)
            ) { Text(if (tab == AddSourceTab.YESTERDAY) "[어제일정]" else "어제일정") }

            OutlinedButton(
                onClick = { tab = AddSourceTab.NEW; quickAddError = "" },
                modifier = Modifier.weight(1f)
            ) { Text(if (tab == AddSourceTab.NEW) "[신규일정]" else "신규일정") }

            OutlinedButton(
                onClick = { tab = AddSourceTab.LOAD; quickAddError = "" },
                modifier = Modifier.weight(1f)
            ) { Text(if (tab == AddSourceTab.LOAD) "[불러오기]" else "불러오기") }
        }

        if (quickAddError.isNotBlank()) {
            Text(quickAddError)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // -------------------------
            // 추가 영역 (탭에 따라)
            // -------------------------
            when (tab) {
                AddSourceTab.YESTERDAY -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("어제일정 (누르면 즉시 추가)")
                                if (yesterday.isEmpty()) {
                                    Text("어제 일정이 없습니다.")
                                } else {
                                    yesterday.forEach { b ->
                                        OutlinedButton(
                                            onClick = {
                                                quickAddError = ""
                                                val ok = viewModel.addTomorrowBlock(
                                                    title = b.title,
                                                    startMinute = b.startMinute,
                                                    endMinute = b.endMinute,
                                                    category = b.category
                                                )
                                                if (!ok) quickAddError = "추가 실패: 다른 일정과 시간이 겹칩니다."
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
                }

                AddSourceTab.NEW -> {
                    item {
                        Button(
                            onClick = {
                                dialogMode = DialogMode.AddNew
                                showDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("새 일정 만들기") }
                    }
                }

                AddSourceTab.LOAD -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("불러오기 (날짜 선택 → 일정 누르면 즉시 추가)")

                                if (historyDates.isEmpty()) {
                                    Text("히스토리가 없습니다. 홈→요약→계획 흐름을 한 번 거치면 누적됩니다.")
                                } else {
                                    // ✅ 날짜 선택 버튼들: Column spacing으로 간격 처리(Spacer 남발 제거)
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        historyDates.forEach { d ->
                                            OutlinedButton(
                                                onClick = { selectedHistoryDate = d },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text(if (d == selectedHistoryDate) "[$d]" else d) }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("선택 날짜: ${selectedHistoryDate ?: "-"}")

                                    if (historyBlocks.isEmpty()) {
                                        Text("선택 날짜에 일정이 없습니다.")
                                    } else {
                                        historyBlocks.forEach { b ->
                                            OutlinedButton(
                                                onClick = {
                                                    quickAddError = ""
                                                    val ok = viewModel.addTomorrowBlock(
                                                        title = b.title,
                                                        startMinute = b.startMinute,
                                                        endMinute = b.endMinute,
                                                        category = b.category
                                                    )
                                                    if (!ok) quickAddError = "추가 실패: 다른 일정과 시간이 겹칩니다."
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
                    }
                }
            }

            // -------------------------
            // 내일 일정 목록
            // -------------------------
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("내일 일정 (수정/삭제 가능)")
                        if (tomorrow.isEmpty()) Text("아직 없음")
                    }
                }
            }

            // ✅ key 부여: 삭제/정렬 시 UI 안정성 향상
            items(items = tomorrow, key = { it.id }) { b ->
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
                                onClick = { viewModel.deleteTomorrowBlock(b.id) },
                                modifier = Modifier.weight(1f)
                            ) { Text("삭제") }
                        }
                    }
                }
            }
        }
    }

    // -------------------------
    // 다이얼 (신규/수정)
    // -------------------------
    if (showDialog) {
        val (titleDefault, startDefault, endDefault) = when (val m = dialogMode) {
            DialogMode.AddNew -> Triple("", 9 * 60, 10 * 60)
            is DialogMode.EditTomorrow -> Triple(m.target.title, m.target.startMinute, m.target.endMinute)
        }

        WheelTimeDialog(
            title = when (dialogMode) {
                DialogMode.AddNew -> "일정 추가"
                is DialogMode.EditTomorrow -> "일정 수정"
            },
            titleDefault = titleDefault,
            startDefaultMinute = startDefault,
            endDefaultMinute = endDefault,
            onDismiss = { showDialog = false },
            onTrySave = { t, s, e ->
                when (val m = dialogMode) {
                    DialogMode.AddNew -> {
                        viewModel.addTomorrowBlock(
                            title = t,
                            startMinute = s,
                            endMinute = e,
                            category = guessCategory(t)
                        )
                    }
                    is DialogMode.EditTomorrow -> {
                        viewModel.updateTomorrowBlock(
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
