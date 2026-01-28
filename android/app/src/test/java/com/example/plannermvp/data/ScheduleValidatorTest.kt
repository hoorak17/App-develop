package com.example.plannermvp.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleValidatorTest {

    @Test
    fun `canPlace returns false on overlap and true on gap`() {
        val existing = listOf(
            TimeBlock(title = "A", startMinute = 9 * 60, endMinute = 10 * 60)
        )

        assertFalse(ScheduleValidator.canPlace(existing, null, 9 * 60 + 30, 10 * 60 + 30))
        assertTrue(ScheduleValidator.canPlace(existing, null, 10 * 60, 11 * 60))
    }

    @Test
    fun `canPlace respects midnight wrap`() {
        val existing = listOf(
            TimeBlock(title = "Night", startMinute = 23 * 60, endMinute = 25 * 60)
        )

        assertFalse(ScheduleValidator.canPlace(existing, null, 30, 90))
        assertTrue(ScheduleValidator.canPlace(existing, null, 2 * 60, 3 * 60))
    }
}
