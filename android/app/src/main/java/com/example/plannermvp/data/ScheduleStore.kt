package com.example.plannermvp.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.plannermvp.data.persistence.ScheduleRepository
import com.example.plannermvp.data.persistence.ScheduleSnapshot
import kotlinx.coroutines.*
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
    // Rollover (날짜 전환 UX)
    // ---------------------------
    private val _lastActiveDateIso = mutableStateOf("")          // 스냅샷 메타와 동기화
    private val _pendingRollover = mutableStateOf(false)
    private val _pendingFromDateIso = mutableStateOf("")

    /** Home에서 읽기용 */
    val pendingRollover: Boolean get() = _pendingRollover.value
    val pendingFromDateIso: String get() = _pendingFromDateIso.value

    // ---------------------------
    // Dev Mode (시간/날짜 시뮬레이션)
    // ---------------------------
    private val _devMode = mutableStateOf(false)
    val devMode: Boolean get() = _devMode.value

    // Compose가 즉시 감지하도록 State로 유지
    private val devBaseRealNow = mutableStateOf<LocalDateTime?>(null)
    private val devOffsetMinutes = mutableStateOf(0L)
    private val devOffsetDays = mutableStateOf(0L)

    private var snapshotBeforeDev: ScheduleSnapshot? = null

    private var clock: ScheduleClock = SystemScheduleClock

    fun nowDateTime(): LocalDateTime {
        return if (!devMode) {
            clock.nowDateTime()
        } else {
            val base = devBaseRealNow.value ?: clock.nowDateTime()
            base.plusDays(devOffsetDays.value).plusMinutes(devOffsetMinutes.value)
        }
    }

    fun nowMinuteOfDay(): Int {
        val now = nowDateTime()
        return (now.hour * 60 + now.minute).coerceIn(0, DAY_MIN - 1)
    }

    fun toggleDevMode() {
        if (!devMode) {
            // dev ON: 현 상태 스냅샷 백업(저장 금지)
            snapshotBeforeDev = buildSnapshot(schemaVersion = 4)
            devBaseRealNow.value = clock.nowDateTime()
            devOffsetMinutes.value = 0L
            devOffsetDays.value = 0L
            _devMode.value = true
        } else {
            // dev OFF: 백업 복구 후 저장
            snapshotBeforeDev?.let { applySnapshotToState(it) }
            snapshotBeforeDev = null
            devBaseRealNow.value = null
            devOffsetMinutes.value = 0L
            devOffsetDays.value = 0L
            _devMode.value = false

            persistDebounced()
        }
    }

    fun devAdjustMinutes(deltaMinutes: Int) {
        if (!devMode) return
        devOffsetMinutes.value += deltaMinutes.toLong()
    }

    fun devAdjustDays(deltaDays: Int) {
        if (!devMode) return
        devOffsetDays.value += deltaDays.toLong()
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
            val snap = ScheduleRepository.load(appContext!!)
            withContext(Dispatchers.Main) {
                if (snap != null) {
                    applySnapshotToState(snap)
                    // lastActiveDateIso가 비어있으면 보정
                    if (_lastActiveDateIso.value.isBlank()) {
                        _lastActiveDateIso.value = clock.todayIso()
                    }
                } else {
                    seedIfNeeded()
                    _lastActiveDateIso.value = clock.todayIso()
                    _pendingRollover.value = false
                    _pendingFromDateIso.value = ""
                }

                // ✅ 핵심: 로드 완료 플래그를 먼저 세운 뒤,
                // 바로 롤오버 체크를 1회 수행해서 "첫 진입"에서도 팝업이 안정적으로 뜨게 함
                loadedOnce = true
                if (!devMode) {
                    checkRolloverOnAppOpen()
                }

                // 신규 생성인 경우엔 즉시 저장도 진행
                if (snap == null) {
                    persistDebounced()
                }
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
            ScheduleRepository.save(ctx, buildSnapshot(schemaVersion = 4))
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

            // v4 meta
            lastActiveDateIso = _lastActiveDateIso.value,
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

        // meta 적용
        _lastActiveDateIso.value = snap.lastActiveDateIso
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

    private fun archiveBlocksToHistory(dateIso: String, blocks: List<TimeBlock>) {
        if (devMode) return
        if (dateIso.isBlank()) return
        if (blocks.isEmpty()) return
        upsertHistoryDay(dateIso, blocks.toList())
    }

    /**
     * "현재 todayBlocks가 의미하는 날짜"를 반환.
     * - pendingRollover=true면, todayBlocks는 사실상 어제 데이터이므로 pendingFromDateIso로 본다.
     */
    private fun effectiveTodayDataIso(): String {
        return if (_pendingRollover.value && _pendingFromDateIso.value.isNotBlank()) {
            _pendingFromDateIso.value
        } else {
            clock.todayIso()
        }
    }

    private fun archiveTodayToHistory() {
        if (devMode) return
        val iso = effectiveTodayDataIso()
        archiveBlocksToHistory(iso, todayBlocks)
    }

    fun historyForTitle(title: String, limit: Int = 10): List<HistoryItem> {
        val t = title.trim()
        if (t.isEmpty()) return emptyList()

        val excludeIso = effectiveTodayDataIso()
        val items = mutableListOf<HistoryItem>()

        for (day in historyDays) {
            if (day.dateIso == excludeIso) continue
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
    // Rollover API (Home/Summary가 호출하는 것들)
    // ---------------------------

    /**
     * 앱 오픈/복귀 시 날짜 전환 감지.
     * - devMode에서는 동작하지 않음
     * - lastActiveDateIso != 오늘이면 pendingRollover=true로 세팅
     */
    fun checkRolloverOnAppOpen() {
        if (devMode) return
        if (!loadedOnce) return

        val todayIso = clock.todayIso()
        if (_lastActiveDateIso.value.isBlank()) {
            _lastActiveDateIso.value = todayIso
            persistDebounced()
            return
        }

        // 이미 pending이면 그대로 둔다(사용자 결정 대기)
        if (_pendingRollover.value) return

        // 날짜가 바뀌었고, "어제 데이터(todayBlocks)"가 남아있으면 마무리/넘기기 선택 유도
        if (_lastActiveDateIso.value != todayIso && todayBlocks.isNotEmpty()) {
            _pendingRollover.value = true
            _pendingFromDateIso.value = _lastActiveDateIso.value
            persistDebounced()
        } else {
            // 날짜가 바뀌었지만 데이터가 비었거나 이미 정리된 상태면 lastActive만 갱신
            if (_lastActiveDateIso.value != todayIso) {
                _lastActiveDateIso.value = todayIso
                persistDebounced()
            }
        }
    }

    /** 사용자가 "마무리"를 눌렀을 때: 상태를 유지(그냥 Summary로 보내기용) */
    fun keepRolloverPending() {
        if (devMode) return
        if (!_pendingRollover.value) return
        persistDebounced()
    }

    /**
     * 사용자가 "넘기기" 선택: 어제 기록을 히스토리에 넣고 새 날로 진입.
     * - 어제(todayBlocks)를 history에 저장
     * - yesterdayBlocks에 복사(Plan에서 "어제일정" 탭에 쓰임)
     * - todayBlocks는 새 날이므로 비움
     */
    fun confirmRolloverSkip() {
        if (devMode) return
        if (!_pendingRollover.value) return

        val fromIso = _pendingFromDateIso.value
        if (fromIso.isNotBlank()) {
            archiveBlocksToHistory(fromIso, todayBlocks)
        }

        yesterdayBlocks.clear()
        yesterdayBlocks.addAll(todayBlocks.map { it.copy() })

        todayBlocks.clear()
        tomorrowBlocks.clear()

        _pendingRollover.value = false
        _pendingFromDateIso.value = ""
        _lastActiveDateIso.value = clock.todayIso()

        persistDebounced()
    }

    // SummaryScreen에서 요구하는 결과 타입
    sealed interface RolloverResult {
        data object Ok : RolloverResult
        data object RequirePlan : RolloverResult
    }

    /**
     * "오늘 강제 종료"
     * - Summary의 버튼 UX와 기존 "내일 계획 세우기" 흐름을 최대한 일치시킴.
     */
    fun manualForceCloseToday(): RolloverResult {
        if (devMode) return RolloverResult.Ok
        preparePlanningNextDay()
        return RolloverResult.RequirePlan
    }

    /** Home 배너 조건용 */
    fun isPlanMissing(): Boolean = todayBlocks.isEmpty()

    /** 전체 초기화(Dev UI에서 호출) */
    fun resetAll() {
        val ctx = appContext
        todayBlocks.clear()
        yesterdayBlocks.clear()
        tomorrowBlocks.clear()
        historyDays.clear()

        _pendingRollover.value = false
        _pendingFromDateIso.value = ""
        _lastActiveDateIso.value = clock.todayIso()

        if (ctx != null) {
            ioScope.launch {
                runCatching { ScheduleRepository.clearAll(ctx) }
            }
        }

        persistDebounced()
    }

    // ---------------------------
    // Planning flow
    // ---------------------------
    fun preparePlanningNextDay() {
        archiveTodayToHistory()

        yesterdayBlocks.clear()
        yesterdayBlocks.addAll(todayBlocks.map { it.copy() })

        tomorrowBlocks.clear()

        // pending 상태였다면 "어제"를 정리하는 동작이므로 pending 해제 및 lastActive 갱신
        if (_pendingRollover.value) {
            _pendingRollover.value = false
            _pendingFromDateIso.value = ""
            _lastActiveDateIso.value = clock.todayIso()
        }

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

            _pendingRollover.value = false
            _pendingFromDateIso.value = ""
            _lastActiveDateIso.value = clock.todayIso()
        } finally {
            isAdvancing = false
        }
        persistDebounced()
    }

    fun setClockForTesting(testClock: ScheduleClock) {
        clock = testClock
    }

    fun resetClockForTesting() {
        clock = SystemScheduleClock
    }

    fun resetForTesting() {
        todayBlocks.clear()
        yesterdayBlocks.clear()
        tomorrowBlocks.clear()
        historyDays.clear()
        _pendingRollover.value = false
        _pendingFromDateIso.value = ""
        _lastActiveDateIso.value = ""
        loadedOnce = true
        seededOnce = false
        isAdvancing = false
        snapshotBeforeDev = null
        devBaseRealNow.value = null
        devOffsetMinutes.value = 0L
        devOffsetDays.value = 0L
        _devMode.value = false
    }

    fun setLastActiveDateForTesting(dateIso: String) {
        _lastActiveDateIso.value = dateIso
    }

    // ---------------------------
    // Today CRUD
    // ---------------------------
    fun addTodayBlock(title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        if (!ScheduleValidator.canPlace(todayBlocks, null, startMinute, endMinute)) return false
        todayBlocks.add(TimeBlock(title = title, startMinute = startMinute, endMinute = endMinute, category = category))
        persistDebounced()
        return true
    }

    fun updateTodayBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        if (!ScheduleValidator.canPlace(todayBlocks, id, startMinute, endMinute)) return false
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
            recordFeedbackUsage(tags)
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
        if (!ScheduleValidator.canPlace(tomorrowBlocks, null, startMinute, endMinute)) return false
        tomorrowBlocks.add(TimeBlock(title = title, startMinute = startMinute, endMinute = endMinute, category = category))
        persistDebounced()
        return true
    }

    fun updateTomorrowBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        val idx = tomorrowBlocks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        if (!ScheduleValidator.canPlace(tomorrowBlocks, id, startMinute, endMinute)) return false
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
    private val commonCauseTags = listOf("시간부족", "과대계획", "변수발생", "집중안됨", "컨디션저하")
    private val sleepSpecificTags = listOf("개운함", "수면부족", "중간각성", "늦잠")
    private val exerciseSpecificTags = listOf("컨디션좋음", "운동강도높음", "운동강도낮음")
    private val studySpecificTags = listOf("집중됨", "진도OK", "막힘")
    private val feedbackUsageCounts = mutableMapOf<String, Int>()
    private val keywordTagRules = listOf(
        listOf("회의", "발표", "미팅") to "변수발생",
        listOf("알바", "근무", "출근") to "시간부족",
        listOf("이동", "통학", "통근") to "시간부족",
        listOf("병원", "진료", "약속") to "변수발생",
        listOf("공부", "과제", "리딩") to "집중안됨",
        listOf("운동", "헬스", "러닝") to "컨디션저하"
    )

    fun feedbackOptionsFor(block: TimeBlock): List<String> {
        val baseTags = when (block.category) {
            Category.SLEEP -> sleepSpecificTags + commonCauseTags
            Category.EXERCISE -> exerciseSpecificTags + commonCauseTags
            Category.STUDY -> studySpecificTags + commonCauseTags
            Category.ETC -> commonCauseTags
        }
        val prioritized = mutableListOf<String>()
        val title = block.title
        keywordTagRules.forEach { (keywords, tag) ->
            if (keywords.any { title.contains(it, ignoreCase = true) } && baseTags.contains(tag)) {
                prioritized.add(tag)
            }
        }
        val frequentTags = baseTags
            .sortedByDescending { feedbackUsageCounts[it] ?: 0 }
            .filter { (feedbackUsageCounts[it] ?: 0) > 0 }
        prioritized.addAll(frequentTags)
        val uniquePrioritized = prioritized.distinct()
        val remaining = baseTags.filterNot { uniquePrioritized.contains(it) }
        return uniquePrioritized + remaining
    }

    private fun recordFeedbackUsage(tags: Set<String>) {
        tags.forEach { tag ->
            feedbackUsageCounts[tag] = (feedbackUsageCounts[tag] ?: 0) + 1
        }
    }
}
