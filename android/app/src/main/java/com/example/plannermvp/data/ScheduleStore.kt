package com.example.plannermvp.data

import androidx.compose.runtime.mutableStateListOf

object ScheduleStore {

    // 오늘(홈 타임라인)
    val todayBlocks = mutableStateListOf<TimeBlock>()

    // 어제(복사 소스)
    val yesterdayBlocks = mutableStateListOf<TimeBlock>()

    // 내일(계획 작성 중)
    val tomorrowBlocks = mutableStateListOf<TimeBlock>()

    init {
        // 샘플 데이터(원하면 지워도 됨)
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

    // --- 피드백 옵션(나중에 일정/카테고리별로 바꾸기 쉬움) ---
    fun feedbackOptionsFor(category: Category): List<String> {
        // MVP: 공통 + 카테고리별 약간 차이
        return when (category) {
            Category.SLEEP -> listOf("개운함", "숙면", "뒤척임", "늦잠", "수면부족")
            Category.EXERCISE -> listOf("고강도", "유산소", "근력", "컨디션저하", "만족")
            Category.STUDY -> listOf("집중", "산만", "진도OK", "막힘", "복습필요")
            Category.ETC -> listOf("GOOD", "OKAY", "BAD", "FAIL")
        }
    }

    // --- Today CRUD ---
    fun addTodayBlock(title: String, startMinute: Int, endMinute: Int, category: Category) {
        todayBlocks.add(
            TimeBlock(
                title = title,
                startMinute = startMinute,
                endMinute = endMinute,
                category = category
            )
        )
    }

    fun updateTodayBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category) {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = todayBlocks[idx]
            todayBlocks[idx] = old.copy(
                title = title,
                startMinute = startMinute,
                endMinute = endMinute,
                category = category
            )
        }
    }

    fun deleteTodayBlock(id: String) {
        todayBlocks.removeAll { it.id == id }
    }

    fun updateTodayFeedbackTags(id: String, tags: Set<String>) {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = todayBlocks[idx]
            todayBlocks[idx] = old.copy(feedbackTags = tags)
        }
    }

    // --- Tomorrow CRUD (계획 작성 중) ---
    fun addTomorrowBlock(title: String, startMinute: Int, endMinute: Int, category: Category) {
        tomorrowBlocks.add(
            TimeBlock(
                title = title,
                startMinute = startMinute,
                endMinute = endMinute,
                category = category
            )
        )
    }

    fun updateTomorrowBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category) {
        val idx = tomorrowBlocks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = tomorrowBlocks[idx]
            tomorrowBlocks[idx] = old.copy(
                title = title,
                startMinute = startMinute,
                endMinute = endMinute,
                category = category
            )
        }
    }

    fun deleteTomorrowBlock(id: String) {
        tomorrowBlocks.removeAll { it.id == id }
    }

    // 내일 계획을 오늘로 확정 반영(+ 어제 업데이트)
    fun finalizeTomorrowToToday() {
        // 어제를 오늘로 업데이트
        yesterdayBlocks.clear()
        yesterdayBlocks.addAll(todayBlocks)

        // 오늘을 내일로 교체
        todayBlocks.clear()
        todayBlocks.addAll(tomorrowBlocks.map { it.copy(feedbackTags = emptySet()) })

        // 내일 편집 버퍼 비움
        tomorrowBlocks.clear()
    }
}
