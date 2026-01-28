package com.example.plannermvp.data.persistence

import com.example.plannermvp.data.Category
import com.example.plannermvp.data.TimeBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SnapshotCodecTest {

    @Test
    fun `encode and decode preserves snapshot data`() {
        val block = TimeBlock(
            id = "id-1",
            title = "테스트␜블록",
            startMinute = 60,
            endMinute = 120,
            category = Category.STUDY,
            feedbackTags = setOf("집중", "진도OK"),
            feedbackMemo = "메모␟"
        )
        val snapshot = ScheduleSnapshot(
            schemaVersion = 4,
            today = listOf(block),
            yesterday = emptyList(),
            tomorrow = emptyList(),
            history = listOf(
                ScheduleSnapshot.HistoryDay(
                    dateIso = "2024-01-01",
                    blocks = listOf(block.copy(id = "id-2"))
                )
            ),
            lastActiveDateIso = "2024-01-02",
            pendingRollover = false,
            pendingFromDateIso = ""
        )

        val raw = SnapshotCodec.encode(snapshot)
        val decoded = SnapshotCodec.decode(raw)

        assertEquals(snapshot.schemaVersion, decoded.schemaVersion)
        assertEquals(snapshot.lastActiveDateIso, decoded.lastActiveDateIso)
        assertEquals(snapshot.today.first().title, decoded.today.first().title)
        assertEquals(snapshot.today.first().feedbackMemo, decoded.today.first().feedbackMemo)
        assertEquals(snapshot.history.first().dateIso, decoded.history.first().dateIso)
        assertFalse(decoded.history.first().blocks.isEmpty())
    }
}
