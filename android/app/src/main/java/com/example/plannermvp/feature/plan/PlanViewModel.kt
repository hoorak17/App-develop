package com.example.plannermvp.feature.plan

import androidx.lifecycle.ViewModel
import com.example.plannermvp.data.Category
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.TimeBlock

class PlanViewModel : ViewModel() {
    val yesterdayBlocks = ScheduleStore.yesterdayBlocks
    val tomorrowBlocks = ScheduleStore.tomorrowBlocks

    fun historyDayList(limit: Int = 14): List<String> = ScheduleStore.historyDayList(limit)
    fun blocksOfHistoryDay(dateIso: String): List<TimeBlock> = ScheduleStore.blocksOfHistoryDay(dateIso)

    fun finalizeTomorrowToToday() = ScheduleStore.finalizeTomorrowToToday()

    fun addTomorrowBlock(title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        return ScheduleStore.addTomorrowBlock(title, startMinute, endMinute, category)
    }

    fun updateTomorrowBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        return ScheduleStore.updateTomorrowBlock(id, title, startMinute, endMinute, category)
    }

    fun deleteTomorrowBlock(id: String) = ScheduleStore.deleteTomorrowBlock(id)
}
