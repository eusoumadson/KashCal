package org.onekash.kashcal.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.onekash.kashcal.data.contacts.ContactEventTitleFormatter
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.domain.model.DisplayEvent
import org.onekash.kashcal.domain.reader.DisplayEventRepository
import org.onekash.kashcal.util.DateTimeUtils
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads events for the Agenda, Week, Month and Upcoming widgets from visible calendars, Room and
 * device calendars merged by [DisplayEventRepository].
 *
 * Every method sorts a day's events all-day first, then by start time, and marks past events
 * ([WidgetEvent.isPast]): Agenda and Week rows render them grayed and struck through, and the
 * Upcoming widget hides them.
 */
@Singleton
class WidgetDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayEventRepository: DisplayEventRepository,
    private val dataStore: KashCalDataStore
) {
    /** One event occurrence as a widget renders it. */
    data class WidgetEvent(
        val eventId: Long,
        val occurrenceStartTs: Long,
        val title: String,
        val startTs: Long,
        val endTs: Long,
        val isAllDay: Boolean,
        val calendarColor: Int,
        val isPast: Boolean,
        val isDeviceEvent: Boolean,
        val startDay: Int,
        val isCancelled: Boolean = false,
        val isFree: Boolean = false,
        val endDay: Int = startDay,
        // FORK:tasks-widget
        val isTask: Boolean = false,
        val isCompleted: Boolean = false,
        val taskId: Long? = null,
        val isFutureOccurrence: Boolean = false,
        val isReadOnly: Boolean = false
    ) {
        /** Multi-day events span more than one day cell and render as continuous bars. */
        val isMultiDay: Boolean get() = startDay != endDay

        /** Stable identity across the day buckets the event appears in (for span dedup). */
        val spanKey: String get() = "$eventId:$occurrenceStartTs"
    }

    /** Returns today's events, for the Agenda widget. */
    suspend fun getTodayEvents(): List<WidgetEvent> {
        val now = System.currentTimeMillis()
        val todayCode = DateTimeUtils.eventTsToDayCode(now, isAllDay = false)
        val tasksInWidgets = dataStore.getTasksOrgEnabled() && dataStore.getTasksInWidgetsEnabled()

        val eventsMap = displayEventRepository.getDisplayEventsGroupedByDayOnce(todayCode, todayCode)
        val todayEvents = eventsMap[todayCode] ?: return emptyList()

        return todayEvents.mapNotNull { toWidgetEvent(it, tasksInWidgets) }
            .sortedWith(compareBy({ !it.isAllDay }, { it.startTs }))
    }

    /**
     * Returns the events of the 7 days from today, for the Week widget, keyed by dayCode
     * (YYYYMMDD). Always exactly 7 entries, empty days included. A multi-day event appears on
     * each day it spans within the window.
     */
    suspend fun getWeekEvents(): Map<Int, List<WidgetEvent>> {
        // today, tomorrow, ..., today + 6
        val dayCodes = (0..6).map { offset ->
            val date = LocalDate.now().plusDays(offset.toLong())
            date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        }

        val startDayCode = dayCodes.first()
        val endDayCode = dayCodes.last()
        val tasksInWidgets = dataStore.getTasksOrgEnabled() && dataStore.getTasksInWidgetsEnabled()

        val eventsMap = displayEventRepository.getDisplayEventsGroupedByDayOnce(startDayCode, endDayCode)

        // Every day gets an entry, even with no events.
        return dayCodes.associateWith { dayCode ->
            eventsMap[dayCode].orEmpty()
                .mapNotNull { toWidgetEvent(it, tasksInWidgets) }
                .sortedWith(compareBy({ !it.isAllDay }, { it.startTs }))
        }
    }

    /**
     * Returns the events from [startDayCode] to [endDayCode] (YYYYMMDD, inclusive), keyed by
     * dayCode, for the Month widget's grid range and the Upcoming widget's horizon. Only days
     * with events have an entry.
     */
    suspend fun getEventsInRange(startDayCode: Int, endDayCode: Int): Map<Int, List<WidgetEvent>> {
        val tasksInWidgets = dataStore.getTasksOrgEnabled() && dataStore.getTasksInWidgetsEnabled()
        val eventsMap = displayEventRepository.getDisplayEventsGroupedByDayOnce(startDayCode, endDayCode)

        return eventsMap.mapValues { (_, displayEvents) ->
            displayEvents
                .mapNotNull { toWidgetEvent(it, tasksInWidgets) }
                .sortedWith(compareBy({ !it.isAllDay }, { it.startTs }))
        }
            .filterValues { it.isNotEmpty() }
    }

    /**
     * Maps a [DisplayEvent] to a [WidgetEvent]. A set event color wins over the calendar color;
     * when the winner is 0, [DEFAULT_CALENDAR_COLOR] is used.
     *
     * Tasks are included when [tasksInWidgets] is true (FORK:tasks-widget).
     */
    private fun toWidgetEvent(displayEvent: DisplayEvent, tasksInWidgets: Boolean = false): WidgetEvent? {
        return when (displayEvent) {
            is DisplayEvent.Room -> WidgetEvent(
                eventId = displayEvent.event.id,
                occurrenceStartTs = displayEvent.startTs,
                title = ContactEventTitleFormatter.format(
                    displayEvent.event, displayEvent.startTs, context.resources
                ),
                startTs = displayEvent.startTs,
                endTs = displayEvent.endTs,
                isAllDay = displayEvent.isAllDay,
                calendarColor = (displayEvent.eventColor ?: displayEvent.calendarColor).takeIf { it != 0 } ?: DEFAULT_CALENDAR_COLOR,
                isPast = DateTimeUtils.isEventPast(displayEvent.endTs, displayEvent.endDay, displayEvent.isAllDay),
                isDeviceEvent = false,
                startDay = displayEvent.startDay,
                isCancelled = displayEvent.isCancelled,
                isFree = displayEvent.isFree,
                endDay = displayEvent.endDay,
                isTask = false
            )
            is DisplayEvent.Device -> WidgetEvent(
                eventId = displayEvent.instance.eventId,
                occurrenceStartTs = displayEvent.startTs,
                title = displayEvent.title,
                startTs = displayEvent.startTs,
                endTs = displayEvent.endTs,
                isAllDay = displayEvent.isAllDay,
                calendarColor = (displayEvent.eventColor ?: displayEvent.calendarColor).takeIf { it != 0 } ?: DEFAULT_CALENDAR_COLOR,
                isPast = DateTimeUtils.isEventPast(displayEvent.endTs, displayEvent.endDay, displayEvent.isAllDay),
                isDeviceEvent = true,
                startDay = displayEvent.startDay,
                isCancelled = displayEvent.isCancelled,
                isFree = displayEvent.isFree,
                endDay = displayEvent.endDay,
                isTask = false
            )
            is DisplayEvent.Task -> {
                if (!tasksInWidgets) return null
                WidgetEvent(
                    eventId = displayEvent.task.id,
                    occurrenceStartTs = displayEvent.startTs,
                    title = displayEvent.task.title,
                    startTs = displayEvent.startTs,
                    endTs = displayEvent.endTs,
                    isAllDay = displayEvent.isAllDay,
                    calendarColor = (displayEvent.eventColor ?: displayEvent.calendarColor).takeIf { it != 0 } ?: DEFAULT_CALENDAR_COLOR,
                    isPast = DateTimeUtils.isEventPast(displayEvent.endTs, displayEvent.endDay, displayEvent.isAllDay),
                    isDeviceEvent = false,
                    startDay = displayEvent.startDay,
                    isCancelled = false,
                    isFree = false,
                    endDay = displayEvent.endDay,
                    // FORK:tasks-widget
                    isTask = true,
                    isCompleted = displayEvent.task.isCompleted,
                    taskId = displayEvent.task.id,
                    isFutureOccurrence = displayEvent.task.isFutureOccurrence,
                    isReadOnly = displayEvent.task.isReadOnly
                )
            }
        }
    }

    companion object {
        /** Material Blue 500. */
        private const val DEFAULT_CALENDAR_COLOR = 0xFF2196F3.toInt()
    }
}
