package com.example.plannermvp.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.example.plannermvp.data.persistence.ScheduleDataStore
import com.example.plannermvp.data.persistence.ScheduleSnapshot
import com.example.plannermvp.data.persistence.TimeBlockDto
import kotlinx.coroutines.*

object ScheduleStore {

    // ---------------------------
    // In-memory state (Compose observes)
    // ---------------------------
    val todayBlocks = mutableStateListOf<TimeBlock>()
    val yesterdayBlocks = mutableStateListOf<TimeBlock>()
    val tomorrowBlocks = mutableStateListOf<TimeBlock>()

    private var isAdvancing: Boolean = false
    private var seededOnce: Boolean = false

    // ---------------------------
    // Persistence (DataStore + JSON)
    // ---------------------------
    private var appContext: Context? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private var loadedOnce: Boolean = false

    /**
     * MainActivity에서 applicationContext로 1회 호출.
     * - 저장 데이터 있으면 복원
     * - 없으면 seed 유지
     */
    fun initPersistence(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        ioScope.launch {
            val snap = ScheduleDataStore.load(appContext!!)
            if (snap != null) {
                applySnapshotOnMain(snap)
                loadedOnce = true
            } else {
                // 저장본 없으면 seed
                withContext(Dispatchers.Main) { seedIfNeeded() }
                loadedOnce = true
                persistDebounced() // seed도 저장해두면 다음 실행부터 안정적
            }
        }
    }

    private suspend fun applySnapshotOnMain(snap: ScheduleSnapshot) {
        withContext(Dispatchers.Main) {
            todayBlocks.clear()
            yesterdayBlocks.clear()
            tomorrowBlocks.clear()

            todayBlocks.addAll(snap.today.map { it.toDomain() })
            yesterdayBlocks.addAll(snap.yesterday.map { it.toDomain() })
            tomorrowBlocks.addAll(snap.tomorrow.map { it.toDomain() })
        }
    }

    private fun persistDebounced() {
        val ctx = appContext ?: return
        if (!loadedOnce) return // 로드 완료 전에는 저장하지 않음

        saveJob?.cancel()
        saveJob = ioScope.launch {
            delay(600) // 디바운스
            val snap = ScheduleSnapshot(
                schemaVersion = 1,
                today = todayBlocks.map { TimeBlockDto.fromDomain(it) },
                yesterday = yesterdayBlocks.map { TimeBlockDto.fromDomain(it) },
                tomorrow = tomorrowBlocks.map { TimeBlockDto.fromDomain(it) }
            )
            ScheduleDataStore.save(ctx, snap)
        }
    }

    // ---------------------------
    // Seed (default blocks)
    // ---------------------------
    private fun seedIfNeeded() {
        if (seededOnce) return
        seededOnce = true

        if (todayBlocks.isNotEmpty()) return

        todayBlocks.add(
            TimeBlock(
                title = "수면",
                startMinute = 0,
                endMinute = 8 * 60,
                category = Category.SLEEP
            )
        )
        todayBlocks.add(
            TimeBlock(
                title = "공부",
                startMinute = 10 * 60,
                endMinute = 12 * 60,
                category = Category.STUDY
            )
        )
        todayBlocks.add(
            TimeBlock(
                title = "운동",
                startMinute = 18 * 60,
                endMinute = 19 * 60,
                category = Category.EXERCISE
            )
        )
    }

    // ---------------------------
    // Planning flow
    // ---------------------------
    fun preparePlanningNextDay() {
        // "어제" = 방금까지의 "오늘" 스냅샷
        yesterdayBlocks.clear()
        yesterdayBlocks.addAll(todayBlocks.map { it.copy() })

        // 계획 화면 진입 시 내일 계획은 새로 작성
        tomorrowBlocks.clear()

        persistDebounced()
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
            todayBlocks.addAll(
                tomorrowBlocks.map { it.copy(feedbackTags = emptySet(), feedbackMemo = "") }
            )

            tomorrowBlocks.clear()
        } finally {
            isAdvancing = false
        }
        persistDebounced()
    }

    // ---------------------------
    // Overlap validation (자정 넘김 지원)
    // ---------------------------
    private const val DAY_MIN = 24 * 60

    private fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
        // [start, end) overlap
        return maxOf(aStart, bStart) < minOf(aEnd, bEnd)
    }

    /**
     * endMinute가 1440 초과(자정 넘김)일 수 있으므로
     * [start, end) 를 0~1440 범위의 구간들로 분해해서 비교한다.
     */
    private fun splitIntervals(start: Int, end: Int): List<Pair<Int, Int>> {
        if (end <= start) return emptyList()
        if (end <= DAY_MIN) return listOf(start to end)

        // ex) 23:00(1380) ~ 01:00(+1일, 1500)
        val endNext = end % DAY_MIN
        return listOf(
            start to DAY_MIN,
            0 to endNext
        )
    }

    private fun canPlace(
        list: List<TimeBlock>,
        ignoreId: String?,
        startMinute: Int,
        endMinute: Int
    ): Boolean {
        if (endMinute <= startMinute) return false

        val aParts = splitIntervals(startMinute, endMinute)
        if (aParts.isEmpty()) return false

        for (b in list) {
            if (ignoreId != null && b.id == ignoreId) continue

            val bParts = splitIntervals(b.startMinute, b.endMinute)
            for (ap in aParts) {
                for (bp in bParts) {
                    if (overlaps(ap.first, ap.second, bp.first, bp.second)) return false
                }
            }
        }
        return true
    }

    // ---------------------------
    // Today CRUD (겹치면 false)
    // ---------------------------
    fun addTodayBlock(title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        if (!canPlace(todayBlocks, ignoreId = null, startMinute = startMinute, endMinute = endMinute)) return false
        todayBlocks.add(TimeBlock(title = title, startMinute = startMinute, endMinute = endMinute, category = category))
        persistDebounced()
        return true
    }

    fun updateTodayBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        val idx = todayBlocks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        if (!canPlace(todayBlocks, ignoreId = id, startMinute = startMinute, endMinute = endMinute)) return false

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
    // Tomorrow CRUD (겹치면 false)
    // ---------------------------
    fun addTomorrowBlock(title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        if (!canPlace(tomorrowBlocks, ignoreId = null, startMinute = startMinute, endMinute = endMinute)) return false
        tomorrowBlocks.add(TimeBlock(title = title, startMinute = startMinute, endMinute = endMinute, category = category))
        persistDebounced()
        return true
    }

    fun updateTomorrowBlock(id: String, title: String, startMinute: Int, endMinute: Int, category: Category): Boolean {
        val idx = tomorrowBlocks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        if (!canPlace(tomorrowBlocks, ignoreId = id, startMinute = startMinute, endMinute = endMinute)) return false

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
