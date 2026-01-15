package com.example.plannermvp.data

import java.util.UUID

enum class Category { SLEEP, STUDY, EXERCISE, ETC }

data class TimeBlock(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val category: Category = Category.ETC,

    // ✅ 피드백 태그(복수 선택)
    val feedbackTags: Set<String> = emptySet(),

    // ✅ 추가 메모(타자 입력)
    val feedbackMemo: String = ""
)

fun Int.toHHMM(): String {
    val h = this / 60
    val m = this % 60
    return "%02d:%02d".format(h, m)
}
