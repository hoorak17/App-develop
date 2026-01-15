package com.example.plannermvp.data

import androidx.compose.runtime.mutableStateListOf

object ScheduleStore {

    val todayBlocks = mutableStateListOf<TimeBlock>()
    val yesterdayBlocks = mutableStateListOf<TimeBlock>()
    val tomorrowBlocks = mutableStateListOf<TimeBlock>()

    private var isAdvancing: Boolean = false
    private var seededOnce: Boolean = false

    init {
        seedIfNeeded()
    }

    private fun seedIfNeeded() {
        if (seededOnce) return
        seededOnce = true

        // ✅ 앱 실행 시 기본 일정: 수면/공부/운동
        if (todayBlocks.isEmpty()) {
            todayBlocks.add(TimeBlock(title = "수면", startMinute = 0, endMinute = 8 * 60, category = Category.SLEEP))
            todayBlocks.add(TimeBlock(title = "공부", startMinute = 10 * 60, endMinute = 12 * 60, category = Category.STUDY))
            todayBlocks.add(TimeBlock(title = "운동", startMinute = 18 * 60, endMinute = 19 * 60, category = Category.EXERCISE))
        }
    }

    // ---------------------------
    // Planning flow
    // ---------------------------
    fun preparePlanningNextDay() {
        // "어제" = 방금까지의 "오늘" 스냅샷
        yesterdayBlocks.clear()
        yesterdayBlocks.addAll(todayBlocks.map { it.copy() })

        // 계획 화면 진입 시 내일 계획은 새로 작성(원하면 유지로 바꿀 수 있음)
        tomorrowBlocks.clear()
    }

    fun finalizeTomorrowToToday() {
        if (isAdvancing) return
        isAdvancing = true
        try {
            // yesterday <- today
            yesterdayBlocks.clear()
            yesterdayBlocks.addAll(todayBlocks.map { it.copy() })

            // today <- tomorrow (피드백 초기화)
            todayBlocks.clear()
            todayBlocks.addAll(tomorrowBlocks.map { it.copy(feedbackTags = emptySet(), feedbackMemo = "") })

            tomorrowBlocks.clear()
        } finally {
            isAdvancing = false
        }
    }

    // ---------------------------
    // Overlap validation
    // ---------------------------
    private fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
        // [start, end) interval overlap
        return maxOf(aStart, bStart) < minOf(aEnd, bEnd)
    }

    private fun canPlace(
        list: List<TimeBlock>,
        ignoreId: String?,
        startMinute: Int,
        endMinute: Int
    ): Boolean {
        if (endMinute <= startMinute) return false
        for (b in list) {
            if (ignoreId != null && b.id == ignoreId) continue
            if (overlaps(startMinute, endMinute, b.startMinute, b.endMinute)) return false
        }
        return true
    }

    // ---------------------------
    // Today CRUD (겹치면 false)
    // ---------------------------
    fun addTodayBlock(title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        if (!canPlace(todayBlocks, ignoreId = null, startMinute = startMinute, endMinute = endMinute)) return false
        todayBlocks.add(TimeBlock(title = title, startMinute = startMinute, endMinute = endMinute, category = category))
        return true
    }

    fun updateTodayBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        if (!canPlace(todayBlocks, ignoreId = id, startMinute = startMinute, endMinute = endMinute)) return false

        val old = todayBlocks[idx]
        todayBlocks[idx] = old.copy(title = title, startMinute = startMinute, endMinute = endMinute, category = category)
        return true
    }

    fun deleteTodayBlock(id: String) {
        todayBlocks.removeAll { it.id == id }
    }

    fun updateTodayFeedback(id: String, tags: Set<String>, memo: String) {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = todayBlocks[idx]
            todayBlocks[idx] = old.copy(feedbackTags = tags, feedbackMemo = memo)
        }
    }

    fun clearTodayFeedback(id: String) {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = todayBlocks[idx]
            todayBlocks[idx] = old.copy(feedbackTags = emptySet(), feedbackMemo = "")
        }
    }

    // ---------------------------
    // Tomorrow CRUD (겹치면 false)
    // ---------------------------
    fun addTomorrowBlock(title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        if (!canPlace(tomorrowBlocks, ignoreId = null, startMinute = startMinute, endMinute = endMinute)) return false
        tomorrowBlocks.add(TimeBlock(title = title, startMinute = startMinute, endMinute = endMinute, category = category))
        return true
    }

    fun updateTomorrowBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        val idx = tomorrowBlocks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        if (!canPlace(tomorrowBlocks, ignoreId = id, startMinute = startMinute, endMinute = endMinute)) return false

        val old = tomorrowBlocks[idx]
        tomorrowBlocks[idx] = old.copy(title = title, startMinute = startMinute, endMinute = endMinute, category = category)
        return true
    }

    fun deleteTomorrowBlock(id: String) {
        tomorrowBlocks.removeAll { it.id == id }
    }

    fun feedbackOptionsFor(category: Category): List<String> {
        return when (category) {
            Category.SLEEP -> listOf("개운함", "숙면", "뒤척임", "늦잠", "수면부족")
            Category.EXERCISE -> listOf("고강도", "유산소", "근력", "컨디션저하", "만족")
            Category.STUDY -> listOf("집중", "산만", "진도OK", "막힘", "복습필요")
            Category.ETC -> listOf("GOOD", "OKAY", "BAD", "FAIL")
        }
    }
}
