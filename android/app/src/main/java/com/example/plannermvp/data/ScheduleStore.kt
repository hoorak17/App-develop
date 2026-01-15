package com.example.plannermvp.data

import androidx.compose.runtime.mutableStateListOf
import java.util.UUID

/**
 * MVP 1.0: 인메모리 스토어
 * - todayBlocks: 오늘 화면에서 실행/피드백
 * - tomorrowBlocks: 계획 화면에서 편집, 완료 시 today로 반영
 */
object ScheduleStore {

    // 어제 기록(샘플/고정). 추후 실제 저장으로 교체.
    val yesterdayBlocks: List<TimeBlock> = listOf(
        TimeBlock(id(), "수면", 0, 8 * 60, Category.SLEEP),
        TimeBlock(id(), "공부", 9 * 60, 12 * 60, Category.STUDY),
        TimeBlock(id(), "운동", 20 * 60, 21 * 60, Category.EXERCISE)
    )

    // "오늘" (실행 화면)
    val todayBlocks = mutableStateListOf(
        TimeBlock(id(), "수면", 0, 8 * 60, Category.SLEEP),
        TimeBlock(id(), "공부", 9 * 60, 12 * 60, Category.STUDY),
        TimeBlock(id(), "운동", 20 * 60, 21 * 60, Category.EXERCISE)
    )

    // "내일 계획" (계획 화면에서 편집)
    val tomorrowBlocks = mutableStateListOf<TimeBlock>()

    fun addTomorrowBlock(title: String, startMinute: Int, endMinute: Int, category: Category) {
        tomorrowBlocks.add(
            TimeBlock(
                id = id(),
                title = title.trim(),
                startMinute = startMinute,
                endMinute = endMinute,
                category = category
            )
        )
        sortTomorrow()
    }

    fun copyYesterdayToTomorrow(block: TimeBlock) {
        addTomorrowBlock(block.title, block.startMinute, block.endMinute, block.category)
    }

    fun updateTodayFeedback(blockId: String, feedback: Feedback?) {
        val idx = todayBlocks.indexOfFirst { it.id == blockId }
        if (idx >= 0) {
            todayBlocks[idx] = todayBlocks[idx].copy(feedback = feedback)
        }
    }

    fun finalizeTomorrowToToday() {
        // MVP 1.0: 내일 계획을 "오늘"로 덮어씀(흐름 확인용)
        todayBlocks.clear()
        todayBlocks.addAll(tomorrowBlocks)
        tomorrowBlocks.clear()
        sortToday()
    }

    private fun sortToday() {
        val sorted = todayBlocks.sortedBy { it.startMinute }
        todayBlocks.clear()
        todayBlocks.addAll(sorted)
    }

    private fun sortTomorrow() {
        val sorted = tomorrowBlocks.sortedBy { it.startMinute }
        tomorrowBlocks.clear()
        tomorrowBlocks.addAll(sorted)
    }

    private fun id(): String = UUID.randomUUID().toString()
}
