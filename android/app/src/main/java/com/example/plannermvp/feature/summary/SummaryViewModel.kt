package com.example.plannermvp.feature.summary

import androidx.lifecycle.ViewModel
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.TimeBlock

class SummaryViewModel : ViewModel() {
    val todayBlocks = ScheduleStore.todayBlocks

    fun preparePlanningNextDay() = ScheduleStore.preparePlanningNextDay()

    fun manualForceCloseTodayRequiresPlan(): Boolean {
        return ScheduleStore.manualForceCloseToday() is ScheduleStore.RolloverResult.RequirePlan
    }

    fun summaryCounts(blocks: List<TimeBlock>): Pair<Int, Int> {
        val total = blocks.size
        val reviewed = blocks.count { it.feedbackTags.isNotEmpty() || it.feedbackMemo.isNotBlank() }
        return total to reviewed
    }
}
