package org.onekash.kashcal.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Pure JUnit 4 unit tests for SplitAgendaGrouping engine.
 *
 * Covers:
 * 1) Filtering past events on Today (isPast == true)
 * 2) Keeping in-progress events on Today (isPast == false)
 * 3) Mandatory "Hoje" section even when empty (emitting EmptyTodayNotice)
 * 4) Omission of future days without events (and days outside 3-day window)
 * 5) Application of maxEventsPerDay limit strictly to future days (Tomorrow, Day+2), not Today
 * 6) Grouping of pending and completed tasks alongside calendar events
 * 7) Relative date header formatting ("Hoje", "Amanhã", "Dia-da-semana, data")
 */
class SplitAgendaGroupingTest {

    private fun makeEvent(
        eventId: Long = 1L,
        title: String = "Event",
        startTs: Long = 1000L,
        endTs: Long = 2000L,
        isAllDay: Boolean = false,
        calendarColor: Int = 0xFF2196F3.toInt(),
        isPast: Boolean = false,
        isDeviceEvent: Boolean = false,
        startDay: Int = 20260428,
        isCancelled: Boolean = false,
        isTask: Boolean = false,
        isCompleted: Boolean = false,
        taskId: Long? = null
    ) = WidgetDataRepository.WidgetEvent(
        eventId = eventId,
        occurrenceStartTs = startTs,
        title = title,
        startTs = startTs,
        endTs = endTs,
        isAllDay = isAllDay,
        calendarColor = calendarColor,
        isPast = isPast,
        isDeviceEvent = isDeviceEvent,
        startDay = startDay,
        isCancelled = isCancelled,
        isTask = isTask,
        isCompleted = isCompleted,
        taskId = taskId
    )

    // ==================== 1. Filtering Past Events on Today ====================

    @Test
    fun `past events on today are filtered out`() {
        val today = 20260428
        val pastEvent1 = makeEvent(eventId = 1, title = "Past Meeting", isPast = true, startDay = today)
        val pastEvent2 = makeEvent(eventId = 2, title = "Past Lunch", isPast = true, startDay = today)
        val futureEvent = makeEvent(eventId = 3, title = "Upcoming Dinner", isPast = false, startDay = today)

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(today to listOf(pastEvent1, pastEvent2, futureEvent)),
            todayDayCode = today,
            nowMs = 1714330000000L,
            maxEventsPerDay = 3
        )

        val eventRows = items.filterIsInstance<SplitAgendaItem.EventRow>()
        assertEquals(1, eventRows.size)
        assertEquals("Upcoming Dinner", eventRows[0].event.title)
        assertFalse(eventRows.any { it.event.isPast })
    }

    // ==================== 2. Keeping In-Progress Events on Today ====================

    @Test
    fun `in-progress events on today are kept`() {
        // Event that started earlier but has not ended yet: isPast is false
        val today = 20260428
        val inProgressEvent = makeEvent(
            eventId = 10,
            title = "Sprint Planning (in progress)",
            startTs = 1000L,
            endTs = 5000L,
            isPast = false,
            startDay = today
        )
        val finishedEvent = makeEvent(
            eventId = 11,
            title = "Morning Standup (finished)",
            startTs = 100L,
            endTs = 500L,
            isPast = true,
            startDay = today
        )

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(today to listOf(finishedEvent, inProgressEvent)),
            todayDayCode = today,
            nowMs = 2500L,
            maxEventsPerDay = 3
        )

        val eventRows = items.filterIsInstance<SplitAgendaItem.EventRow>()
        assertEquals(1, eventRows.size)
        assertEquals("Sprint Planning (in progress)", eventRows[0].event.title)
        assertFalse(eventRows[0].event.isPast)
    }

    // ==================== 3. Mandatory "Hoje" Section When Empty ====================

    @Test
    fun `today section is always present with EmptyTodayNotice when eventsByDay is empty`() {
        val today = 20260428
        val items = buildSplitUpcomingItems(
            eventsByDay = emptyMap(),
            todayDayCode = today,
            nowMs = 1000L,
            maxEventsPerDay = 3
        )

        assertEquals(2, items.size)
        assertTrue(items[0] is SplitAgendaItem.DayHeader)
        val header = items[0] as SplitAgendaItem.DayHeader
        assertEquals(today, header.dayCode)
        assertEquals("Hoje", header.label)
        assertEquals(0, header.eventCount)

        assertTrue(items[1] is SplitAgendaItem.EmptyTodayNotice)
        val notice = items[1] as SplitAgendaItem.EmptyTodayNotice
        assertEquals(today, notice.dayCode)
        assertEquals("Nenhum evento hoje", notice.message)
    }

    @Test
    fun `today section emits EmptyTodayNotice when all today events are past`() {
        val today = 20260428
        val tomorrow = 20260429
        val pastEvent = makeEvent(eventId = 1, isPast = true, startDay = today)
        val tomorrowEvent = makeEvent(eventId = 2, isPast = false, startDay = tomorrow)

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(
                today to listOf(pastEvent),
                tomorrow to listOf(tomorrowEvent)
            ),
            todayDayCode = today,
            nowMs = 1000L,
            maxEventsPerDay = 3
        )

        // Today header + EmptyTodayNotice, followed by Tomorrow header + tomorrowEvent
        assertEquals(4, items.size)
        assertTrue(items[0] is SplitAgendaItem.DayHeader)
        assertEquals(today, (items[0] as SplitAgendaItem.DayHeader).dayCode)

        assertTrue(items[1] is SplitAgendaItem.EmptyTodayNotice)
        assertEquals(today, (items[1] as SplitAgendaItem.EmptyTodayNotice).dayCode)

        assertTrue(items[2] is SplitAgendaItem.DayHeader)
        assertEquals(tomorrow, (items[2] as SplitAgendaItem.DayHeader).dayCode)
        assertEquals("Amanhã", (items[2] as SplitAgendaItem.DayHeader).label)

        assertTrue(items[3] is SplitAgendaItem.EventRow)
        assertEquals(tomorrow, (items[3] as SplitAgendaItem.EventRow).dayCode)
        assertEquals(2L, (items[3] as SplitAgendaItem.EventRow).event.eventId)
    }

    // ==================== 4. Omission of Future Days Without Events ====================

    @Test
    fun `future days without events are omitted`() {
        val today = 20260428
        val dayAfterTomorrow = 20260430 // 2 days later, tomorrow (20260429) has no events

        val todayEvent = makeEvent(eventId = 1, title = "Today 1", isPast = false, startDay = today)
        val day2Event = makeEvent(eventId = 2, title = "Day 2 Event", isPast = false, startDay = dayAfterTomorrow)

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(
                today to listOf(todayEvent),
                // tomorrow is absent from map
                dayAfterTomorrow to listOf(day2Event)
            ),
            todayDayCode = today,
            nowMs = 1000L,
            maxEventsPerDay = 3
        )

        val headers = items.filterIsInstance<SplitAgendaItem.DayHeader>()
        assertEquals(2, headers.size)
        assertEquals(today, headers[0].dayCode)
        assertEquals(dayAfterTomorrow, headers[1].dayCode)

        // Verify tomorrow was completely omitted
        assertFalse(items.any { (it as? SplitAgendaItem.DayHeader)?.dayCode == 20260429 })
    }

    @Test
    fun `days outside the 3-day window are ignored`() {
        val yesterday = 20260427
        val today = 20260428
        val tomorrow = 20260429
        val day2 = 20260430
        val day3 = 20260501 // 4th day (outside window)

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(
                yesterday to listOf(makeEvent(eventId = 99, startDay = yesterday)),
                today to listOf(makeEvent(eventId = 1, startDay = today)),
                tomorrow to listOf(makeEvent(eventId = 2, startDay = tomorrow)),
                day2 to listOf(makeEvent(eventId = 3, startDay = day2)),
                day3 to listOf(makeEvent(eventId = 100, startDay = day3))
            ),
            todayDayCode = today,
            nowMs = 1000L,
            maxEventsPerDay = 5
        )

        val headers = items.filterIsInstance<SplitAgendaItem.DayHeader>().map { it.dayCode }
        assertEquals(listOf(today, tomorrow, day2), headers)
        val eventIds = items.filterIsInstance<SplitAgendaItem.EventRow>().map { it.event.eventId }
        assertEquals(listOf(1L, 2L, 3L), eventIds)
    }

    // ==================== 5. Application of maxEventsPerDay Only on Future Days ====================

    @Test
    fun `today events are not capped by maxEventsPerDay while future days are capped`() {
        val today = 20260428
        val tomorrow = 20260429
        val day2 = 20260430
        val maxEvents = 2

        // 5 events for today, 4 events for tomorrow, 3 events for day2
        val todayEvents = (1..5).map { makeEvent(eventId = it.toLong(), title = "Today $it", startDay = today) }
        val tomorrowEvents = (10..13).map { makeEvent(eventId = it.toLong(), title = "Tomorrow $it", startDay = tomorrow) }
        val day2Events = (20..22).map { makeEvent(eventId = it.toLong(), title = "Day2 $it", startDay = day2) }

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(
                today to todayEvents,
                tomorrow to tomorrowEvents,
                day2 to day2Events
            ),
            todayDayCode = today,
            nowMs = 1000L,
            maxEventsPerDay = maxEvents
        )

        // Today must have ALL 5 events
        val todayRendered = items.filterIsInstance<SplitAgendaItem.EventRow>().filter { it.dayCode == today }
        assertEquals(5, todayRendered.size)

        // Tomorrow must be capped at 2
        val tomorrowRendered = items.filterIsInstance<SplitAgendaItem.EventRow>().filter { it.dayCode == tomorrow }
        assertEquals(2, tomorrowRendered.size)
        assertEquals(10L, tomorrowRendered[0].event.eventId)
        assertEquals(11L, tomorrowRendered[1].event.eventId)

        // Day2 must be capped at 2
        val day2Rendered = items.filterIsInstance<SplitAgendaItem.EventRow>().filter { it.dayCode == day2 }
        assertEquals(2, day2Rendered.size)
        assertEquals(20L, day2Rendered[0].event.eventId)
        assertEquals(21L, day2Rendered[1].event.eventId)
    }

    // ==================== 6. Grouping Pending and Completed Tasks ====================

    @Test
    fun `tasks pending and completed are grouped alongside events`() {
        val today = 20260428
        val event1 = makeEvent(eventId = 1, title = "Standup", isTask = false, startDay = today)
        val pendingTask = makeEvent(
            eventId = 2,
            title = "Review PR",
            isTask = true,
            isCompleted = false,
            taskId = 101L,
            startDay = today
        )
        val completedTask = makeEvent(
            eventId = 3,
            title = "Submit Report",
            isTask = true,
            isCompleted = true,
            taskId = 102L,
            startDay = today
        )

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(today to listOf(event1, pendingTask, completedTask)),
            todayDayCode = today,
            nowMs = 1000L,
            maxEventsPerDay = 5
        )

        val eventRows = items.filterIsInstance<SplitAgendaItem.EventRow>()
        assertEquals(3, eventRows.size)

        val row0 = eventRows[0]
        assertFalse(row0.event.isTask)
        assertEquals("Standup", row0.event.title)

        val row1 = eventRows[1]
        assertTrue(row1.event.isTask)
        assertFalse(row1.event.isCompleted)
        assertEquals(101L, row1.event.taskId)

        val row2 = eventRows[2]
        assertTrue(row2.event.isTask)
        assertTrue(row2.event.isCompleted)
        assertEquals(102L, row2.event.taskId)
    }

    // ==================== 7. Header Labels & Date Formatting ====================

    @Test
    fun `header labels format correctly across month and year boundaries`() {
        // Cross month boundary: April 30 -> May 1 (Tomorrow) -> May 2 (Day+2)
        val todayApr30 = 20260430
        val tomorrowMay1 = 20260501
        val dayMay2 = 20260502

        val itemsMonthEnd = buildSplitUpcomingItems(
            eventsByDay = mapOf(
                todayApr30 to listOf(makeEvent(eventId = 1, startDay = todayApr30)),
                tomorrowMay1 to listOf(makeEvent(eventId = 2, startDay = tomorrowMay1)),
                dayMay2 to listOf(makeEvent(eventId = 3, startDay = dayMay2))
            ),
            todayDayCode = todayApr30,
            nowMs = 1000L,
            maxEventsPerDay = 3,
            locale = Locale.ENGLISH
        )

        val headers = itemsMonthEnd.filterIsInstance<SplitAgendaItem.DayHeader>()
        assertEquals(3, headers.size)
        assertEquals("Hoje", headers[0].label)
        assertEquals("Amanhã", headers[1].label)
        // 2026-05-02 is Saturday
        assertEquals("Sat, May 2", headers[2].label)
    }

    @Test
    fun `custom labels can be passed for localization`() {
        val today = 20260428
        val tomorrow = 20260429

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(
                today to emptyList(),
                tomorrow to listOf(makeEvent(eventId = 1, startDay = tomorrow))
            ),
            todayDayCode = today,
            nowMs = 1000L,
            maxEventsPerDay = 3,
            todayLabel = "Today",
            tomorrowLabel = "Tomorrow",
            emptyNoticeMessage = "No events today"
        )

        val headerToday = items[0] as SplitAgendaItem.DayHeader
        assertEquals("Today", headerToday.label)

        val noticeToday = items[1] as SplitAgendaItem.EmptyTodayNotice
        assertEquals("No events today", noticeToday.message)

        val headerTomorrow = items[2] as SplitAgendaItem.DayHeader
        assertEquals("Tomorrow", headerTomorrow.label)
    }
}
