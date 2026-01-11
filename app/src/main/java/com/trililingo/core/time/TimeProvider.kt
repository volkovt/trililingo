package com.trililingo.core.time

import java.time.LocalDate
import java.time.ZoneId

interface TimeProvider {
    fun nowMs(): Long
    fun todayEpochDay(): Long

    object System : TimeProvider {
        override fun nowMs(): Long = java.lang.System.currentTimeMillis()
        override fun todayEpochDay(): Long =
            LocalDate.now(ZoneId.systemDefault()).toEpochDay()
    }
}
