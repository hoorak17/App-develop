package com.example.plannermvp.feature.home

import androidx.lifecycle.ViewModel
import com.example.plannermvp.data.Category
import com.example.plannermvp.data.ScheduleStore
import com.example.plannermvp.data.TimeBlock
import java.time.LocalDateTime

class HomeViewModel : ViewModel() {
    val todayBlocks = ScheduleStore.todayBlocks
    val devMode: Boolean get() = ScheduleStore.devMode
    val pendingRollover: Boolean get() = ScheduleStore.pendingRollover
    val pendingFromDateIso: String get() = ScheduleStore.pendingFromDateIso

    fun nowDateTime(): LocalDateTime = ScheduleStore.nowDateTime()
    fun nowMinuteOfDay(): Int = ScheduleStore.nowMinuteOfDay()

    fun checkRolloverOnAppOpen() = ScheduleStore.checkRolloverOnAppOpen()
    fun keepRolloverPending() = ScheduleStore.keepRolloverPending()
    fun confirmRolloverSkip() = ScheduleStore.confirmRolloverSkip()

    fun toggleDevMode() = ScheduleStore.toggleDevMode()
    fun devAdjustMinutes(deltaMinutes: Int) = ScheduleStore.devAdjustMinutes(deltaMinutes)
    fun devAdjustDays(deltaDays: Int) = ScheduleStore.devAdjustDays(deltaDays)
    fun resetAll() = ScheduleStore.resetAll()

    fun isPlanMissing(): Boolean = ScheduleStore.isPlanMissing()

    fun addTodayBlock(title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        return ScheduleStore.addTodayBlock(title, startMinute, endMinute, category)
    }

    fun updateTodayBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        return ScheduleStore.updateTodayBlock(id, title, startMinute, endMinute, category)
    }

    fun deleteTodayBlock(id: String) = ScheduleStore.deleteTodayBlock(id)
    fun updateTodayFeedback(id: String, tags: Set<String>, memo: String) =
        ScheduleStore.updateTodayFeedback(id, tags, memo)

    fun clearTodayFeedback(id: String) = ScheduleStore.clearTodayFeedback(id)

    fun feedbackOptionsFor(block: TimeBlock): List<String> {
        return ScheduleStore.feedbackOptionsFor(block)
    }

    fun latestBlock(id: String): TimeBlock? {
        return ScheduleStore.todayBlocks.firstOrNull { it.id == id }
    }
}
