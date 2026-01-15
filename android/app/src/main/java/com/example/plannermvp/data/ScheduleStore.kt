package com.example.plannermvp.data

import androidx.compose.runtime.mutableStateListOf
import java.util.UUID

object ScheduleStore {

    // 어제 기록(샘플)
    val yesterdayBlocks: List<TimeBlock> = listOf(
        TimeBlock(id(), "수면", 0, 8 * 60, Category.SLEEP),
        TimeBlock(id(), "공부", 9 * 60, 12 * 60, Category.STUDY),
        TimeBlock(id(), "운동", 20 * 60, 21 * 60, Category.EXERCISE)
    )

    // 홈 화면에 표시되는 "오늘"
    val todayBlocks = mutableStateListOf(
        TimeBlock(id(), "수면", 0, 8 * 60, Category.SLEEP),
        TimeBlock(id(), "공부", 9 * 60, 12 * 60, Category.STUDY),
        TimeBlock(id(), "운동", 20 * 60, 21 * 60, Category.EXERCISE)
    )

    // 계획 화면에서 편집하는 "내일"
    val tomorrowBlocks = mutableStateListOf<TimeBlock>()

    fun addTodayBlock(title: String, startMinute: Int, endMinute: Int, category: Category) {
        todayBlocks.add(TimeBlock(id(), title.trim(), startMinute, endMinute, category))
        sortToday()
    }

    fun addTomorrowBlock(title: String, startMinute: Int, endMinute: Int, category: Category) {
        tomorrowBlocks.add(TimeBlock(id(), title.trim(), startMinute, endMinute, category))
        sortTomorrow()
    }

    fun updateTodayFeedback(blockId: String, feedback: Feedback?) {
        val idx = todayBlocks.indexOfFirst { it.id == blockId }
        if (idx >= 0) todayBlocks[idx] = todayBlocks[idx].copy(feedback = feedback)
    }

    fun finalizeTomorrowToToday() {
        todayBlocks.clear()
        todayBlocks.addAll(tomorrowBlocks.sortedBy { it.startMinute })
        tomorrowBlocks.clear()
    }

    private fun sortToday() {
        val sorted = todayBlocks.sortedBy { it.startMinute }
        todayBlocks.clear(); todayBlocks.addAll(sorted)
    }

    private fun sortTomorrow() {
        val sorted = tomorrowBlocks.sortedBy { it.startMinute }
        tomorrowBlocks.clear(); tomorrowBlocks.addAll(sorted)
    }

    private fun id(): String = UUID.randomUUID().toString()
}
