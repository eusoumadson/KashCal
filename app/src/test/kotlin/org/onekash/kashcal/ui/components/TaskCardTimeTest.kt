package org.onekash.kashcal.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Tests [formatTaskCardTime], the end of the compact task card. */
class TaskCardTimeTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val due = LocalDateTime.of(2026, 10, 29, 17, 30).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a timed task shows its due time in the zone`() {
        assertEquals("17:30", formatTaskCardTime(due, isAllDay = false, isRecurring = false, timePattern = "HH:mm", zoneId = zone))
    }

    @Test
    fun `a repeating timed task adds the repeat marker`() {
        assertEquals("17:30 \uD83D\uDD01", formatTaskCardTime(due, isAllDay = false, isRecurring = true, timePattern = "HH:mm", zoneId = zone))
    }

    @Test
    fun `an all-day task shows no time, only the repeat marker when it repeats`() {
        // All-day tasks sit at UTC midnight (TasksOrgTask).
        val day = 1793318400000L
        assertEquals("", formatTaskCardTime(day, isAllDay = true, isRecurring = false, timePattern = "HH:mm", zoneId = zone))
        assertEquals("\uD83D\uDD01", formatTaskCardTime(day, isAllDay = true, isRecurring = true, timePattern = "HH:mm", zoneId = zone))
    }
}
