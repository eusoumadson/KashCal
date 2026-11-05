package org.onekash.kashcal.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onekash.kashcal.data.db.entity.Calendar
import org.onekash.kashcal.data.db.entity.Event
import org.onekash.kashcal.data.db.entity.Occurrence
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.onekash.kashcal.domain.model.DisplayEvent
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AgendaListModelTest {

    private var nextId = 1L

    private fun roomEvent(
        startTs: Long,
        startDay: Int,
        endDay: Int = startDay,
        title: String = "Event"
    ): DisplayEvent.Room {
        val id = nextId++
        // A timed event's days come from its instants, so place [startTs] (an offset that orders
        // events within a day) on [startDay] in UTC and end it on [endDay].
        val start = utcMidnight(startDay) + startTs
        val end = if (endDay > startDay) utcMidnight(endDay) + startTs else start + 3_600_000L
        val event = Event(id = id, uid = "uid-$id", calendarId = 1L, title = title, startTs = start, endTs = end, dtstamp = start)
        val occ = Occurrence(eventId = id, calendarId = 1L, startTs = start, endTs = end, startDay = startDay, endDay = endDay)
        val cal = Calendar(id = 1L, accountId = 1L, caldavUrl = "https://example.com/cal/", displayName = "Cal", color = 0xFF0000.toInt())
        return DisplayEvent.Room(event, occ, cal, zone = ZoneOffset.UTC)
    }

    private fun utcMidnight(dayCode: Int): Long =
        LocalDate.of(dayCode / 10000, dayCode / 100 % 100, dayCode % 100)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun taskEvent(
        id: Long,
        dayCode: Int,
        isAllDay: Boolean = true,
        title: String = "Task $id",
        startTs: Long = 0L
    ): DisplayEvent.Task {
        val start = if (isAllDay) utcMidnight(dayCode) else utcMidnight(dayCode) + startTs
        val task = TasksOrgTask(
            id = id,
            title = title,
            notes = "",
            dueTs = start,
            isAllDay = isAllDay,
            startTs = start,
            endTs = start,
            startDay = dayCode,
            endDay = dayCode,
            completedAt = null,
            recurrence = null,
            listId = 1L,
            listTitle = "List",
            listColor = 0xFF2196F3.toInt(),
            isReadOnly = false
        )
        return DisplayEvent.Task(task, ZoneOffset.UTC)
    }

    @Test
    fun `single-day event yields one item with total day count 1`() {
        val model = buildAgendaListModel(listOf(roomEvent(1000L, 20260718)), todayDayCode = 20260718)
        assertEquals(1, model.groups.size)
        assertEquals(20260718, model.groups[0].dayCode)
        assertEquals(1, model.groups[0].items.size)
        assertEquals(1, model.groups[0].items[0].totalDays)
        assertEquals(1, model.groups[0].items[0].dayNumber)
    }

    @Test
    fun `multi-day event expands to one item per spanned day with Day X of Y`() {
        // Jul 18 -> Jul 20 inclusive = 3 days.
        val model = buildAgendaListModel(listOf(roomEvent(1000L, 20260718, endDay = 20260720)), todayDayCode = 20260718)
        assertEquals(listOf(20260718, 20260719, 20260720), model.groups.map { it.dayCode })
        val allItems = model.groups.flatMap { it.items }
        assertEquals(3, allItems.size)
        assertTrue(allItems.all { it.totalDays == 3 })
        assertEquals(listOf(1, 2, 3), allItems.map { it.dayNumber })
    }

    @Test
    fun `duplicate title-startTs-day collapses to one item`() {
        val e1 = roomEvent(1000L, 20260718, title = "Same")
        // Same title + same startTs + same day -> deduped.
        val e2Event = e1.event.copy(id = 99L)
        val e2 = DisplayEvent.Room(e2Event, e1.occurrence, e1.calendar)
        val model = buildAgendaListModel(listOf(e1, e2), todayDayCode = 20260718)
        assertEquals(1, model.groups[0].items.size)
    }

    @Test
    fun `events before today are filtered out`() {
        val past = roomEvent(1000L, 20260716)
        val today = roomEvent(2000L, 20260718)
        val model = buildAgendaListModel(listOf(past, today), todayDayCode = 20260718)
        assertEquals(listOf(20260718), model.groups.map { it.dayCode })
    }

    @Test
    fun `groups are ordered by day then start time within a day`() {
        val later = roomEvent(5000L, 20260718, title = "Later")
        val earlier = roomEvent(2000L, 20260718, title = "Earlier")
        val nextDay = roomEvent(1000L, 20260719, title = "NextDay")
        val model = buildAgendaListModel(listOf(later, earlier, nextDay), todayDayCode = 20260718)
        assertEquals(listOf(20260718, 20260719), model.groups.map { it.dayCode })
        assertEquals(listOf("Earlier", "Later"), model.groups[0].items.map { it.displayEvent.title })
    }

    @Test
    fun `header index math accounts for one header plus N cards per preceding group`() {
        // Day A: 2 cards. Day B: 1 card.
        val a1 = roomEvent(1000L, 20260718, title = "A1")
        val a2 = roomEvent(2000L, 20260718, title = "A2")
        val b1 = roomEvent(3000L, 20260719, title = "B1")
        val model = buildAgendaListModel(listOf(a1, a2, b1), todayDayCode = 20260718)
        // Flat layout: [0]=headerA, [1]=A1, [2]=A2, [3]=headerB, [4]=B1
        assertEquals(0, model.headerIndexByDayCode[20260718])
        assertEquals(3, model.headerIndexByDayCode[20260719])
    }

    @Test
    fun `resolveScrollTargetIndex returns exact header index`() {
        val a = roomEvent(1000L, 20260718)
        val b = roomEvent(2000L, 20260720)
        val model = buildAgendaListModel(listOf(a, b), todayDayCode = 20260718)
        assertEquals(0, resolveScrollTargetIndex(20260718, model))
        // headerB at index 2 ([0]=headerA,[1]=A,[2]=headerB,[3]=B)
        assertEquals(2, resolveScrollTargetIndex(20260720, model))
    }

    @Test
    fun `resolveScrollTargetIndex falls to the nearest following day`() {
        val a = roomEvent(1000L, 20260718)
        val c = roomEvent(2000L, 20260720)
        val model = buildAgendaListModel(listOf(a, c), todayDayCode = 20260718)
        // Tapping Jul 19 (no events) -> nearest following header = Jul 20 at index 2.
        assertEquals(2, resolveScrollTargetIndex(20260719, model))
    }

    @Test
    fun `resolveScrollTargetIndex past the last day clamps to the last header`() {
        val a = roomEvent(1000L, 20260718)
        val b = roomEvent(2000L, 20260720)
        val model = buildAgendaListModel(listOf(a, b), todayDayCode = 20260718)
        // Tapping beyond the last group -> last header (Jul 20 at index 2).
        assertEquals(2, resolveScrollTargetIndex(20260805, model))
    }

    @Test
    fun `resolveScrollTargetIndex on empty model returns -1`() {
        val model = buildAgendaListModel(emptyList(), todayDayCode = 20260718)
        assertTrue(model.groups.isEmpty())
        assertEquals(-1, resolveScrollTargetIndex(20260718, model))
    }

    @Test
    fun `multiple all-day tasks are stacked leaving 1 task in items and rest in overflow`() {
        val tasks = (1..8).map { taskEvent(it.toLong(), 20260718, isAllDay = true, title = "Task $it") }
        val model = buildAgendaListModel(tasks, todayDayCode = 20260718)

        assertEquals(1, model.groups.size)
        val group = model.groups[0]
        assertEquals(20260718, group.dayCode)
        assertEquals(1, group.items.size)
        assertEquals("Task 1", (group.items[0].displayEvent as DisplayEvent.Task).task.title)
        assertEquals(7, group.overflowTasks.size)
        assertEquals((2..8).map { "Task $it" }, group.overflowTasks.map { (it.displayEvent as DisplayEvent.Task).task.title })
    }

    @Test
    fun `day with 8 all-day tasks and 2 timed meetings preserves meetings in items and offsets header index`() {
        val tasks = (1..8).map { taskEvent(it.toLong(), 20260718, isAllDay = true, title = "Task $it") }
        val meeting1 = roomEvent(3_600_000L, 20260718, title = "Meeting 1")
        val meeting2 = roomEvent(7_200_000L, 20260718, title = "Meeting 2")
        val nextDayEvent = roomEvent(1_000L, 20260719, title = "Tomorrow Event")

        val model = buildAgendaListModel(tasks + listOf(meeting1, meeting2, nextDayEvent), todayDayCode = 20260718)

        assertEquals(2, model.groups.size)
        val group1 = model.groups[0]
        // 1st task + 2 meetings = 3 items in group1.items
        assertEquals(3, group1.items.size)
        assertEquals(7, group1.overflowTasks.size)

        // Flat indices:
        // Group 1 header = index 0.
        // Group 1 elements: 1 header + 3 items + 1 overflow card = 5.
        // Group 2 header = index 5.
        assertEquals(0, model.headerIndexByDayCode[20260718])
        assertEquals(5, model.headerIndexByDayCode[20260719])
    }

    @Test
    fun `single all-day task produces no overflow`() {
        val singleTask = taskEvent(1L, 20260718, isAllDay = true)
        val meeting = roomEvent(3_600_000L, 20260718, title = "Meeting")

        val model = buildAgendaListModel(listOf(singleTask, meeting), todayDayCode = 20260718)

        assertEquals(1, model.groups.size)
        val group = model.groups[0]
        assertEquals(2, group.items.size)
        assertTrue(group.overflowTasks.isEmpty())
        assertEquals(0, model.headerIndexByDayCode[20260718])
    }

    @Test
    fun `multiple timed tasks are not grouped into all-day overflow`() {
        val timedTasks = (1..5).map {
            taskEvent(it.toLong(), 20260718, isAllDay = false, title = "Timed Task $it", startTs = it * 1000L)
        }
        val model = buildAgendaListModel(timedTasks, todayDayCode = 20260718)

        assertEquals(1, model.groups.size)
        val group = model.groups[0]
        assertEquals(5, group.items.size)
        assertTrue(group.overflowTasks.isEmpty())
    }
}
