package org.onekash.kashcal.reminder.travel

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.onekash.kashcal.data.db.dao.EventWithOccurrenceAndColor
import org.onekash.kashcal.data.db.dao.EventsDao
import org.onekash.kashcal.data.db.dao.ScheduledRemindersDao
import org.onekash.kashcal.data.db.entity.Event
import org.onekash.kashcal.data.db.entity.Occurrence
import org.onekash.kashcal.data.db.entity.ReminderType
import org.onekash.kashcal.data.db.entity.ScheduledReminder
import org.onekash.kashcal.reminder.scheduler.ReminderScheduler

@OptIn(ExperimentalCoroutinesApi::class)
class TravelReminderSchedulerTest {

    private val context: Context = mockk(relaxed = true)
    private val eventsDao: EventsDao = mockk(relaxed = true)
    private val scheduledRemindersDao: ScheduledRemindersDao = mockk(relaxed = true)
    private val reminderScheduler: ReminderScheduler = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var scheduler: TravelReminderScheduler

    @Before
    fun setUp() {
        scheduler = TravelReminderScheduler(
            context = context,
            eventsDao = eventsDao,
            scheduledRemindersDao = scheduledRemindersDao,
            reminderScheduler = reminderScheduler,
            ioDispatcher = testDispatcher,
            workManager = workManager
        )
    }

    private fun sampleEvent(id: Long = 1L, travelMode: String? = "CAR") = Event(
        id = id,
        calendarId = 10L,
        uid = "uid-$id",
        title = "Meeting",
        location = "Av. Paulista",
        startTs = 10_000_000L,
        endTs = 11_000_000L,
        dtstamp = 10_000_000L,
        isAllDay = false,
        travelMode = travelMode,
        travelDepartBufferMin = 10
    )

    private fun sampleOccurrence(eventId: Long = 1L, startTs: Long = 10_000_000L) = Occurrence(
        id = 100L,
        eventId = eventId,
        exceptionEventId = null,
        calendarId = 10L,
        startTs = startTs,
        endTs = startTs + 3_600_000L,
        startDay = 20261031,
        endDay = 20261031,
        isCancelled = false
    )

    @Test
    fun scheduleForEvent_enqueuesWorkWhenTravelModePresent() = runTest(testDispatcher) {
        val event = sampleEvent(1L, "CAR")
        val occurrences = listOf(sampleOccurrence(1L, 10_000_000L))

        scheduler.scheduleForEvent(event, occurrences)

        verify {
            workManager.enqueueUniqueWork(
                "travel_alarm_1_10000000",
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun scheduleForEvent_cancelsWhenTravelModeNull() = runTest(testDispatcher) {
        val event = sampleEvent(1L, travelMode = null)
        val occurrences = listOf(sampleOccurrence(1L, 10_000_000L))

        scheduler.scheduleForEvent(event, occurrences)

        verify { workManager.cancelAllWorkByTag("travel_event_1") }
        coVerify { scheduledRemindersDao.deleteTravelRemindersForEvent(1L) }
    }

    @Test
    fun cancelForEvent_cancelsAlarmsAndDeletesRows() = runTest(testDispatcher) {
        val pendingReminder = ScheduledReminder(
            id = 42L,
            eventId = 1L,
            occurrenceTime = 10_000_000L,
            reminderOffset = "TRAVEL_DEPART",
            triggerTime = 9_500_000L,
            eventTitle = "Meeting",
            eventLocation = "Av. Paulista",
            calendarColor = 0xFF00FF,
            isAllDay = false,
            reminderType = ReminderType.TRAVEL_DEPART
        )
        coEvery { scheduledRemindersDao.getPendingTravelRemindersForEvent(1L) } returns listOf(pendingReminder)

        scheduler.cancelForEvent(1L)

        verify { workManager.cancelAllWorkByTag("travel_event_1") }
        verify { reminderScheduler.cancelAlarm(42L) }
        coVerify { scheduledRemindersDao.deleteTravelRemindersForEvent(1L) }
    }

    @Test
    fun scheduleUpcomingTravelAlarms_enqueuesWorkersForUpcomingEvents() = runTest(testDispatcher) {
        val event = sampleEvent(5L, "CAR")
        val item = EventWithOccurrenceAndColor(
            event = event,
            occurrenceStartTs = 20_000_000L,
            occurrenceEndTs = 21_000_000L,
            calendarColor = 0x00FF00,
            targetEventId = 5L
        )
        coEvery { eventsDao.getEventsWithTravelAlertsInRange(any(), any()) } returns listOf(item)

        scheduler.scheduleUpcomingTravelAlarms(7)

        verify {
            workManager.enqueueUniqueWork(
                "travel_alarm_5_20000000",
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
    }
}
