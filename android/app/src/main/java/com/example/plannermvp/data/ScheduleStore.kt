package com.example.plannermvp.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.plannermvp.data.persistence.ScheduleDataStore
import com.example.plannermvp.data.persistence.ScheduleSnapshot
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalDateTime

object ScheduleStore {

    // ---------------------------
    // State
    // ---------------------------
    val todayBlocks = mutableStateListOf<TimeBlock>()
    val yesterdayBlocks = mutableStateListOf<TimeBlock>()
    val tomorrowBlocks = mutableStateListOf<TimeBlock>()

    /** 최근 N일 히스토리 (일자별 blocks) */
    val historyDays = mutableStateListOf<DayRecord>()

    data class DayRecord(
        val dateIso: String,
        val blocks: List<TimeBlock>
    )

    private const val DAY_MIN = 24 * 60
    private const val HISTORY_KEEP_DAYS = 30

    private var isAdvancing = false
    private var seededOnce = false

    // ---------------------------
    // Dev Mode (시간/날짜 시뮬레이션) - ✅ Compose 관찰 가능 State로 승격
    // ---------------------------
    private var _devMode by mutableStateOf(false)
    val devMode: Boolean get() = _devMode

    private var devBaseRealNow: LocalDateTime? = null

    private var _devOffsetMinutes by mutableStateOf(0L)
    private var _devOffsetDays by mutableStateOf(0L)

    val devOffsetMinutes: Long get() = _devOffsetMinutes
    val devOffsetDays: Long get() = _devOffsetDays

    private var snapshotBeforeDev: ScheduleSnapshot? = null

    fun nowDateTime(): LocalDateTime {
        return if (!devMode) {
            LocalDateTime.now()
        } else {
            val base = devBaseRealNow ?: LocalDateTime.now()
            base.plusDays(_devOffsetDays).plusMinutes(_devOffsetMinutes)
        }
    }

    fun nowMinuteOfDay(): Int {
        val now = nowDateTime()
        return (now.hour * 60 + now.minute).coerceIn(0, DAY_MIN - 1)
    }

    fun toggleDevMode() {
        if (!devMode) {
            snapshotBeforeDev = buildSnapshot(schemaVersion = 3)
            devBaseRealNow = LocalDateTime.now()
            _devOffsetMinutes = 0L
            _devOffsetDays = 0L
            _devMode = true
        } else {
            snapshotBeforeDev?.let { applySnapshotToState(it) }
            snapshotBeforeDev = null
            devBaseRealNow = null
            _devOffsetMinutes = 0L
            _devOffsetDays = 0L
            _devMode = false

            // dev OFF 후 실사용 상태 저장
            persistDebounced()
        }
    }

    fun devAdjustMinutes(deltaMinutes: Int) {
        if (!devMode) return
        _devOffsetMinutes += deltaMinutes.toLong()
    }

    fun devAdjustDays(deltaDays: Int) {
        if (!devMode) return
        _devOffsetDays += deltaDays.toLong()
    }

    // ---------------------------
    // Persistence (SharedPreferences)
    // ---------------------------
    private var appContext: Context? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private var loadedOnce = false

    fun initPersistence(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        ioScope.launch {
            val snap = ScheduleDataStore.load(appContext!!)
            if (snap != null) {
                withContext(Dispatchers.Main) { applySnapshotToState(snap) }
                loadedOnce = true
            } else {
                withContext(Dispatchers.Main) { seedIfNeeded() }
                loadedOnce = true
                persistDebounced()
            }
        }
    }

    private fun persistDebounced() {
        val ctx = appContext ?: return
        if (!loadedOnce) return
        if (devMode) return // 요구사항: dev 기간 기록은 저장 금지

        saveJob?.cancel()
        saveJob = ioScope.launch {
            delay(600)
            ScheduleDataStore.save(ctx, buildSnapshot(schemaVersion = 3))
        }
    }

    private fun buildSnapshot(schemaVersion: Int): ScheduleSnapshot {
        return ScheduleSnapshot(
            schemaVersion = schemaVersion,
            today = todayBlocks.map { it.copy() },
            yesterday = yesterdayBlocks.map { it.copy() },
            tomorrow = tomorrowBlocks.map { it.copy() },
            history = historyDays.map { day ->
                ScheduleSnapshot.HistoryDay(
                    dateIso = day.dateIso,
                    blocks = day.blocks.map { it.copy() }
                )
            }
        )
    }

    private fun applySnapshotToState(snap: ScheduleSnapshot) {
        todayBlocks.clear()
        yesterdayBlocks.clear()
        tomorrowBlocks.clear()
        historyDays.clear()

        todayBlocks.addAll(snap.today.map { it.copy() })
        yesterdayBlocks.addAll(snap.yesterday.map { it.copy() })
        tomorrowBlocks.addAll(snap.tomorrow.map { it.copy() })

        historyDays.addAll(
            snap.history.map { day ->
                DayRecord(
                    dateIso = day.dateIso,
                    blocks = day.blocks.map { it.copy() }
                )
            }
        )
    }

    // ---------------------------
    // Seed
    // ---------------------------
    private fun seedIfNeeded() {
        if (seededOnce) return
        seededOnce = true

        if (todayBlocks.isNotEmpty()) return
        todayBlocks.add(TimeBlock(title = "수면", startMinute = 0, endMinute = 8 * 60, category = Category.SLEEP))
        todayBlocks.add(TimeBlock(title = "공부", startMinute = 10 * 60, endMinute = 12 * 60, category = Category.STUDY))
        todayBlocks.add(TimeBlock(title = "운동", startMinute = 18 * 60, endMinute = 19 * 60, category = Category.EXERCISE))
    }

    // ---------------------------
    // History
    // ---------------------------
    private fun upsertHistoryDay(dateIso: String, blocks: List<TimeBlock>) {
        val idx = historyDays.indexOfFirst { it.dateIso == dateIso }
        val record = DayRecord(dateIso, blocks.map { it.copy() })
        if (idx >= 0) historyDays[idx] = record else historyDays.add(record)

        historyDays.sortBy { it.dateIso }
        if (historyDays.size > HISTORY_KEEP_DAYS) {
            val drop = historyDays.size - HISTORY_KEEP_DAYS
            repeat(drop) { historyDays.removeAt(0) }
        }
    }

    private fun archiveTodayToHistory() {
        if (devMode) return
        val todayIso = nowDateTime().toLocalDate().toString()
        if (todayBlocks.isEmpty()) return
        upsertHistoryDay(todayIso, todayBlocks.toList())
    }

    fun historyForTitle(title: String, limit: Int = 10): List<HistoryItem> {
        val t = title.trim()
        if (t.isEmpty()) return emptyList()

        val todayIso = nowDateTime().toLocalDate().toString()
        val items = mutableListOf<HistoryItem>()

        for (day in historyDays) {
            if (day.dateIso == todayIso) continue
            for (b in day.blocks) {
                if (b.title.trim() == t) {
                    val hasFb = b.feedbackTags.isNotEmpty() || b.feedbackMemo.isNotBlank()
                    items.add(
                        HistoryItem(
                            dateIso = day.dateIso,
                            startMinute = b.startMinute,
                            endMinute = b.endMinute,
                            tags = b.feedbackTags,
                            memo = b.feedbackMemo,
                            hasFeedback = hasFb
                        )
                    )
                }
            }
        }
        return items.sortedByDescending { it.dateIso }.take(limit)
    }

    data class HistoryItem(
        val dateIso: String,
        val startMinute: Int,
        val endMinute: Int,
        val tags: Set<String>,
        val memo: String,
        val hasFeedback: Boolean
    )

    fun blocksOfHistoryDay(dateIso: String): List<TimeBlock> {
        return historyDays.firstOrNull { it.dateIso == dateIso }?.blocks ?: emptyList()
    }

    fun historyDayList(limit: Int = 14): List<String> {
        return historyDays.map { it.dateIso }.sortedDescending().take(limit)
    }

    // ---------------------------
    // Planning flow
    // ---------------------------
    fun preparePlanningNextDay() {
        archiveTodayToHistory()

        yesterdayBlocks.clear()
        yesterdayBlocks.addAll(todayBlocks.map { it.copy() })

        tomorrowBlocks.clear()

        persistDebounced()
    }

    fun finalizeTomorrowToToday() {
        if (isAdvancing) return
        isAdvancing = true
        try {
            yesterdayBlocks.clear()
            yesterdayBlocks.addAll(todayBlocks.map { it.copy() })

            todayBlocks.clear()
            todayBlocks.addAll(tomorrowBlocks.map { it.copy(feedbackTags = emptySet(), feedbackMemo = "") })

            tomorrowBlocks.clear()
        } finally {
            isAdvancing = false
        }
        persistDebounced()
    }

    // ---------------------------
    // Overlap validation (자정 넘김 지원)
    // ---------------------------
    private fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
        return maxOf(aStart, bStart) < minOf(aEnd, bEnd)
    }

    private fun splitIntervals(start: Int, end: Int): List<Pair<Int, Int>> {
        if (end <= start) return emptyList()
        if (end <= DAY_MIN) return listOf(start to end)
        val endNext = end % DAY_MIN
        return listOf(start to DAY_MIN, 0 to endNext)
    }

    private fun canPlace(list: List<TimeBlock>, ignoreId: String?, startMinute: Int, endMinute: Int): Boolean {
        if (endMinute <= startMinute) return false
        val aParts = splitIntervals(startMinute, endMinute)
        if (aParts.isEmpty()) return false

        for (b in list) {
            if (ignoreId != null && b.id == ignoreId) continue
            val bParts = splitIntervals(b.startMinute, b.endMinute)
            for (ap in aParts) for (bp in bParts) {
                if (overlaps(ap.first, ap.second, bp.first, bp.second)) return false
            }
        }
        return true
    }

    // ---------------------------
    // Today CRUD
    // ---------------------------
    fun addTodayBlock(title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        if (!canPlace(todayBlocks, null, startMinute, endMinute)) return false
        todayBlocks.add(TimeBlock(title = title, startMinute = startMinute, endMinute = endMinute, category = category))
        persistDebounced()
        return true
    }

    fun updateTodayBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        if (!canPlace(todayBlocks, id, startMinute, endMinute)) return false
        val old = todayBlocks[idx]
        todayBlocks[idx] = old.copy(title = title, startMinute = startMinute, endMinute = endMinute, category = category)
        persistDebounced()
        return true
    }

    fun deleteTodayBlock(id: String) {
        todayBlocks.removeAll { it.id == id }
        persistDebounced()
    }

    fun updateTodayFeedback(id: String, tags: Set<String>, memo: String) {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = todayBlocks[idx]
            todayBlocks[idx] = old.copy(feedbackTags = tags, feedbackMemo = memo)
            persistDebounced()
        }
    }

    fun clearTodayFeedback(id: String) {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = todayBlocks[idx]
            todayBlocks[idx] = old.copy(feedbackTags = emptySet(), feedbackMemo = "")
            persistDebounced()
        }
    }

    // ---------------------------
    // Tomorrow CRUD
    // ---------------------------
    fun addTomorrowBlock(title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        if (!canPlace(tomorrowBlocks, null, startMinute, endMinute)) return false
        tomorrowBlocks.add(TimeBlock(title = title, startMinute = startMinute, endMinute = endMinute, category = category))
        persistDebounced()
        return true
    }

    fun updateTomorrowBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        val idx = tomorrowBlocks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        if (!canPlace(tomorrowBlocks, id, startMinute, endMinute)) return false
        val old = tomorrowBlocks[idx]
        tomorrowBlocks[idx] = old.copy(title = title, startMinute = startMinute, endMinute = endMinute, category = category)
        persistDebounced()
        return true
    }

    fun deleteTomorrowBlock(id: String) {
        tomorrowBlocks.removeAll { it.id == id }
        persistDebounced()
    }

    // ---------------------------
    // Feedback options
    // ---------------------------
    fun feedbackOptionsFor(category: Category): List<String> {
        return when (category) {
            Category.SLEEP -> listOf("개운함", "숙면", "뒤척임", "늦잠", "수면부족")
            Category.EXERCISE -> listOf("고강도", "유산소", "근력", "컨디션저하", "만족")
            Category.STUDY -> listOf("집중", "산만", "진도OK", "막힘", "복습필요")
            Category.ETC -> listOf("GOOD", "OKAY", "BAD", "FAIL")
        }
    }
}
