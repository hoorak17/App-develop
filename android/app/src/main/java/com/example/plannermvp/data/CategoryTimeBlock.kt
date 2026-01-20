package com.example.plannermvp.data

import java.util.UUID

enum class Category { SLEEP, STUDY, EXERCISE, ETC }

data class TimeBlock(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startMinute: Int,
    /**
     * endMinute는 "자정 넘김"을 지원하기 위해 1440(24:00) 초과 가능
     * 예) 23:00(1380) ~ 01:00(+1일) => endMinute = 1500
     */
    val endMinute: Int,
    val category: Category = Category.ETC,
    val feedbackTags: Set<String> = emptySet(),
    val feedbackMemo: String = ""
)

private const val DAY_MIN = 24 * 60

/** 0~1439 범위로 정규화해서 HH:MM 반환 */
private fun formatHHMM(min: Int): String {
    val m = ((min % DAY_MIN) + DAY_MIN) % DAY_MIN
    val h = m / 60
    val mm = m % 60
    return "%02d:%02d".format(h, mm)
}

/** 기존 코드 호환: Int.toHHMM() (자정 넘어가도 시각만 표시) */
fun Int.toHHMM(): String = formatHHMM(this)

/** 시간 범위를 “23:00-01:00(+1일)”처럼 표시 */
fun formatRange(startMinute: Int, endMinute: Int): String {
    val endDayOffset = endMinute / DAY_MIN
    val suffix = if (endDayOffset >= 1) "(+1일)" else ""
    return "${formatHHMM(startMinute)}-${formatHHMM(endMinute)}$suffix"
}
