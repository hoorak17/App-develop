package com.example.plannermvp.data.persistence

import com.example.plannermvp.data.TimeBlock

/**
 * ✅ 저장/로드용 스냅샷
 * - TimeBlock(도메인)을 그대로 담되, 실제 저장은 Codec이 문자열로 직렬화함
 */
data class ScheduleSnapshot(
    val schemaVersion: Int = 4,

    val today: List<TimeBlock> = emptyList(),
    val yesterday: List<TimeBlock> = emptyList(),
    val tomorrow: List<TimeBlock> = emptyList(),
    val history: List<HistoryDay> = emptyList(),

    // ✅ 날짜 전환 UX를 위한 최소 메타
    val lastActiveDateIso: String = "",          // 마지막으로 앱이 정상 상태로 본 "오늘" 날짜
    val pendingRollover: Boolean = false,        // 날짜가 바뀌었는데 사용자 결정(마무리/넘기기) 대기 중
    val pendingFromDateIso: String = ""          // 대기 중인 "어제" 날짜(표시/로그용)
) {
    data class HistoryDay(
        val dateIso: String = "",
        val blocks: List<TimeBlock> = emptyList()
    )
}
