package org.onekash.kashcal.domain.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom
import org.onekash.kashcal.data.tasks_org.placementOf
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Tests [futureOccurrences]: the dates after a repeating task's current occurrence that the
 * calendar shows dimmed. The current occurrence itself is the provider's row and is never
 * produced here.
 */
class TasksOrgOccurrencesTest {

    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun ms(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    /** A timed task due at [dueTs]. */
    private fun timed(rule: String?, dueTs: Long = ms(2026, 10, 29, 10)): TasksOrgTask {
        val p = placementOf(dueTs, false, zone)
        return TasksOrgTask(
            id = 7, title = "Review the day", notes = "", dueTs = dueTs, isAllDay = false,
            startTs = p.startTs, endTs = p.endTs, startDay = p.startDay, endDay = p.endDay,
            completedAt = null, recurrence = rule, listId = 1, listTitle = "Inbox", listColor = 0,
            isReadOnly = false
        )
    }

    /** An all-day task due on the local date [date], stored as local midnight like Tasks.org does. */
    private fun allDay(rule: String?, date: LocalDate = LocalDate.of(2026, 10, 29)): TasksOrgTask {
        val dueTs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val p = placementOf(dueTs, true, zone)
        return TasksOrgTask(
            id = 8, title = "Pay rent", notes = "", dueTs = dueTs, isAllDay = true,
            startTs = p.startTs, endTs = p.endTs, startDay = p.startDay, endDay = p.endDay,
            completedAt = null, recurrence = rule, listId = 1, listTitle = "Inbox", listColor = 0,
            isReadOnly = false
        )
    }

    private fun days(task: TasksOrgTask, from: Long, to: Long) =
        futureOccurrences(task, from, to, zone).map { it.startDay }

    @Test
    fun `a daily task shows the days after the current one, not the current one`() {
        val task = timed("FREQ=DAILY")

        assertEquals(
            listOf(20261030, 20261031, 20261101),
            days(task, ms(2026, 10, 29), ms(2026, 11, 1, 23, 59))
        )
    }

    @Test
    fun `an occurrence keeps the time of day and carries the task's identity`() {
        val later = futureOccurrences(timed("FREQ=WEEKLY"), ms(2026, 11, 1), ms(2026, 11, 7), zone).single()

        assertEquals(ms(2026, 11, 5, 10), later.dueTs)
        assertEquals(later.dueTs, later.startTs)
        assertEquals(later.dueTs, later.endTs)
        assertEquals(20261105, later.startDay)
        assertTrue(later.isFutureOccurrence)
        assertEquals(7L, later.id)
        assertEquals("Review the day", later.title)
        assertEquals("FREQ=WEEKLY", later.recurrence)
        assertEquals(null, later.completedAt)
    }

    @Test
    fun `the time of day holds across a daylight saving change`() {
        // Sao Paulo has no DST now; use a zone that does, to see 10:00 stay 10:00 local.
        val ny = ZoneId.of("America/New_York")
        val due = LocalDateTime.of(2026, 10, 31, 10, 0).atZone(ny).toInstant().toEpochMilli()
        val p = placementOf(due, false, ny)
        val task = timed("FREQ=DAILY").copy(
            dueTs = due, startTs = p.startTs, endTs = p.endTs, startDay = p.startDay, endDay = p.endDay
        )

        val nextDay = futureOccurrences(task, due, due + 4 * 86_400_000L, ny).map { it.dueTs }

        // 1 Nov 2026 is the end of DST in New York: the second occurrence is 25 hours after the first.
        assertEquals(
            listOf(1, 2, 3).map { LocalDateTime.of(2026, 11, it, 10, 0).atZone(ny).toInstant().toEpochMilli() },
            nextDay
        )
    }

    @Test
    fun `an all-day task keeps all-day conventions on the right local dates`() {
        val later = futureOccurrences(allDay("FREQ=WEEKLY"), ms(2026, 11, 1), ms(2026, 11, 14), zone)

        assertEquals(listOf(20261105, 20261112), later.map { it.startDay })
        val first = later.first()
        assertTrue(first.isAllDay)
        // UTC midnight of the date for the views, local midnight for the due date Tasks.org stores.
        assertEquals(LocalDate.of(2026, 11, 5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), first.startTs)
        assertEquals(first.startTs + 24L * 60 * 60 * 1000 - 1, first.endTs)
        assertEquals(LocalDate.of(2026, 11, 5).atStartOfDay(zone).toInstant().toEpochMilli(), first.dueTs)
    }

    @Test
    fun `a rule with a count shows only what is left, current occurrence included`() {
        // COUNT=3 left: the current one plus two more.
        assertEquals(
            listOf(20261105, 20261112),
            days(timed("FREQ=WEEKLY;COUNT=3"), ms(2026, 10, 29), ms(2027, 3, 1))
        )
        // Last one left: nothing after it.
        assertTrue(days(timed("FREQ=WEEKLY;COUNT=1"), ms(2026, 10, 29), ms(2027, 3, 1)).isEmpty())
    }

    @Test
    fun `a rule with an end date stops there`() {
        val task = timed("FREQ=DAILY;UNTIL=20261102T235959Z")

        assertEquals(listOf(20261030, 20261031, 20261101, 20261102), days(task, ms(2026, 10, 29), ms(2026, 12, 31)))
    }

    @Test
    fun `the rule is read with or without its prefix`() {
        val range = ms(2026, 11, 1) to ms(2026, 11, 7)
        assertEquals(listOf(20261105), days(timed("RRULE:FREQ=WEEKLY"), range.first, range.second))
    }

    @Test
    fun `only the part of the range after the current occurrence is produced`() {
        val task = timed("FREQ=DAILY")

        // A range that ends before the task's current date has nothing to add.
        assertTrue(days(task, ms(2026, 10, 1), ms(2026, 10, 28)).isEmpty())
        // A range that straddles it starts after it.
        assertEquals(listOf(20261030), days(task, ms(2026, 10, 25), ms(2026, 10, 30, 23, 59)))
    }

    @Test
    fun `a task that is not repeating, completed, or already a later occurrence adds nothing`() {
        val range = ms(2026, 10, 29) to ms(2026, 12, 31)
        assertTrue(days(timed(null), range.first, range.second).isEmpty())
        assertTrue(days(timed("FREQ=DAILY").copy(completedAt = 1L), range.first, range.second).isEmpty())
        assertTrue(days(timed("FREQ=DAILY").copy(isFutureOccurrence = true), range.first, range.second).isEmpty())
    }

    @Test
    fun `a task that repeats from its completion date adds nothing, since the next date is not known`() {
        val task = timed("FREQ=DAILY").copy(repeatFrom = TasksOrgRepeatFrom.COMPLETION_DATE)

        assertTrue(days(task, ms(2026, 10, 29), ms(2026, 12, 31)).isEmpty())
    }

    @Test
    fun `a rule that does not parse adds nothing`() {
        assertTrue(days(timed("nonsense"), ms(2026, 10, 29), ms(2026, 12, 31)).isEmpty())
        assertTrue(days(timed("  "), ms(2026, 10, 29), ms(2026, 12, 31)).isEmpty())
    }

    @Test
    fun `an hourly rule gives one occurrence per day`() {
        val perDay = days(timed("FREQ=HOURLY"), ms(2026, 10, 29), ms(2026, 11, 2, 23, 59))

        assertEquals(perDay.distinct(), perDay)
        assertEquals(listOf(20261029, 20261030, 20261031, 20261101, 20261102), perDay)
    }

    @Test
    fun `one task adds a bounded number of occurrences`() {
        val many = futureOccurrences(timed("FREQ=DAILY"), ms(2026, 10, 29), ms(2030, 1, 1), zone)

        assertEquals(MAX_FUTURE_OCCURRENCES, many.size)
        assertFalse(many.any { !it.isFutureOccurrence })
    }
}
