package org.onekash.kashcal.reminder.travel

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.onekash.kashcal.data.db.dao.CalendarsDao
import org.onekash.kashcal.data.db.dao.EventsDao
import org.onekash.kashcal.data.db.dao.ScheduledRemindersDao
import org.onekash.kashcal.data.db.entity.ReminderStatus
import org.onekash.kashcal.data.db.entity.ReminderType
import org.onekash.kashcal.data.db.entity.ScheduledReminder
import org.onekash.kashcal.data.db.entity.SyncStatus
import org.onekash.kashcal.data.travel.RoutingService
import org.onekash.kashcal.data.travel.TravelMode
import org.onekash.kashcal.di.IoDispatcher
import org.onekash.kashcal.reminder.scheduler.ReminderScheduler

/**
 * WorkManager worker executed ~1 hour before an event (or immediately if close)
 * to query real-time device location, compute travel time using [RoutingService],
 * persist [ScheduledReminder] rows with [ReminderType.TRAVEL_DEPART] and
 * [ReminderType.TRAVEL_BUFFER], and arm exact alarms via [ReminderScheduler].
 */
@HiltWorker
class TravelAlarmWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val eventsDao: EventsDao,
    private val calendarsDao: CalendarsDao,
    private val scheduledRemindersDao: ScheduledRemindersDao,
    private val reminderScheduler: ReminderScheduler,
    private val travelLocationProvider: TravelLocationProvider,
    private val routingService: RoutingService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TravelAlarmWorker"
        const val KEY_EVENT_ID = "event_id"
        const val KEY_OCCURRENCE_START_TS = "occurrence_start_ts"
        const val OFFSET_DEPART = "TRAVEL_DEPART"
        const val OFFSET_BUFFER = "TRAVEL_BUFFER"

        fun formatTravelNote(
            providerName: String,
            trafficDelayMin: Int,
            isRushHourAdjusted: Boolean,
            hasLiveTraffic: Boolean,
            isEstimate: Boolean,
            modeName: String
        ): String {
            return "provider=$providerName;trafficDelay=$trafficDelayMin;rushHour=$isRushHourAdjusted;live=$hasLiveTraffic;estimate=$isEstimate;mode=$modeName"
        }
    }

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val eventId = inputData.getLong(KEY_EVENT_ID, -1L)
        val occurrenceStartTs = inputData.getLong(KEY_OCCURRENCE_START_TS, -1L)

        if (eventId == -1L || occurrenceStartTs == -1L) {
            Log.e(TAG, "Invalid work parameters: eventId=$eventId, occurrenceStartTs=$occurrenceStartTs")
            return@withContext Result.failure()
        }

        try {
            val event = eventsDao.getById(eventId)
            if (event == null || event.syncStatus == SyncStatus.PENDING_DELETE || event.status == "CANCELLED") {
                Log.d(TAG, "Event $eventId not found or deleted, skipping travel alert")
                return@withContext Result.success()
            }

            if (event.travelMode.isNullOrBlank()) {
                Log.d(TAG, "Event $eventId has travel alerts disabled")
                return@withContext Result.success()
            }

            val calendar = calendarsDao.getById(event.calendarId)
            if (calendar == null || calendar.isNotificationMuted) {
                Log.d(TAG, "Calendar for event $eventId is missing or muted")
                return@withContext Result.success()
            }

            // 1. Resolve destination coordinates
            val destCoords: Pair<Double, Double>? = if (event.geoLat != null && event.geoLon != null) {
                Pair(event.geoLat, event.geoLon)
            } else if (!event.location.isNullOrBlank()) {
                travelLocationProvider.resolveCoordinates(event.location)
            } else null

            if (destCoords == null) {
                Log.w(TAG, "Could not resolve destination coordinates for event $eventId ('${event.location}')")
                return@withContext Result.success()
            }

            // 2. Resolve origin coordinates
            val originCoords: Pair<Double, Double>? = if (!event.travelOriginFixed.isNullOrBlank()) {
                travelLocationProvider.resolveCoordinates(event.travelOriginFixed)
            } else {
                travelLocationProvider.getCurrentLocation()
            }

            if (originCoords == null) {
                Log.w(TAG, "Could not resolve origin coordinates for event $eventId")
                return@withContext Result.success()
            }

            // 3. Compute route estimate
            val mode = TravelMode.fromName(event.travelMode) ?: TravelMode.CAR
            val routingResult = routingService.estimate(
                originLat = originCoords.first,
                originLon = originCoords.second,
                destLat = destCoords.first,
                destLon = destCoords.second,
                mode = mode,
                arrivalEpochMs = occurrenceStartTs
            )

            val travelMin = routingResult.minutes
            val trafficDelayMin = routingResult.trafficDelayMinutes
            val travelNote = formatTravelNote(
                providerName = routingResult.source.displayName,
                trafficDelayMin = trafficDelayMin,
                isRushHourAdjusted = routingResult.isRushHourAdjusted,
                hasLiveTraffic = routingResult.hasLiveTraffic,
                isEstimate = routingResult.isEstimate,
                modeName = mode.name
            )

            val now = System.currentTimeMillis()
            val travelMs = travelMin * 60_000L
            val targetDepartTs = occurrenceStartTs - travelMs
            val departTriggerTs = if (targetDepartTs < now) now else targetDepartTs

            // 4. Schedule Buffer reminder if configured
            val bufferMin = event.travelDepartBufferMin ?: 0
            if (bufferMin > 0) {
                val bufferTriggerTs = departTriggerTs - (bufferMin * 60_000L)
                if (bufferTriggerTs > now) {
                    val existingBuffer = scheduledRemindersDao.findExisting(eventId, occurrenceStartTs, OFFSET_BUFFER)
                    val bufferReminder = ScheduledReminder(
                        id = existingBuffer?.id ?: 0L,
                        eventId = eventId,
                        occurrenceTime = occurrenceStartTs,
                        reminderOffset = OFFSET_BUFFER,
                        triggerTime = bufferTriggerTs,
                        eventTitle = event.title,
                        eventLocation = event.location,
                        calendarColor = calendar.color,
                        isAllDay = false,
                        status = ReminderStatus.PENDING,
                        reminderType = ReminderType.TRAVEL_BUFFER,
                        estimatedTravelMin = travelMin,
                        travelNote = travelNote
                    )
                    val insertedId = scheduledRemindersDao.insert(bufferReminder)
                    val armId = if (bufferReminder.id != 0L) bufferReminder.id else insertedId
                    reminderScheduler.scheduleAlarm(armId, bufferTriggerTs)
                    Log.i(TAG, "Scheduled travel buffer reminder ($bufferMin min) for event $eventId at $bufferTriggerTs")
                }
            }

            // 5. Schedule Departure reminder
            val existingDepart = scheduledRemindersDao.findExisting(eventId, occurrenceStartTs, OFFSET_DEPART)
            val departReminder = ScheduledReminder(
                id = existingDepart?.id ?: 0L,
                eventId = eventId,
                occurrenceTime = occurrenceStartTs,
                reminderOffset = OFFSET_DEPART,
                triggerTime = departTriggerTs,
                eventTitle = event.title,
                eventLocation = event.location,
                calendarColor = calendar.color,
                isAllDay = false,
                status = ReminderStatus.PENDING,
                reminderType = ReminderType.TRAVEL_DEPART,
                estimatedTravelMin = travelMin,
                travelNote = travelNote
            )
            val insertedId = scheduledRemindersDao.insert(departReminder)
            val armId = if (departReminder.id != 0L) departReminder.id else insertedId
            reminderScheduler.scheduleAlarm(armId, departTriggerTs)
            Log.i(TAG, "Scheduled travel depart reminder for event $eventId at $departTriggerTs (travel: $travelMin min via ${routingResult.source.displayName})")

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating travel alert for event $eventId", e)
            Result.retry()
        }
    }
}
