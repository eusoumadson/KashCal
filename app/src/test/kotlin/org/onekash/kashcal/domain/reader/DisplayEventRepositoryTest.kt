package org.onekash.kashcal.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.onekash.kashcal.data.calendar_provider.CalendarProviderManager
import org.onekash.kashcal.data.calendar_provider.CalendarProviderRepository
import org.onekash.kashcal.data.calendar_provider.DeviceCalendarInstance
import org.onekash.kashcal.data.calendar_provider.FakeCalendarProviderRepository
import org.onekash.kashcal.data.db.dao.AccountsDao
import org.onekash.kashcal.data.db.dao.AttendeesDao
import org.onekash.kashcal.data.db.entity.Calendar
import org.onekash.kashcal.data.db.entity.Event
import org.onekash.kashcal.data.db.entity.Occurrence
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.tasks_org.TasksOrgManager
import org.onekash.kashcal.data.tasks_org.TasksOrgRepository
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.onekash.kashcal.domain.model.DisplayEvent
import org.onekash.kashcal.domain.model.SearchResult
import org.onekash.kashcal.util.ZoneSource
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Tests the day-code helpers of [DisplayEventRepository], [generateDayCodesInRange] and
 * [spannedDayCodesWithinWindow], and the instance, search and SecurityException behavior of
 * [FakeCalendarProviderRepository].
 *
 * The merge, grouping, multi-day expansion and search-merge tests run inline `sortedBy`,
 * `groupBy` and unclamped expansion code modeled on the repository and don't call it; the
 * repository expands through [spannedDayCodesWithinWindow].
 * [DisplayEventRepositoryDeviceRealProviderTest] drives its Flow and one-shot reads.
 */
class DisplayEventRepositoryTest {

    // ========== Merge Logic ==========

    @Test
    fun `merge sorts by startTs`() {
        val room = listOf(
            roomEvent(startTs = 1000L, title = "Room Event"),
        )
        val device = listOf(
            deviceEvent(startTs = 500L, title = "Device Event"),
        )

        val merged = (room + device).sortedBy { it.startTs }
        assertEquals("Device Event", merged[0].title)
        assertEquals("Room Event", merged[1].title)
    }

    @Test
    fun `merge with empty device events returns room only`() {
        val room = listOf(
            roomEvent(startTs = 1000L, title = "Room Event"),
        )
        val device = emptyList<DisplayEvent>()

        val merged = (room + device).sortedBy { it.startTs }
        assertEquals(1, merged.size)
        assertEquals("Room Event", merged[0].title)
    }

    @Test
    fun `merge with empty room events returns device only`() {
        val room = emptyList<DisplayEvent>()
        val device = listOf(
            deviceEvent(startTs = 1000L, title = "Device Event"),
        )

        val merged = (room + device).sortedBy { it.startTs }
        assertEquals(1, merged.size)
        assertEquals("Device Event", merged[0].title)
    }

    @Test
    fun `merge with both empty returns empty`() {
        val merged = (emptyList<DisplayEvent>() + emptyList<DisplayEvent>())
            .sortedBy { it.startTs }
        assertTrue(merged.isEmpty())
    }

    // ========== Day Grouping ==========

    @Test
    fun `events group by startDay`() {
        val events = listOf(
            roomEvent(startDay = 20260215, title = "Feb 15 Event"),
            deviceEvent(startDay = 20260216, title = "Feb 16 Event"),
            roomEvent(startDay = 20260215, title = "Another Feb 15"),
        )

        val grouped = events
            .groupBy { it.startDay }
            .mapValues { (_, list) -> list.sortedBy { it.startTs } }

        assertEquals(2, grouped.size)
        assertEquals(2, grouped[20260215]?.size)
        assertEquals(1, grouped[20260216]?.size)
    }

    // ========== Multi-Day Expansion ==========

    @Test
    fun `multi-day event appears in all spanned days`() {
        val event = roomEvent(startDay = 20260215, endDay = 20260217, title = "3-day")

        val expanded = if (event.startDay == event.endDay) {
            listOf(event.startDay to event)
        } else {
            generateDayCodesInRange(event.startDay, event.endDay)
                .map { dayCode -> dayCode to event }
        }

        assertEquals(3, expanded.size)
        assertEquals(20260215, expanded[0].first)
        assertEquals(20260216, expanded[1].first)
        assertEquals(20260217, expanded[2].first)
    }

    @Test
    fun `single day event generates one entry`() {
        val event = roomEvent(startDay = 20260215, endDay = 20260215, title = "Single")

        val expanded = if (event.startDay == event.endDay) {
            listOf(event.startDay to event)
        } else {
            generateDayCodesInRange(event.startDay, event.endDay)
                .map { dayCode -> dayCode to event }
        }

        assertEquals(1, expanded.size)
        assertEquals(20260215, expanded[0].first)
    }

    // ========== Multi-Day Expansion clamped to the query window ==========
    // A multi-day event occupies only the day buckets inside the requested window. Expanding
    // over its full span leaks buckets before the window start (#306: the upcoming widget's
    // first row showed the event's start date instead of today for an event that began earlier).

    @Test
    fun `multi-day event starting before the window clamps to the window start`() {
        // Event runs Feb 13-17; window is Feb 15-20 (e.g. today = Feb 15).
        val days = spannedDayCodesWithinWindow(
            startDay = 20260213, endDay = 20260217,
            windowStartDayCode = 20260215, windowEndDayCode = 20260220
        )
        // No bucket before the window start; the first bucket is the window start.
        assertEquals(listOf(20260215, 20260216, 20260217), days)
    }

    @Test
    fun `multi-day event ending after the window clamps to the window end`() {
        val days = spannedDayCodesWithinWindow(
            startDay = 20260218, endDay = 20260225,
            windowStartDayCode = 20260215, windowEndDayCode = 20260220
        )
        assertEquals(listOf(20260218, 20260219, 20260220), days)
    }

    @Test
    fun `multi-day event spanning beyond both edges yields exactly the window days`() {
        val days = spannedDayCodesWithinWindow(
            startDay = 20260210, endDay = 20260225,
            windowStartDayCode = 20260215, windowEndDayCode = 20260218
        )
        assertEquals(listOf(20260215, 20260216, 20260217, 20260218), days)
    }

    @Test
    fun `multi-day event fully inside the window yields all its days`() {
        val days = spannedDayCodesWithinWindow(
            startDay = 20260216, endDay = 20260218,
            windowStartDayCode = 20260215, windowEndDayCode = 20260220
        )
        assertEquals(listOf(20260216, 20260217, 20260218), days)
    }

    @Test
    fun `single-day event inside the window yields that day`() {
        val days = spannedDayCodesWithinWindow(
            startDay = 20260216, endDay = 20260216,
            windowStartDayCode = 20260215, windowEndDayCode = 20260220
        )
        assertEquals(listOf(20260216), days)
    }

    @Test
    fun `event entirely before the window yields no days`() {
        val days = spannedDayCodesWithinWindow(
            startDay = 20260210, endDay = 20260213,
            windowStartDayCode = 20260215, windowEndDayCode = 20260220
        )
        assertTrue(days.isEmpty())
    }

    @Test
    fun `event entirely after the window yields no days`() {
        val days = spannedDayCodesWithinWindow(
            startDay = 20260221, endDay = 20260223,
            windowStartDayCode = 20260215, windowEndDayCode = 20260220
        )
        assertTrue(days.isEmpty())
    }

    // ========== generateDayCodesInRange ==========

    @Test
    fun `generateDayCodesInRange same day`() {
        val result = generateDayCodesInRange(20260215, 20260215)
        assertEquals(listOf(20260215), result)
    }

    @Test
    fun `generateDayCodesInRange two days`() {
        val result = generateDayCodesInRange(20260215, 20260216)
        assertEquals(listOf(20260215, 20260216), result)
    }

    @Test
    fun `generateDayCodesInRange across month boundary`() {
        val result = generateDayCodesInRange(20260228, 20260302)
        assertEquals(listOf(20260228, 20260301, 20260302), result)
    }

    @Test
    fun `generateDayCodesInRange across year boundary`() {
        val result = generateDayCodesInRange(20251230, 20260102)
        assertEquals(listOf(20251230, 20251231, 20260101, 20260102), result)
    }

    // ========== generateDayCodesInRange validation ==========

    @Test
    fun `generateDayCodesInRange returns empty for zero startDayCode`() {
        val result = generateDayCodesInRange(0, 20260215)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateDayCodesInRange returns empty for zero endDayCode`() {
        val result = generateDayCodesInRange(20260215, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateDayCodesInRange returns empty for both zero`() {
        val result = generateDayCodesInRange(0, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateDayCodesInRange returns empty for reversed range`() {
        val result = generateDayCodesInRange(20260217, 20260215)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateDayCodesInRange returns empty for range exceeding 366 days`() {
        val result = generateDayCodesInRange(20260101, 20280101)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateDayCodesInRange valid range at 366-day cap works`() {
        // 366 days apart, at the cap: 367 entries.
        val result = generateDayCodesInRange(20260101, 20270102)
        assertEquals(367, result.size)
        assertEquals(20260101, result.first())
        assertEquals(20270102, result.last())
    }

    @Test
    fun `generateDayCodesInRange returns empty for negative dayCode`() {
        val result = generateDayCodesInRange(-1, 20260215)
        assertTrue(result.isEmpty())
    }

    // ========== FakeCalendarProviderRepository ==========

    @Test
    fun `fake repo returns configured instances`() {
        val fake = FakeCalendarProviderRepository()
        fake.instances = listOf(testDeviceInstance(calendarId = 1L))

        kotlinx.coroutines.runBlocking {
            val result = fake.getInstancesForDayRange(20260215, 20260215, setOf(1L))
            assertEquals(1, result.size)
        }
    }

    @Test
    fun `fake repo filters by enabledCalendarIds`() {
        val fake = FakeCalendarProviderRepository()
        fake.instances = listOf(
            testDeviceInstance(calendarId = 1L),
            testDeviceInstance(calendarId = 2L)
        )

        kotlinx.coroutines.runBlocking {
            val result = fake.getInstancesForDayRange(20260215, 20260215, setOf(1L))
            assertEquals(1, result.size)
            assertEquals(1L, result[0].calendarId)
        }
    }

    @Test
    fun `fake repo throws SecurityException when configured`() {
        val fake = FakeCalendarProviderRepository()
        fake.shouldThrowSecurityException = true

        try {
            kotlinx.coroutines.runBlocking {
                fake.getDeviceCalendars()
            }
            assertTrue("Should have thrown", false)
        } catch (e: SecurityException) {
            // expected
        }
    }

    // ========== FakeCalendarProviderRepository Search ==========

    @Test
    fun `fake repo searchInstances with empty query returns empty`() {
        val fake = FakeCalendarProviderRepository()
        fake.instances = listOf(
            testDeviceInstance(calendarId = 1L, title = "Meeting")
        )

        kotlinx.coroutines.runBlocking {
            val result = fake.searchInstances("", 20260215, 20260215, setOf(1L))
            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun `fake repo searchInstances matches title partially`() {
        val fake = FakeCalendarProviderRepository()
        fake.instances = listOf(
            testDeviceInstance(calendarId = 1L, title = "Team Meeting"),
            testDeviceInstance(calendarId = 1L, title = "Lunch Break")
        )

        kotlinx.coroutines.runBlocking {
            val result = fake.searchInstances("meet", 20260215, 20260215, setOf(1L))
            assertEquals(1, result.size)
            assertEquals("Team Meeting", result[0].title)
        }
    }

    @Test
    fun `fake repo searchInstances filters by enabledCalendarIds`() {
        val fake = FakeCalendarProviderRepository()
        fake.instances = listOf(
            testDeviceInstance(calendarId = 1L, title = "Meeting A"),
            testDeviceInstance(calendarId = 2L, title = "Meeting B")
        )

        kotlinx.coroutines.runBlocking {
            val result = fake.searchInstances("meeting", 20260215, 20260215, setOf(1L))
            assertEquals(1, result.size)
            assertEquals("Meeting A", result[0].title)
        }
    }

    @Test
    fun `fake repo searchInstances is case insensitive`() {
        val fake = FakeCalendarProviderRepository()
        fake.instances = listOf(
            testDeviceInstance(calendarId = 1L, title = "IMPORTANT Meeting")
        )

        kotlinx.coroutines.runBlocking {
            val result = fake.searchInstances("important", 20260215, 20260215, setOf(1L))
            assertEquals(1, result.size)
        }
    }

    @Test
    fun `fake repo searchInstances matches description`() {
        val fake = FakeCalendarProviderRepository()
        fake.instances = listOf(
            testDeviceInstance(calendarId = 1L, title = "Event", description = "Discuss budget")
        )

        kotlinx.coroutines.runBlocking {
            val result = fake.searchInstances("budget", 20260215, 20260215, setOf(1L))
            assertEquals(1, result.size)
        }
    }

    @Test
    fun `fake repo searchInstances matches location`() {
        val fake = FakeCalendarProviderRepository()
        fake.instances = listOf(
            testDeviceInstance(calendarId = 1L, title = "Event", location = "Conference Room B")
        )

        kotlinx.coroutines.runBlocking {
            val result = fake.searchInstances("conference", 20260215, 20260215, setOf(1L))
            assertEquals(1, result.size)
        }
    }

    // ========== Search Merge (Room + Device) ==========

    @Test
    fun `search merge sorts by displayTs`() {
        val roomResult = SearchResult(
            displayEvent = roomEvent(startTs = 2000L, title = "Room Result"),
            displayTs = 2000L
        )
        val deviceResult = SearchResult(
            displayEvent = deviceEvent(startTs = 1000L, title = "Device Result"),
            displayTs = 1000L
        )

        val merged = listOf(roomResult, deviceResult).sortedBy { it.displayTs }
        assertEquals("Device Result", merged[0].displayEvent.title)
        assertEquals("Room Result", merged[1].displayEvent.title)
    }

    @Test
    fun `search merge with Room recurring uses nextOccurrenceTs`() {
        // A recurring Room event with startTs=1000 whose next occurrence is at 5000.
        val roomResult = SearchResult(
            displayEvent = roomEvent(startTs = 1000L, title = "Recurring Room"),
            displayTs = 5000L // nextOccurrenceTs
        )
        val deviceResult = SearchResult(
            displayEvent = deviceEvent(startTs = 3000L, title = "Device Event"),
            displayTs = 3000L
        )

        val merged = listOf(roomResult, deviceResult).sortedBy { it.displayTs }
        assertEquals("Device Event", merged[0].displayEvent.title)
        assertEquals("Recurring Room", merged[1].displayEvent.title)
    }

    @Test
    fun `search merge with both empty returns empty`() {
        val merged = emptyList<SearchResult>().sortedBy { it.displayTs }
        assertTrue(merged.isEmpty())
    }

    // ========== Test Helpers ==========

    private fun roomEvent(
        startTs: Long = 1000L,
        startDay: Int = 20260215,
        endDay: Int = startDay,
        title: String = "Room Event"
    ): DisplayEvent.Room {
        // A timed event's days come from its instants: [startTs] is an offset on [startDay] in
        // UTC, as in [deviceEvent], so the two sort the same way.
        val start = utcMidnight(startDay) + startTs
        val end = if (endDay > startDay) utcMidnight(endDay) + startTs else start + 3600000L
        val event = Event(
            id = 1L,
            uid = "test-uid",
            calendarId = 1L,
            title = title,
            startTs = start,
            endTs = end,
            dtstamp = start
        )
        val occurrence = Occurrence(
            eventId = 1L,
            calendarId = 1L,
            startTs = start,
            endTs = end,
            startDay = startDay,
            endDay = endDay
        )
        val calendar = Calendar(
            id = 1L,
            accountId = 1L,
            caldavUrl = "https://example.com/cal/",
            displayName = "Test Calendar",
            color = 0xFF0000.toInt()
        )
        return DisplayEvent.Room(event, occurrence, calendar, zone = ZoneOffset.UTC)
    }

    private fun utcMidnight(dayCode: Int): Long =
        java.time.LocalDate.of(dayCode / 10000, dayCode / 100 % 100, dayCode % 100)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun deviceEvent(
        startTs: Long = 1000L,
        startDay: Int = 20260215,
        endDay: Int = startDay,
        title: String = "Device Event"
    ): DisplayEvent.Device {
        return DisplayEvent.Device(testDeviceInstance(
            startTs = utcMidnight(startDay) + startTs,
            startDay = startDay,
            endDay = endDay,
            title = title
        ))
    }

    private fun testDeviceInstance(
        calendarId: Long = 5L,
        startTs: Long = 1000L,
        startDay: Int = 20260215,
        endDay: Int = startDay,
        title: String = "Device Event",
        description: String = "",
        location: String = ""
    ) = DeviceCalendarInstance(
        instanceId = 0L,
        eventId = 0L,
        title = title,
        description = description,
        location = location,
        startTs = startTs,
        endTs = startTs + 3600000L,
        startDay = startDay,
        endDay = endDay,
        isAllDay = false,
        hasRrule = false,
        rrule = null,
        reminders = emptyList(),
        calendarId = calendarId,
        calendarDisplayName = "Device Cal",
        calendarColor = 0xFF00FF.toInt(),
        eventColor = null,
        status = 0,
        availability = 0,
        hasAlarm = false,
        selfAttendeeStatus = 0,
        isWritable = true,
        originalId = null,
        originalInstanceTime = null,
        timezone = "America/New_York",
        eventStartTs = startTs,
    )

    // ========== Tasks.org Timezone & Integration Tests (RF-01, AC-04) ==========

    @Test
    fun `DisplayEvent Task keeps all-day task on its scheduled date regardless of zone`() {
        val allDayTask = TasksOrgTask(
            id = 100L,
            title = "File Taxes",
            notes = "",
            dueTs = LocalDate.of(2026, 11, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            isAllDay = true,
            startTs = LocalDate.of(2026, 11, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            endTs = LocalDate.of(2026, 11, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 86400000L - 1,
            startDay = 20261104,
            endDay = 20261104,
            completedAt = null,
            recurrence = null,
            listId = 1L,
            listTitle = "Inbox",
            listColor = 0,
            isReadOnly = false
        )

        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val laZone = ZoneId.of("America/Los_Angeles")
        val spZone = ZoneId.of("America/Sao_Paulo")

        val tokyoEvent = DisplayEvent.Task(allDayTask, zone = tokyoZone)
        val laEvent = DisplayEvent.Task(allDayTask, zone = laZone)
        val spEvent = DisplayEvent.Task(allDayTask, zone = spZone)

        assertEquals(20261104, tokyoEvent.startDay)
        assertEquals(20261104, tokyoEvent.endDay)
        assertEquals(20261104, laEvent.startDay)
        assertEquals(20261104, laEvent.endDay)
        assertEquals(20261104, spEvent.startDay)
        assertEquals(20261104, spEvent.endDay)
    }

    @Test
    fun `DisplayEvent Task updates timed task startDay based on active zone`() {
        // Tue Nov 3 2026 12:00:00 PST is Wed Nov 4 2026 05:00:00 JST
        val laZone = ZoneId.of("America/Los_Angeles")
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val instant = LocalDate.of(2026, 11, 3).atTime(12, 0).atZone(laZone).toInstant().toEpochMilli()

        val timedTask = TasksOrgTask(
            id = 101L,
            title = "Doctor Appointment",
            notes = "",
            dueTs = instant,
            isAllDay = false,
            startTs = instant,
            endTs = instant,
            startDay = 20261103,
            endDay = 20261103,
            completedAt = null,
            recurrence = null,
            listId = 1L,
            listTitle = "Inbox",
            listColor = 0,
            isReadOnly = false
        )

        val laEvent = DisplayEvent.Task(timedTask, zone = laZone)
        val tokyoEvent = DisplayEvent.Task(timedTask, zone = tokyoZone)

        assertEquals(20261103, laEvent.startDay)
        assertEquals(20261104, tokyoEvent.startDay)
    }

    @Test
    fun `DisplayEventRepository groups tasks into the correct day when zone changes without off-by-one`() = runTest {
        val targetDay = 20261104
        val allDayTask = TasksOrgTask(
            id = 200L,
            title = "Buy Groceries",
            notes = "",
            dueTs = LocalDate.of(2026, 11, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            isAllDay = true,
            startTs = LocalDate.of(2026, 11, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            endTs = LocalDate.of(2026, 11, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 86400000L - 1,
            startDay = targetDay,
            endDay = targetDay,
            completedAt = null,
            recurrence = null,
            listId = 1L,
            listTitle = "Inbox",
            listColor = 0,
            isReadOnly = false
        )

        val eventReader: EventReader = mockk {
            every { getVisibleOccurrencesWithEventsInRangeFlow(any(), any()) } returns flowOf(emptyList())
        }
        val dataStore: KashCalDataStore = mockk {
            coEvery { getShowDeclinedEvents() } returns false
            coEvery { getDeviceCalendarsEnabled() } returns false
            coEvery { getTasksOrgEnabled() } returns true
            coEvery { getHiddenTasksOrgListIds() } returns emptySet()
            every { hiddenDeviceCalendarIds } returns flowOf(emptySet())
            every { showDeclinedEvents } returns flowOf(false)
        }
        val tasksRepository: TasksOrgRepository = mockk {
            coEvery { getTasksDueInRange(any(), any(), any(), any()) } returns listOf(allDayTask)
            coEvery { getRecurringTasksDueInRange(any(), any(), any()) } returns emptyList()
        }

        val zoneSource = ZoneSource()
        val repo = DisplayEventRepository(
            eventReader = eventReader,
            calendarProviderRepository = mockk(),
            calendarProviderManager = mockk { every { changeSignal } returns MutableStateFlow(0) },
            dataStore = dataStore,
            attendeesDao = mockk { every { attendeesChangeSignal() } returns flowOf(0) },
            accountsDao = mockk(),
            tasksOrgRepository = tasksRepository,
            tasksOrgManager = mockk { every { changeSignal } returns MutableStateFlow(0) },
            zoneSource = zoneSource
        )

        val savedZone = java.util.TimeZone.getDefault()
        try {
            // Read in default zone
            val result1 = repo.getDisplayEventsGroupedByDayOnce(targetDay, targetDay)
            assertTrue("Task should be present on targetDay", result1.containsKey(targetDay))
            assertEquals(listOf(200L), result1[targetDay]?.map { (it as DisplayEvent.Task).task.id })

            // Change zone to Tokyo and refresh
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
            zoneSource.refresh()

            val resultTokyo = repo.getDisplayEventsGroupedByDayOnce(targetDay, targetDay)
            assertTrue("Task should remain on targetDay in Tokyo", resultTokyo.containsKey(targetDay))
            assertEquals(listOf(200L), resultTokyo[targetDay]?.map { (it as DisplayEvent.Task).task.id })

            // Change zone to Los Angeles and refresh
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            zoneSource.refresh()

            val resultLA = repo.getDisplayEventsGroupedByDayOnce(targetDay, targetDay)
            assertTrue("Task should remain on targetDay in LA", resultLA.containsKey(targetDay))
            assertEquals(listOf(200L), resultLA[targetDay]?.map { (it as DisplayEvent.Task).task.id })
        } finally {
            java.util.TimeZone.setDefault(savedZone)
        }
    }
}
