package com.example.plannermvp.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
    // UX flags (UI에서 읽기만)
    // ---------------------------
    private val _pendingRollover = mutableStateOf(false)
    val pendingRollover: Boolean get() = _pendingRollover.value

    private val _pendingFromDateIso = mutableStateOf("")
    val pendingFromDateIso: String get() = _pendingFromDateIso.value

    /** 홈 배너: 계획 비어있음 */
    fun isPlanMissing(): Boolean = tomorrowBlocks.isEmpty()

    /** 내부 기준 날짜(사용자에게 노출 금지) */
    private var lastActiveDateIso: String = LocalDate.now().toString()

    // ---------------------------
    // Dev Mode (시간/날짜 시뮬레이션)
    // ---------------------------
    private val _devMode = mutableStateOf(false)
    val devMode: Boolean get() = _devMode.value

    private var devBaseRealNow: LocalDateTime? = null
    private var devOffsetMinutes: Long = 0L
    private var devOffsetDays: Long = 0L

    private var snapshotBeforeDev: ScheduleSnapshot? = null

    fun nowDateTime(): LocalDateTime {
        return if (!devMode) {
            LocalDateTime.now()
        } else {
            val base = devBaseRealNow ?: LocalDateTime.now()
            base.plusDays(devOffsetDays).plusMinutes(devOffsetMinutes)
        }
    }

    fun nowMinuteOfDay(): Int {
        val now = nowDateTime()
        return (now.hour * 60 + now.minute).coerceIn(0, DAY_MIN - 1)
    }

    fun toggleDevMode() {
        if (!devMode) {
            snapshotBeforeDev = buildSnapshot(schemaVersion = 4)
            devBaseRealNow = LocalDateTime.now()
            devOffsetMinutes = 0L
            devOffsetDays = 0L
            _devMode.value = true
        } else {
            snapshotBeforeDev?.let { applySnapshotToState(it) }
            snapshotBeforeDev = null
            devBaseRealNow = null
            devOffsetMinutes = 0L
            devOffsetDays = 0L
            _devMode.value = false
            persistDebounced()
        }
    }

    fun devAdjustMinutes(deltaMinutes: Int) {
        if (!devMode) return
        devOffsetMinutes += deltaMinutes.toLong()
    }

    fun devAdjustDays(deltaDays: Int) {
        if (!devMode) return
        devOffsetDays += deltaDays.toLong()
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
                withContext(Dispatchers.Main) { checkRolloverOnAppOpen() }
            } else {
                withContext(Dispatchers.Main) { seedIfNeeded() }
                loadedOnce = true
                lastActiveDateIso = LocalDate.now().toString()
                _pendingRollover.value = false
                _pendingFromDateIso.value = ""
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
            delay(400)
            ScheduleDataStore.save(ctx, buildSnapshot(schemaVersion = 4))
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
            },
            lastActiveDateIso = lastActiveDateIso,
            pendingRollover = _pendingRollover.value,
            pendingFromDateIso = _pendingFromDateIso.value
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

        lastActiveDateIso = snap.lastActiveDateIso.ifBlank { LocalDate.now().toString() }
        _pendingRollover.value = snap.pendingRollover
        _pendingFromDateIso.value = snap.pendingFromDateIso
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

    private const val TAG_UNRECORDED = "미입력"

    private fun archiveTodayToHistoryWithUnrecordedTag(dateIso: String) {
        if (todayBlocks.isEmpty()) return
        val archived = todayBlocks.map { b ->
            val hasFb = b.feedbackTags.isNotEmpty() || b.feedbackMemo.isNotBlank()
            if (hasFb) b.copy()
            else b.copy(feedbackTags = setOf(TAG_UNRECORDED), feedbackMemo = "")
        }
        upsertHistoryDay(dateIso, archived)
    }

    fun historyForTitle(title: String, limit: Int = 10): List<HistoryItem> {
        val t = title.trim()
        if (t.isEmpty()) return emptyList()

        val todayIso = LocalDate.now().toString()
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
    // Rollover (자동 감지: 사용자 선택 대기)
    // ---------------------------
    fun checkRolloverOnAppOpen() {
        if (devMode) return
        val todayIso = LocalDate.now().toString()
        if (todayIso == lastActiveDateIso) {
            _pendingRollover.value = false
            _pendingFromDateIso.value = ""
            return
        }

        _pendingRollover.value = true
        _pendingFromDateIso.value = lastActiveDateIso
        persistDebounced()
    }

    fun confirmRolloverSkip() {
        if (devMode) return
        if (!_pendingRollover.value) return

        val fromIso = _pendingFromDateIso.value.ifBlank { lastActiveDateIso }
        archiveTodayToHistoryWithUnrecordedTag(fromIso)

        yesterdayBlocks.clear()
        yesterdayBlocks.addAll(todayBlocks.map { it.copy() })

        todayBlocks.clear()
        todayBlocks.addAll(tomorrowBlocks.map { it.copy(feedbackTags = emptySet(), feedbackMemo = "") })

        tomorrowBlocks.clear()

        lastActiveDateIso = LocalDate.now().toString()
        _pendingRollover.value = false
        _pendingFromDateIso.value = ""

        persistDebounced()
    }

    fun keepRolloverPending() {
        // intentionally no-op
    }

    // ---------------------------
    // Manual force close (SummaryScreen에서 호출)
    // ---------------------------
    sealed interface RolloverResult {
        data object Done : RolloverResult
        data object RequirePlan : RolloverResult
    }

    /**
     * ✅ 사용자가 "오늘 강제 종료"를 눌렀을 때:
     * - 내부 기준 날짜(lastActiveDateIso)로 todayBlocks를 history에 확정 (피드백 없으면 "미입력")
     * - yesterday <- today
     * - today <- tomorrow (없으면 빈)
     * - tomorrow 비움
     * - 내부 날짜 +1 (연속 강제 종료도 안정)
     * - 다음 today가 비면 RequirePlan 반환
     */
    fun manualForceCloseToday(): RolloverResult {
        if (!loadedOnce) return RolloverResult.Done
        if (devMode) return RolloverResult.Done

        val fromIso = lastActiveDateIso.ifBlank { LocalDate.now().toString() }
        archiveTodayToHistoryWithUnrecordedTag(fromIso)

        yesterdayBlocks.clear()
        yesterdayBlocks.addAll(todayBlocks.map { it.copy() })

        val nextToday = tomorrowBlocks.map { it.copy(feedbackTags = emptySet(), feedbackMemo = "") }
        todayBlocks.clear()
        todayBlocks.addAll(nextToday)

        tomorrowBlocks.clear()

        _pendingRollover.value = false
        _pendingFromDateIso.value = ""

        lastActiveDateIso = runCatching {
            LocalDate.parse(fromIso).plusDays(1).toString()
        }.getOrElse {
            LocalDate.now().plusDays(1).toString()
        }

        persistDebounced()

        return if (todayBlocks.isEmpty()) RolloverResult.RequirePlan else RolloverResult.Done
    }

    // ---------------------------
    // Planning flow
    // ---------------------------
    fun preparePlanningNextDay() {
        val todayIso = LocalDate.now().toString()
        if (!devMode && todayBlocks.isNotEmpty()) {
            upsertHistoryDay(todayIso, todayBlocks.toList())
        }

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

    // ---------------------------
    // Reset (Dev mode에서만 노출 권장)
    // ---------------------------
    fun resetAll() {
        val ctx = appContext ?: return
        ioScope.launch {
            ScheduleDataStore.clearAll(ctx)
            withContext(Dispatchers.Main) {
                todayBlocks.clear()
                yesterdayBlocks.clear()
                tomorrowBlocks.clear()
                historyDays.clear()

                seededOnce = false
                seedIfNeeded()

                lastActiveDateIso = LocalDate.now().toString()
                _pendingRollover.value = false
                _pendingFromDateIso.value = ""

                persistDebounced()
            }
        }
    }
}
