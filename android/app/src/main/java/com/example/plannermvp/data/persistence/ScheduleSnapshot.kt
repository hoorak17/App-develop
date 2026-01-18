package com.example.plannermvp.data.persistence

import com.example.plannermvp.data.TimeBlock

/**
 * ✅ 저장/로드용 스냅샷
 * - DTO 분리 안 함
 * - TimeBlock(도메인)을 그대로 담되, 실제 저장은 ScheduleDataStore의 Codec이 문자열로 직렬화함
 */
data class ScheduleSnapshot(
    val schemaVersion: Int = 3,
    val today: List<TimeBlock> = emptyList(),
    val yesterday: List<TimeBlock> = emptyList(),
    val tomorrow: List<TimeBlock> = emptyList(),
    val history: List<HistoryDay> = emptyList()
) {
    data class HistoryDay(
        val dateIso: String = "",
        val blocks: List<TimeBlock> = emptyList()
    )
}
