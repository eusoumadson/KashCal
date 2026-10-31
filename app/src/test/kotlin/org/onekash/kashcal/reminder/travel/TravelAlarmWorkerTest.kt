package org.onekash.kashcal.reminder.travel

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onekash.kashcal.data.db.dao.CalendarsDao
import org.onekash.kashcal.data.db.dao.EventsDao
import org.onekash.kashcal.data.db.dao.ScheduledRemindersDao
import org.onekash.kashcal.data.db.entity.Calendar
import org.onekash.kashcal.data.db.entity.Event
import org.onekash.kashcal.data.db.entity.ReminderType
import org.onekash.kashcal.data.db.entity.ScheduledReminder
import org.onekash.kashcal.data.travel.RoutingResult
import org.onekash.kashcal.data.travel.RoutingService
import org.onekash.kashcal.data.travel.RoutingSource
import org.onekash.kashcal.reminder.scheduler.ReminderScheduler
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TravelAlarmWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val eventsDao: EventsDao = mockk(relaxed = true)
    private val calendarsDao: CalendarsDao = mockk(relaxed = true)
    private val scheduledRemindersDao: ScheduledRemindersDao = mockk(relaxed = true)
    private val reminderScheduler: ReminderScheduler = mockk(relaxed = true)
    private val travelLocationProvider: TravelLocationProvider = mockk(relaxed = true)
    private val routingService: RoutingService = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private fun createWorker(eventId: Long = 1L, occurrenceStartTs: Long = 100_000_000L): TravelAlarmWorker {
        every { workerParams.inputData } returns workDataOf(
            TravelAlarmWorker.KEY_EVENT_ID to eventId,
            TravelAlarmWorker.KEY_OCCURRENCE_START_TS to occurrenceStartTs
        )
        return TravelAlarmWorker(
            context = context,
            params = workerParams,
            eventsDao = eventsDao,
            calendarsDao = calendarsDao,
            scheduledRemindersDao = scheduledRemindersDao,
            reminderScheduler = reminderScheduler,
            travelLocationProvider = travelLocationProvider,
            routingService = routingService,
            ioDispatcher = testDispatcher
        )
    }

    private fun sampleEvent(
        id: Long = 1L,
        travelMode: String? = "CAR",
        bufferMin: Int? = 10,
        geoLat: Double? = -23.5615,
        geoLon: Double? = -46.6560
    ) = Event(
        id = id,
        calendarId = 10L,
        uid = "uid-$id",
        title = "Important Meeting",
        location = "Avenida Paulista",
        startTs = 100_000_000L,
        endTs = 103_600_000L,
        dtstamp = 100_000_000L,
        isAllDay = false,
        geoLat = geoLat,
        geoLon = geoLon,
        travelMode = travelMode,
        travelDepartBufferMin = bufferMin
    )

    private fun sampleCalendar(id: Long = 10L) = Calendar(
        id = id,
        accountId = 1L,
        caldavUrl = "https://cal.example.com",
        displayName = "Personal",
        color = 0xFF00FF,
        isNotificationMuted = false
    )

    @Test
    fun doWork_returnsFailureWhenParamsMissing() = runTest(testDispatcher) {
        every { workerParams.inputData } returns workDataOf()
        val worker = TravelAlarmWorker(
            context, workerParams, eventsDao, calendarsDao, scheduledRemindersDao,
            reminderScheduler, travelLocationProvider, routingService, testDispatcher
        )
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun doWork_returnsSuccessWhenEventNotFound() = runTest(testDispatcher) {
        coEvery { eventsDao.getById(1L) } returns null
        val worker = createWorker(1L)
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun doWork_returnsSuccessWhenTravelModeNull() = runTest(testDispatcher) {
        coEvery { eventsDao.getById(1L) } returns sampleEvent(1L, travelMode = null)
        val worker = createWorker(1L)
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun doWork_calculatesRouteAndSchedulesDepartureAndBufferReminders() = runTest(testDispatcher) {
        val eventTime = System.currentTimeMillis() + 7_200_000L // 2 hours in future
        val event = sampleEvent(1L, travelMode = "CAR", bufferMin = 10)
        val calendar = sampleCalendar(10L)

        coEvery { eventsDao.getById(1L) } returns event
        coEvery { calendarsDao.getById(10L) } returns calendar
        coEvery { travelLocationProvider.getCurrentLocation() } returns Pair(-23.5505, -46.6333)
        coEvery {
            routingService.estimate(
                originLat = -23.5505,
                originLon = -46.6333,
                destLat = -23.5615,
                destLon = -46.6560,
                mode = any(),
                arrivalEpochMs = any()
            )
        } returns RoutingResult(
            minutes = 25,
            trafficDelayMinutes = 5,
            source = RoutingSource.TOMTOM,
            hasLiveTraffic = true,
            isRushHourAdjusted = false
        )

        coEvery { scheduledRemindersDao.findExisting(any(), any(), any()) } returns null
        coEvery { scheduledRemindersDao.insert(any()) } returns 501L
        every { reminderScheduler.scheduleAlarm(any(), any()) } returns true

        val worker = createWorker(1L, eventTime)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)

        val remindersSlot = mutableListOf<ScheduledReminder>()
        coVerify(exactly = 2) { scheduledRemindersDao.insert(capture(remindersSlot)) }

        val bufferReminder = remindersSlot.firstOrNull { it.reminderType == ReminderType.TRAVEL_BUFFER }
        val departReminder = remindersSlot.firstOrNull { it.reminderType == ReminderType.TRAVEL_DEPART }

        assertEquals(25, departReminder?.estimatedTravelMin)
        assertEquals(25, bufferReminder?.estimatedTravelMin)
        assertTrue(departReminder?.travelNote?.contains("TomTom") == true)
        assertTrue(departReminder?.travelNote?.contains("trafficDelay=5") == true)

        verify(exactly = 2) { reminderScheduler.scheduleAlarm(any(), any()) }
    }
}
