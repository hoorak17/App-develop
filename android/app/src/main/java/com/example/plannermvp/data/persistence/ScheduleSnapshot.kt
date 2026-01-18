package com.example.plannermvp.data.persistence

import com.example.plannermvp.data.Category
import com.example.plannermvp.data.TimeBlock
import kotlinx.serialization.Serializable

@Serializable
data class TimeBlockDto(
    val id: String,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val category: Category,
    val feedbackTags: Set<String>,
    val feedbackMemo: String
) {
    fun toDomain(): TimeBlock = TimeBlock(
        id = id,
        title = title,
        startMinute = startMinute,
        endMinute = endMinute,
        category = category,
        feedbackTags = feedbackTags,
        feedbackMemo = feedbackMemo
    )

    companion object {
        fun fromDomain(b: TimeBlock): TimeBlockDto =
            TimeBlockDto(
                id = b.id,
                title = b.title,
                startMinute = b.startMinute,
                endMinute = b.endMinute,
                category = b.category,
                feedbackTags = b.feedbackTags,
                feedbackMemo = b.feedbackMemo
            )
    }
}

@Serializable
data class ScheduleSnapshot(
    val schemaVersion: Int = 1,
    val today: List<TimeBlockDto>,
    val yesterday: List<TimeBlockDto>,
    val tomorrow: List<TimeBlockDto>
)
