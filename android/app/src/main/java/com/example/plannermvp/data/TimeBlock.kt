package com.example.plannermvp.data

data class TimeBlock(
    val id: String,
    val title: String,
    val startMinute: Int, // 0..1439
    val endMinute: Int,   // 1..1440
    val category: Category,
    val feedback: Feedback? = null
)

enum class Category {
    SLEEP, STUDY, EXERCISE, ETC
}

enum class Feedback {
    GOOD, OKAY, BAD, FAIL
}

fun Int.toHHMM(): String {
    val h = this / 60
    val m = this % 60
    return "%02d:%02d".format(h, m)
}
