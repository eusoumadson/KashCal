package org.onekash.kashcal.widget

import org.onekash.kashcal.ui.util.DayPagerUtils
import org.onekash.kashcal.util.DateTimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Items emitted by the Split Agenda grouping engine for LazyColumn rendering.
 */
sealed class SplitAgendaItem {
    open val itemId: Long get() = 0L

    data class DayHeader(
        val dayCode: Int,
        val label: String,
        val eventCount: Int = 0
    ) : SplitAgendaItem() {
        override val itemId: Long get() = dayCode.toLong()
    }

    data class EventRow(
        val dayCode: Int,
        val event: WidgetDataRepository.WidgetEvent
    ) : SplitAgendaItem() {
        override val itemId: Long get() = dayCode.toLong() * 100_000L + event.eventId + 100_000_000L
    }

    data class EmptyTodayNotice(
        val dayCode: Int,
        val message: String = "Nenhum evento hoje"
    ) : SplitAgendaItem() {
        override val itemId: Long get() = dayCode.toLong() * 100_000L + 999_999L
    }
}

/**
 * Pure day addition helper for YYYYMMDD dayCodes using [LocalDate.plusDays].
 */
internal fun addDaysToSplitDayCode(dayCode: Int, days: Long): Int {
    val date = DayPagerUtils.dayCodeToLocalDate(dayCode).plusDays(days)
    return date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
}

/**
 * Formats a localized date string in "Dia-da-semana, data" form (e.g. "Fri, May 1" or "sex., 1 de mai.").
 */
internal fun formatSplitDayDate(date: LocalDate, locale: Locale = Locale.getDefault()): String {
    val pattern = try {
        val p = DateTimeUtils.localizedPattern("EEEMMMd", locale)
        if (p.isNotBlank()) p else null
    } catch (_: Throwable) {
        null
    } ?: "EEE, MMM d"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

/**
 * Formats the relative day header label for Split Agenda widget sections.
 *
 * - Returns [todayLabel] ("Hoje") when [dayCode] == [todayDayCode].
 * - Returns [tomorrowLabel] ("Amanhã") when [dayCode] is tomorrow.
 * - Returns localized "Dia-da-semana, data" for subsequent days.
 */
internal fun formatSplitAgendaDayHeader(
    dayCode: Int,
    todayDayCode: Int,
    todayLabel: String = "Hoje",
    tomorrowLabel: String = "Amanhã",
    locale: Locale = Locale.getDefault()
): String {
    val tomorrowDayCode = addDaysToSplitDayCode(todayDayCode, 1)
    return when (dayCode) {
        todayDayCode -> todayLabel
        tomorrowDayCode -> tomorrowLabel
        else -> {
            val date = DayPagerUtils.dayCodeToLocalDate(dayCode)
            formatSplitDayDate(date, locale)
        }
    }
}

/**
 * Pure grouping engine for SplitAgendaWidget upcoming events and tasks list.
 *
 * Requirements:
 * - RF-04 / AC-04: Enforces a 3-day window (today, tomorrow, tomorrow+1).
 *   Filters out past events on today (`isPast == true`), keeping in-progress (`isPast == false`)
 *   and future events.
 * - RF-05 / AC-05: Mandatory "Hoje" section even when empty (emits [SplitAgendaItem.EmptyTodayNotice]).
 *   Future days without events are omitted.
 * - RF-06 / AC-06: Today loads all remaining non-past events (not capped by [maxEventsPerDay]).
 *   Future days (tomorrow and tomorrow+1) cap their events at [maxEventsPerDay].
 *   Preserves tasks (both pending and completed) wrapped in [SplitAgendaItem.EventRow].
 */
fun buildSplitUpcomingItems(
    eventsByDay: Map<Int, List<WidgetDataRepository.WidgetEvent>>,
    todayDayCode: Int,
    nowMs: Long = System.currentTimeMillis(),
    maxEventsPerDay: Int = 3,
    todayLabel: String = "Hoje",
    tomorrowLabel: String = "Amanhã",
    emptyNoticeMessage: String = "Nenhum evento hoje",
    locale: Locale = Locale.getDefault()
): List<SplitAgendaItem> {
    val items = mutableListOf<SplitAgendaItem>()

    val day0 = todayDayCode
    val day1 = addDaysToSplitDayCode(todayDayCode, 1)
    val day2 = addDaysToSplitDayCode(todayDayCode, 2)

    // 1. Mandatory "Hoje" section
    val rawTodayEvents = eventsByDay[day0].orEmpty()
    val todayKept = rawTodayEvents.filterNot { it.isPast }
    val todayHeaderLabel = formatSplitAgendaDayHeader(
        dayCode = day0,
        todayDayCode = todayDayCode,
        todayLabel = todayLabel,
        tomorrowLabel = tomorrowLabel,
        locale = locale
    )

    items.add(
        SplitAgendaItem.DayHeader(
            dayCode = day0,
            label = todayHeaderLabel,
            eventCount = todayKept.size
        )
    )

    if (todayKept.isEmpty()) {
        items.add(
            SplitAgendaItem.EmptyTodayNotice(
                dayCode = day0,
                message = emptyNoticeMessage
            )
        )
    } else {
        todayKept.forEach { event ->
            items.add(SplitAgendaItem.EventRow(dayCode = day0, event = event))
        }
    }

    // 2. Future days within 3-day window: Tomorrow (day1) and Day+2 (day2)
    val futureDays = listOf(day1, day2)
    for (dayCode in futureDays) {
        val rawFutureEvents = eventsByDay[dayCode].orEmpty()
        val futureKept = rawFutureEvents.filterNot { it.isPast }

        if (futureKept.isEmpty()) {
            // Future days without events are omitted completely
            continue
        }

        // Apply maxEventsPerDay cap only on future days
        val limitedEvents = if (maxEventsPerDay > 0) {
            futureKept.take(maxEventsPerDay)
        } else {
            futureKept
        }

        val headerLabel = formatSplitAgendaDayHeader(
            dayCode = dayCode,
            todayDayCode = todayDayCode,
            todayLabel = todayLabel,
            tomorrowLabel = tomorrowLabel,
            locale = locale
        )

        items.add(
            SplitAgendaItem.DayHeader(
                dayCode = dayCode,
                label = headerLabel,
                eventCount = limitedEvents.size
            )
        )

        limitedEvents.forEach { event ->
            items.add(SplitAgendaItem.EventRow(dayCode = dayCode, event = event))
        }
    }

    return items
}
