package com.example.plannermvp.data

import java.util.UUID

enum class Category { SLEEP, STUDY, EXERCISE, ETC }

data class TimeBlock(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val category: Category = Category.ETC,
    val feedbackTags: Set<String> = emptySet()
)

fun Int.toHHMM(): String {
    val h = this / 60
    val m = this % 60
    return "%02d:%02d".format(h, m)
}
