package com.example.plannermvp.data

private const val DAY_MIN = 24 * 60

object ScheduleValidator {

    fun canPlace(
        list: List<TimeBlock>,
        ignoreId: String?,
        startMinute: Int,
        endMinute: Int
    ): Boolean {
        if (endMinute <= startMinute) return false
        val aParts = splitIntervals(startMinute, endMinute)
        if (aParts.isEmpty()) return false

        for (b in list) {
            if (ignoreId != null && b.id == ignoreId) continue
            val bParts = splitIntervals(b.startMinute, b.endMinute)
            for (ap in aParts) for (bp in bParts) {
                if (overlaps(ap.first, ap.second, bp.first, bp.second)) return false
            }
        }
        return true
    }

    private fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
        return maxOf(aStart, bStart) < minOf(aEnd, bEnd)
    }

    private fun splitIntervals(start: Int, end: Int): List<Pair<Int, Int>> {
        if (end <= start) return emptyList()
        if (end <= DAY_MIN) return listOf(start to end)
        val endNext = end % DAY_MIN
        return listOf(start to DAY_MIN, 0 to endNext)
    }
}
