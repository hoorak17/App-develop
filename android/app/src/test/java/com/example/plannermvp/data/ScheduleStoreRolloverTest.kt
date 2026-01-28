package com.example.plannermvp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleStoreRolloverTest {

    @Test
    fun `checkRolloverOnAppOpen marks pending when day changes`() {
        ScheduleStore.resetForTesting()
        val testClock = object : ScheduleClock {
            override fun nowDateTime(): LocalDateTime = LocalDateTime.of(2024, 1, 2, 9, 0)
            override fun todayIso(): String = LocalDate.of(2024, 1, 2).toString()
        }
        ScheduleStore.setClockForTesting(testClock)
        ScheduleStore.setLastActiveDateForTesting("2024-01-01")
        ScheduleStore.todayBlocks.add(
            TimeBlock(title = "Block", startMinute = 60, endMinute = 120, category = Category.STUDY)
        )

        ScheduleStore.checkRolloverOnAppOpen()

        assertTrue(ScheduleStore.pendingRollover)
        assertEquals("2024-01-01", ScheduleStore.pendingFromDateIso)

        ScheduleStore.confirmRolloverSkip()

        assertFalse(ScheduleStore.pendingRollover)
        assertTrue(ScheduleStore.historyDays.any { it.dateIso == "2024-01-01" })
        assertTrue(ScheduleStore.todayBlocks.isEmpty())
        ScheduleStore.resetClockForTesting()
    }
}
