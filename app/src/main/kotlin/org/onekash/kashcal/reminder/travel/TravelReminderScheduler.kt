package org.onekash.kashcal.reminder.travel

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.onekash.kashcal.data.db.dao.EventsDao
import org.onekash.kashcal.data.db.dao.ScheduledRemindersDao
import org.onekash.kashcal.data.db.entity.Event
import org.onekash.kashcal.data.db.entity.Occurrence
import org.onekash.kashcal.data.db.entity.SyncStatus
import org.onekash.kashcal.di.IoDispatcher
import org.onekash.kashcal.reminder.scheduler.ReminderScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates WorkManager requests for travel time calculations.
 *
 * Enqueues a [TravelAlarmWorker] ~1 hour before an occurrence to dynamically calculate
 * the route, traffic conditions, and arm the departure alarm via [ReminderScheduler].
 */
@Singleton
class TravelReminderScheduler(
    @ApplicationContext private val context: Context,
    private val eventsDao: EventsDao,
    private val scheduledRemindersDao: ScheduledRemindersDao,
    private val reminderScheduler: ReminderScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val workManager: WorkManager
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        eventsDao: EventsDao,
        scheduledRemindersDao: ScheduledRemindersDao,
        reminderScheduler: ReminderScheduler,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(
        context,
        eventsDao,
        scheduledRemindersDao,
        reminderScheduler,
        ioDispatcher,
        WorkManager.getInstance(context)
    )

    companion object {
        private const val TAG = "TravelReminderScheduler"
        const val LEAD_TIME_MS = 60 * 60 * 1000L // 1 hour before occurrence
    }

    /**
     * Schedules travel calculation workers for all [occurrences] of [event].
     * If the event has no travel alert enabled or is deleted, cancels all its travel reminders.
     */
    suspend fun scheduleForEvent(event: Event, occurrences: List<Occurrence>) = withContext(ioDispatcher) {
        if (event.travelMode.isNullOrBlank() || event.syncStatus == SyncStatus.PENDING_DELETE || event.status == "CANCELLED") {
            cancelForEvent(event.id)
            return@withContext
        }

        for (occ in occurrences) {
            scheduleForOccurrence(event, occ.startTs)
        }
    }

    /**
     * Enqueues a unique [TravelAlarmWorker] for a specific occurrence of [event].
     */
    fun scheduleForOccurrence(event: Event, occurrenceStartTs: Long) {
        val now = System.currentTimeMillis()
        val runAtTs = occurrenceStartTs - LEAD_TIME_MS
        val delayMs = if (runAtTs > now) runAtTs - now else 0L

        val workName = "travel_alarm_${event.id}_${occurrenceStartTs}"
        val workTag = "travel_event_${event.id}"

        val request = OneTimeWorkRequestBuilder<TravelAlarmWorker>()
            .setInputData(
                workDataOf(
                    TravelAlarmWorker.KEY_EVENT_ID to event.id,
                    TravelAlarmWorker.KEY_OCCURRENCE_START_TS to occurrenceStartTs
                )
            )
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(workTag)
            .build()

        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
        Log.d(TAG, "Enqueued travel worker for event ${event.id} at occ $occurrenceStartTs (delay: ${delayMs / 1000}s)")
    }

    /**
     * Cancels any pending travel workers, exact alarms, and database rows for [eventId].
     */
    suspend fun cancelForEvent(eventId: Long) = withContext(ioDispatcher) {
        try {
            workManager.cancelAllWorkByTag("travel_event_${eventId}")

            val pending = scheduledRemindersDao.getPendingTravelRemindersForEvent(eventId)
            for (reminder in pending) {
                reminderScheduler.cancelAlarm(reminder.id)
            }
            scheduledRemindersDao.deleteTravelRemindersForEvent(eventId)
            Log.d(TAG, "Cancelled travel alerts and alarms for event $eventId")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel travel alerts for event $eventId", e)
        }
    }

    /**
     * Scans for events with travel alerts due in the next [windowDays] days and schedules workers.
     * Called during daily background refresh by [ReminderRefreshWorker].
     */
    suspend fun scheduleUpcomingTravelAlarms(windowDays: Int = 7) = withContext(ioDispatcher) {
        try {
            val now = System.currentTimeMillis()
            val windowEnd = now + (windowDays.toLong() * 24 * 60 * 60 * 1000L)

            val upcoming = eventsDao.getEventsWithTravelAlertsInRange(now, windowEnd)
            Log.d(TAG, "Found ${upcoming.size} upcoming occurrences with travel alerts in next $windowDays days")

            for (item in upcoming) {
                scheduleForOccurrence(item.event, item.occurrenceStartTs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling upcoming travel alarms", e)
        }
    }
}
