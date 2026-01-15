package com.example.plannermvp.data

import androidx.compose.runtime.mutableStateListOf

object ScheduleStore {

    val todayBlocks = mutableStateListOf<TimeBlock>()
    val yesterdayBlocks = mutableStateListOf<TimeBlock>()
    val tomorrowBlocks = mutableStateListOf<TimeBlock>()

    init {
        if (todayBlocks.isEmpty()) {
            todayBlocks.add(TimeBlock(title = "수면", startMinute = 0, endMinute = 8 * 60, category = Category.SLEEP))
            todayBlocks.add(TimeBlock(title = "운동", startMinute = 18 * 60, endMinute = 19 * 60, category = Category.EXERCISE))
        }
        if (yesterdayBlocks.isEmpty()) {
            yesterdayBlocks.add(TimeBlock(title = "수면", startMinute = 0, endMinute = 8 * 60, category = Category.SLEEP))
            yesterdayBlocks.add(TimeBlock(title = "공부", startMinute = 10 * 60, endMinute = 12 * 60, category = Category.STUDY))
            yesterdayBlocks.add(TimeBlock(title = "운동", startMinute = 18 * 60, endMinute = 19 * 60, category = Category.EXERCISE))
        }
    }

    fun feedbackOptionsFor(category: Category): List<String> {
        return when (category) {
            Category.SLEEP -> listOf("개운함", "숙면", "뒤척임", "늦잠", "수면부족")
            Category.EXERCISE -> listOf("고강도", "유산소", "근력", "컨디션저하", "만족")
            Category.STUDY -> listOf("집중", "산만", "진도OK", "막힘", "복습필요")
            Category.ETC -> listOf("GOOD", "OKAY", "BAD", "FAIL")
        }
    }

    // --- Today CRUD ---
    fun addTodayBlock(title: String, startMinute: Int, endMinute: Int, category: Category) {
        todayBlocks.add(TimeBlock(title = title, startMinute = startMinute, endMinute = endMinute, category = category))
    }

    fun updateTodayBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category) {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = todayBlocks[idx]
            todayBlocks[idx] = old.copy(title = title, startMinute = startMinute, endMinute = endMinute, category = category)
        }
    }

    fun deleteTodayBlock(id: String) {
        todayBlocks.removeAll { it.id == id }
    }

    // ✅ 태그 + 메모를 함께 저장
    fun updateTodayFeedback(id: String, tags: Set<String>, memo: String) {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = todayBlocks[idx]
            todayBlocks[idx] = old.copy(
                feedbackTags = tags,
                feedbackMemo = memo
            )
        }
    }

    fun clearTodayFeedback(id: String) {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = todayBlocks[idx]
            todayBlocks[idx] = old.copy(feedbackTags = emptySet(), feedbackMemo = "")
        }
    }

    // --- Tomorrow CRUD ---
    fun addTomorrowBlock(title: String, startMinute: Int, endMinute: Int, category: Category) {
        tomorrowBlocks.add(TimeBlock(title = title, startMinute = startMinute, endMinute = endMinute, category = category))
    }

    fun updateTomorrowBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category) {
        val idx = tomorrowBlocks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = tomorrowBlocks[idx]
            tomorrowBlocks[idx] = old.copy(title = title, startMinute = startMinute, endMinute = endMinute, category = category)
        }
    }

    fun deleteTomorrowBlock(id: String) {
        tomorrowBlocks.removeAll { it.id == id }
    }

    fun finalizeTomorrowToToday() {
        // 어제를 오늘로 갱신
        yesterdayBlocks.clear()
        yesterdayBlocks.addAll(todayBlocks)

        // 오늘 = 내일(피드백은 초기화)
        todayBlocks.clear()
        todayBlocks.addAll(
            tomorrowBlocks.map { it.copy(feedbackTags = emptySet(), feedbackMemo = "") }
        )

        tomorrowBlocks.clear()
    }
}
