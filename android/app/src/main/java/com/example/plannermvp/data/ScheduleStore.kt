package com.example.plannermvp.data

import androidx.compose.runtime.mutableStateListOf

object ScheduleStore {

    val todayBlocks = mutableStateListOf<TimeBlock>()
    val yesterdayBlocks = mutableStateListOf<TimeBlock>()
    val tomorrowBlocks = mutableStateListOf<TimeBlock>()

    // 재진입(버튼 연타 등) 방지
    private var isAdvancing: Boolean = false
    private var seededOnce: Boolean = false

    init {
        seedIfNeeded()
    }

    private fun seedIfNeeded() {
        if (seededOnce) return
        seededOnce = true

        // ✅ 앱 처음 켜면 기본적으로 "수면/운동/공부"가 오늘 계획에 존재
        if (todayBlocks.isEmpty()) {
            todayBlocks.add(TimeBlock(title = "수면", startMinute = 0, endMinute = 8 * 60, category = Category.SLEEP))
            todayBlocks.add(TimeBlock(title = "공부", startMinute = 10 * 60, endMinute = 12 * 60, category = Category.STUDY))
            todayBlocks.add(TimeBlock(title = "운동", startMinute = 18 * 60, endMinute = 19 * 60, category = Category.EXERCISE))
        }
    }

    /**
     * ✅ 요약 → 계획 화면으로 넘어갈 때 호출
     * "어제 일정" = 방금까지의 "오늘 일정" 스냅샷으로 만든다.
     * (즉 1일차 요약 → 2일차 계획에서 1일차가 어제로 보이게)
     */
    fun preparePlanningNextDay() {
        yesterdayBlocks.clear()
        yesterdayBlocks.addAll(todayBlocks.map { it.copy() })

        // 계획 화면 들어갈 때, 내일 계획이 남아있으면 헷갈리므로 비워두는 게 안전
        tomorrowBlocks.clear()
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

    // --- Today CRUD (홈에서 추가/수정/삭제할 때 사용) ---
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

    /**
     * ✅ 계획 화면에서 "완료하기" 눌렀을 때만 호출
     * 내일 계획(tomorrowBlocks)을 오늘(todayBlocks)로 확정한다.
     *
     * - yesterday는 이미 preparePlanningNextDay로 잡혀있지만,
     *   안전하게 다시 today 스냅샷을 남겨두어도 무방
     */
    fun finalizeTomorrowToToday() {
        if (isAdvancing) return
        isAdvancing = true
        try {
            // yesterday <- today (스냅샷)
            yesterdayBlocks.clear()
            yesterdayBlocks.addAll(todayBlocks.map { it.copy() })

            // today <- tomorrow (피드백 초기화)
            todayBlocks.clear()
            todayBlocks.addAll(tomorrowBlocks.map { it.copy(feedbackTags = emptySet(), feedbackMemo = "") })

            // tomorrow clear
            tomorrowBlocks.clear()
        } finally {
            isAdvancing = false
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
}
