package org.onekash.kashcal.domain.reader

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.onekash.kashcal.data.calendar_provider.CalendarProviderManager
import org.onekash.kashcal.data.calendar_provider.dayCodeToEndOfDayMs
import org.onekash.kashcal.data.calendar_provider.dayCodeToStartOfDayMs
import org.onekash.kashcal.data.db.dao.AccountsDao
import org.onekash.kashcal.data.db.dao.AttendeesDao
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.tasks_org.TasksOrgManager
import org.onekash.kashcal.data.tasks_org.TasksOrgRepository
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.onekash.kashcal.domain.model.DisplayEvent

/**
 * Tests the Tasks.org source in [DisplayEventRepository]: tasks join every read only while the
 * source is on, with the hidden lists and the day range passed through, and a change signal
 * re-emits the views. Room and device calendars are empty here.
 */
class DisplayEventRepositoryTasksTest {

    private var tasksEnabled = true
    private val hiddenLists = setOf(9L)
    private val tasksSignal = MutableStateFlow(0)
    private lateinit var tasksRepository: TasksOrgRepository
    private lateinit var display: DisplayEventRepository

    private fun task(id: Long, startTs: Long, startDay: Int) = TasksOrgTask(
        id = id, title = "Task $id", notes = "", dueTs = startTs, isAllDay = false,
        startTs = startTs, endTs = startTs, startDay = startDay, endDay = startDay,
        completedAt = null, recurrence = null, listId = 1, listTitle = "Inbox", listColor = 0,
        isReadOnly = false
    )

    private val day1 = 20261029
    private val day2 = 20261030
    private val day1Task = task(1, dayCodeToStartOfDayMs(day1) + 3_600_000, day1)
    private val day2Task = task(2, dayCodeToStartOfDayMs(day2) + 7_200_000, day2)

    @Before
    fun setup() {
        val eventReader: EventReader = mockk {
            every { getVisibleOccurrencesWithEventsInRangeFlow(any(), any()) } returns flowOf(emptyList())
        }
        val calendarManager: CalendarProviderManager = mockk {
            every { changeSignal } returns MutableStateFlow(0)
        }
        val tasksManager: TasksOrgManager = mockk {
            every { changeSignal } returns tasksSignal
        }
        val dataStore: KashCalDataStore = mockk {
            coEvery { getShowDeclinedEvents() } returns false
            coEvery { getDeviceCalendarsEnabled() } returns false
            coEvery { getTasksOrgEnabled() } answers { tasksEnabled }
            coEvery { getHiddenTasksOrgListIds() } returns hiddenLists
            every { hiddenDeviceCalendarIds } returns flowOf(emptySet())
            every { showDeclinedEvents } returns flowOf(false)
        }
        tasksRepository = mockk {
            coEvery { getTasksDueInRange(any(), any(), any(), any(), any()) } returns listOf(day1Task, day2Task)
        }
        val attendeesDao: AttendeesDao = mockk { every { attendeesChangeSignal() } returns flowOf(0) }
        display = DisplayEventRepository(
            eventReader, mockk(), calendarManager, dataStore, attendeesDao, mockk<AccountsDao>(),
            tasksOrgRepository = tasksRepository, tasksOrgManager = tasksManager
        )
    }

    @Test
    fun `tasks are grouped by their due day with the range and hidden lists passed through`() = runTest {
        val byDay = display.getDisplayEventsGroupedByDayOnce(day1, day2)

        assertEquals(listOf(1L), byDay[day1]!!.map { (it as DisplayEvent.Task).task.id })
        assertEquals(listOf(2L), byDay[day2]!!.map { (it as DisplayEvent.Task).task.id })
        coVerify {
            tasksRepository.getTasksDueInRange(
                dayCodeToStartOfDayMs(day1), dayCodeToEndOfDayMs(day2), hiddenLists, null, any()
            )
        }
    }

    @Test
    fun `no task is read while the source is off`() = runTest {
        tasksEnabled = false

        assertTrue(display.getDisplayEventsGroupedByDayOnce(day1, day2).isEmpty())
        coVerify(exactly = 0) { tasksRepository.getTasksDueInRange(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the range flow lists tasks sorted by start`() = runTest {
        val events = display.getDisplayEventsForRange(dayCodeToStartOfDayMs(day1), dayCodeToEndOfDayMs(day2)).first()

        assertEquals(listOf(day1Task.startTs, day2Task.startTs), events.map { it.startTs })
    }

    @Test
    fun `search passes the query to Tasks org`() = runTest {
        val results = display.searchDisplayEvents("Task", day1, day2) { emptyList() }

        assertEquals(2, results.size)
        coVerify { tasksRepository.getTasksDueInRange(any(), any(), hiddenLists, "Task", any()) }
    }
}
