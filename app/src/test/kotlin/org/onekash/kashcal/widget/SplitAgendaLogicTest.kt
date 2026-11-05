package org.onekash.kashcal.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for SplitAgenda dynamic 2-day window and upcoming item grouping logic.
 *
 * Covers:
 * - RF-04 / AC-03: Dynamic 2-day window (Today + Tomorrow if today has future/in-progress events;
 *   advances automatically to Tomorrow + Day+2 when today has only past events or no events).
 * - Filtering out past events on today.
 * - Keeping in-progress events on today.
 * - Omission of today when it has no non-past events.
 * - Capping of events on future days by maxEventsPerDay.
 */
class SplitAgendaLogicTest {

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

    // ==================== RF-04 / AC-03 Dynamic 2-Day Window ====================

    @Test
    fun `auto advance window to tomorrow and D+2 when today has only past events`() {
        val today = 20260428
        val tomorrow = 20260429
        val day2 = 20260430

        val pastEvent1 = makeEvent(eventId = 1, title = "Past Meeting 1", isPast = true, startDay = today)
        val pastEvent2 = makeEvent(eventId = 2, title = "Past Meeting 2", isPast = true, startDay = today)
        val tomorrowEvent = makeEvent(eventId = 3, title = "Tomorrow Standup", isPast = false, startDay = tomorrow)
        val day2Event = makeEvent(eventId = 4, title = "D+2 Workshop", isPast = false, startDay = day2)

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(
                today to listOf(pastEvent1, pastEvent2),
                tomorrow to listOf(tomorrowEvent),
                day2 to listOf(day2Event)
            ),
            todayDayCode = today,
            maxEventsPerDay = 3
        )

        val headers = items.filterIsInstance<SplitAgendaItem.DayHeader>()
        assertEquals(2, headers.size)
        // Today must NOT be emitted
        assertFalse(headers.any { it.dayCode == today })
        assertEquals(tomorrow, headers[0].dayCode)
        assertEquals("Amanhã", headers[0].label)
        assertEquals(day2, headers[1].dayCode)

        val eventRows = items.filterIsInstance<SplitAgendaItem.EventRow>()
        assertEquals(2, eventRows.size)
        assertEquals("Tomorrow Standup", eventRows[0].event.title)
        assertEquals("D+2 Workshop", eventRows[1].event.title)
        assertFalse(eventRows.any { it.dayCode == today })
    }

    @Test
    fun `auto advance window to tomorrow and D+2 when today has no events`() {
        val today = 20260428
        val tomorrow = 20260429
        val day2 = 20260430

        val tomorrowEvent = makeEvent(eventId = 3, title = "Tomorrow Standup", isPast = false, startDay = tomorrow)
        val day2Event = makeEvent(eventId = 4, title = "D+2 Workshop", isPast = false, startDay = day2)

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(
                tomorrow to listOf(tomorrowEvent),
                day2 to listOf(day2Event)
            ),
            todayDayCode = today,
            maxEventsPerDay = 3
        )

        val headers = items.filterIsInstance<SplitAgendaItem.DayHeader>()
        assertEquals(2, headers.size)
        assertFalse(headers.any { it.dayCode == today })
        assertEquals(tomorrow, headers[0].dayCode)
        assertEquals(day2, headers[1].dayCode)
    }

    @Test
    fun `shows today and tomorrow and ignores D+2 when today has remaining events`() {
        val today = 20260428
        val tomorrow = 20260429
        val day2 = 20260430

        val todayPast = makeEvent(eventId = 1, title = "Finished Morning Event", isPast = true, startDay = today)
        val todayFuture = makeEvent(eventId = 2, title = "Upcoming Evening Event", isPast = false, startDay = today)
        val tomorrowEvent = makeEvent(eventId = 3, title = "Tomorrow Event", isPast = false, startDay = tomorrow)
        val day2Event = makeEvent(eventId = 4, title = "D+2 Event", isPast = false, startDay = day2)

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(
                today to listOf(todayPast, todayFuture),
                tomorrow to listOf(tomorrowEvent),
                day2 to listOf(day2Event)
            ),
            todayDayCode = today,
            maxEventsPerDay = 3
        )

        val headers = items.filterIsInstance<SplitAgendaItem.DayHeader>()
        assertEquals(2, headers.size)
        assertEquals(today, headers[0].dayCode)
        assertEquals("Hoje", headers[0].label)
        assertEquals(tomorrow, headers[1].dayCode)
        assertEquals("Amanhã", headers[1].label)

        // D+2 must NOT be emitted when today has active events
        assertFalse(headers.any { it.dayCode == day2 })

        val eventRows = items.filterIsInstance<SplitAgendaItem.EventRow>()
        assertEquals(2, eventRows.size)
        assertEquals("Upcoming Evening Event", eventRows[0].event.title)
        assertEquals("Tomorrow Event", eventRows[1].event.title)
        assertFalse(eventRows.any { it.event.eventId == 4L })
    }

    @Test
    fun `in-progress event on today keeps today section visible`() {
        val today = 20260428
        val tomorrow = 20260429

        val inProgress = makeEvent(
            eventId = 10,
            title = "Long Workshop (in progress)",
            startTs = 1000L,
            endTs = 5000L,
            isPast = false,
            startDay = today
        )
        val finished = makeEvent(eventId = 11, title = "Breakfast", isPast = true, startDay = today)
        val tomorrowEvent = makeEvent(eventId = 12, title = "Tomorrow Meeting", startDay = tomorrow)

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(
                today to listOf(finished, inProgress),
                tomorrow to listOf(tomorrowEvent)
            ),
            todayDayCode = today,
            nowMs = 2500L,
            maxEventsPerDay = 3
        )

        val headers = items.filterIsInstance<SplitAgendaItem.DayHeader>()
        assertEquals(2, headers.size)
        assertEquals(today, headers[0].dayCode)
        assertEquals("Hoje", headers[0].label)
        assertEquals(tomorrow, headers[1].dayCode)

        val eventRows = items.filterIsInstance<SplitAgendaItem.EventRow>()
        assertEquals(2, eventRows.size)
        assertEquals("Long Workshop (in progress)", eventRows[0].event.title)
        assertEquals("Tomorrow Meeting", eventRows[1].event.title)
    }

    @Test
    fun `empty eventsByDay produces empty list without today header`() {
        val today = 20260428
        val items = buildSplitUpcomingItems(
            eventsByDay = emptyMap(),
            todayDayCode = today,
            maxEventsPerDay = 3
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `custom labels and locales are respected`() {
        val today = 20260428
        val tomorrow = 20260429
        val day2 = 20260430

        val tomorrowEvent = makeEvent(eventId = 1, startDay = tomorrow)
        val day2Event = makeEvent(eventId = 2, startDay = day2)

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(
                tomorrow to listOf(tomorrowEvent),
                day2 to listOf(day2Event)
            ),
            todayDayCode = today,
            tomorrowLabel = "Tomorrow",
            locale = Locale.ENGLISH
        )

        val headers = items.filterIsInstance<SplitAgendaItem.DayHeader>()
        assertEquals(2, headers.size)
        assertEquals("Tomorrow", headers[0].label)
        // 2026-04-30 is Thursday
        assertEquals("Thu, Apr 30", headers[1].label)
    }

    // ==================== RF-08 / AC-05 1 Item + Stack Rules ====================

    @Test
    fun `day with 8 all-day tasks emits exactly 1 event row and 1 task stack with count 7`() {
        val today = 20260428
        val tasks = (1..8).map { i ->
            makeEvent(
                eventId = i.toLong(),
                title = "Task $i",
                isAllDay = true,
                isTask = true,
                taskId = 100L + i,
                startDay = today
            )
        }

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(today to tasks),
            todayDayCode = today,
            maxEventsPerDay = 3
        )

        // Today items: DayHeader, EventRow (Task 1), AllDayTaskStack (count = 7)
        val eventRows = items.filterIsInstance<SplitAgendaItem.EventRow>()
        assertEquals(1, eventRows.size)
        assertEquals("Task 1", eventRows[0].event.title)
        assertTrue(eventRows[0].event.isTask)
        assertTrue(eventRows[0].event.isAllDay)

        val taskStacks = items.filterIsInstance<SplitAgendaItem.AllDayTaskStack>()
        assertEquals(1, taskStacks.size)
        assertEquals(today, taskStacks[0].dayCode)
        assertEquals(7, taskStacks[0].count)

        val eventStacks = items.filterIsInstance<SplitAgendaItem.AllDayEventStack>()
        assertTrue(eventStacks.isEmpty())
    }

    @Test
    fun `day with 4 all-day calendar events emits exactly 1 event row and 1 event stack with count 3`() {
        val today = 20260428
        val allDayEvents = (1..4).map { i ->
            makeEvent(
                eventId = i.toLong(),
                title = "Holiday $i",
                isAllDay = true,
                isTask = false,
                startDay = today
            )
        }

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(today to allDayEvents),
            todayDayCode = today,
            maxEventsPerDay = 3
        )

        val eventRows = items.filterIsInstance<SplitAgendaItem.EventRow>()
        assertEquals(1, eventRows.size)
        assertEquals("Holiday 1", eventRows[0].event.title)
        assertFalse(eventRows[0].event.isTask)
        assertTrue(eventRows[0].event.isAllDay)

        val eventStacks = items.filterIsInstance<SplitAgendaItem.AllDayEventStack>()
        assertEquals(1, eventStacks.size)
        assertEquals(today, eventStacks[0].dayCode)
        assertEquals(3, eventStacks[0].count)

        val taskStacks = items.filterIsInstance<SplitAgendaItem.AllDayTaskStack>()
        assertTrue(taskStacks.isEmpty())
    }

    @Test
    fun `single all-day event and single all-day task do not emit stacks`() {
        val today = 20260428
        val singleEvent = makeEvent(
            eventId = 1,
            title = "Birthday",
            isAllDay = true,
            isTask = false,
            startDay = today
        )
        val singleTask = makeEvent(
            eventId = 2,
            title = "Pay Rent",
            isAllDay = true,
            isTask = true,
            taskId = 55L,
            startDay = today
        )

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(today to listOf(singleEvent, singleTask)),
            todayDayCode = today,
            maxEventsPerDay = 3
        )

        val eventRows = items.filterIsInstance<SplitAgendaItem.EventRow>()
        assertEquals(2, eventRows.size)
        assertEquals("Birthday", eventRows[0].event.title)
        assertEquals("Pay Rent", eventRows[1].event.title)

        val eventStacks = items.filterIsInstance<SplitAgendaItem.AllDayEventStack>()
        assertTrue(eventStacks.isEmpty())

        val taskStacks = items.filterIsInstance<SplitAgendaItem.AllDayTaskStack>()
        assertTrue(taskStacks.isEmpty())
    }

    @Test
    fun `multiple all-day events and multiple all-day tasks emit separate stacks and timed items below`() {
        val today = 20260428
        val event1 = makeEvent(eventId = 1, title = "Event 1", isAllDay = true, isTask = false, startDay = today)
        val event2 = makeEvent(eventId = 2, title = "Event 2", isAllDay = true, isTask = false, startDay = today)
        val event3 = makeEvent(eventId = 3, title = "Event 3", isAllDay = true, isTask = false, startDay = today)

        val task1 = makeEvent(eventId = 10, title = "Task 1", isAllDay = true, isTask = true, taskId = 10L, startDay = today)
        val task2 = makeEvent(eventId = 11, title = "Task 2", isAllDay = true, isTask = true, taskId = 11L, startDay = today)

        val timedMeeting = makeEvent(
            eventId = 20,
            title = "Team Sync",
            startTs = 1714310000000L,
            endTs = 1714313600000L,
            isAllDay = false,
            isTask = false,
            startDay = today
        )

        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(today to listOf(event1, event2, event3, task1, task2, timedMeeting)),
            todayDayCode = today,
            maxEventsPerDay = 3
        )

        // Expected order:
        // 1. DayHeader
        // 2. EventRow (Event 1)
        // 3. AllDayEventStack (count = 2)
        // 4. EventRow (Task 1)
        // 5. AllDayTaskStack (count = 1)
        // 6. EventRow (Team Sync)
        assertEquals(6, items.size)

        assertTrue(items[0] is SplitAgendaItem.DayHeader)

        assertTrue(items[1] is SplitAgendaItem.EventRow)
        assertEquals("Event 1", (items[1] as SplitAgendaItem.EventRow).event.title)

        assertTrue(items[2] is SplitAgendaItem.AllDayEventStack)
        assertEquals(2, (items[2] as SplitAgendaItem.AllDayEventStack).count)

        assertTrue(items[3] is SplitAgendaItem.EventRow)
        assertEquals("Task 1", (items[3] as SplitAgendaItem.EventRow).event.title)

        assertTrue(items[4] is SplitAgendaItem.AllDayTaskStack)
        assertEquals(1, (items[4] as SplitAgendaItem.AllDayTaskStack).count)

        assertTrue(items[5] is SplitAgendaItem.EventRow)
        assertEquals("Team Sync", (items[5] as SplitAgendaItem.EventRow).event.title)
    }

    @Test
    fun `stacking applies to future days within window`() {
        val today = 20260428
        val tomorrow = 20260429

        val tomorrowTasks = (1..5).map { i ->
            makeEvent(
                eventId = (100 + i).toLong(),
                title = "Tomorrow Task $i",
                isAllDay = true,
                isTask = true,
                taskId = 200L + i,
                startDay = tomorrow
            )
        }

        // Today has no events -> window advances to tomorrow and D+2
        val items = buildSplitUpcomingItems(
            eventsByDay = mapOf(tomorrow to tomorrowTasks),
            todayDayCode = today,
            maxEventsPerDay = 3
        )

        val eventRows = items.filterIsInstance<SplitAgendaItem.EventRow>()
        assertEquals(1, eventRows.size)
        assertEquals("Tomorrow Task 1", eventRows[0].event.title)

        val taskStacks = items.filterIsInstance<SplitAgendaItem.AllDayTaskStack>()
        assertEquals(1, taskStacks.size)
        assertEquals(tomorrow, taskStacks[0].dayCode)
        assertEquals(4, taskStacks[0].count)
    }

    @Test
    fun `all-day stacks have distinct and stable itemIds`() {
        val dayCode = 20260428
        val eventStack = SplitAgendaItem.AllDayEventStack(dayCode = dayCode, count = 3)
        val taskStack = SplitAgendaItem.AllDayTaskStack(dayCode = dayCode, count = 5)

        assertEquals(dayCode.toLong() * 100_000L + 999_997L, eventStack.itemId)
        assertEquals(dayCode.toLong() * 100_000L + 999_998L, taskStack.itemId)
        assertFalse(eventStack.itemId == taskStack.itemId)
    }
}
