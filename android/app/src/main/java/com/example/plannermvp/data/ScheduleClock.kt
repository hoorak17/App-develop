package com.example.plannermvp.data

import java.time.LocalDate
import java.time.LocalDateTime

interface ScheduleClock {
    fun nowDateTime(): LocalDateTime
    fun todayIso(): String
}

object SystemScheduleClock : ScheduleClock {
    override fun nowDateTime(): LocalDateTime = LocalDateTime.now()
    override fun todayIso(): String = LocalDate.now().toString()
}
