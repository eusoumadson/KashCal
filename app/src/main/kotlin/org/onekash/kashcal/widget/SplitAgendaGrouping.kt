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

    data class AllDayEventStack(
        val dayCode: Int,
        val count: Int
    ) : SplitAgendaItem() {
        override val itemId: Long get() = dayCode.toLong() * 100_000L + 999_997L
    }

    data class AllDayTaskStack(
        val dayCode: Int,
        val count: Int
    ) : SplitAgendaItem() {
        override val itemId: Long get() = dayCode.toLong() * 100_000L + 999_998L
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
 * - RF-04 / AC-03: Dynamic 2-day window (Today + Tomorrow if today has future/in-progress events;
 *   advances automatically to Tomorrow + Day+2 when today has no remaining events).
 *   Filters out past events on today (`isPast == true`), keeping in-progress (`isPast == false`)
 *   and future events.
 * - RF-05: Today is omitted completely when it has no non-past events.
 *   Future days without events are omitted.
 * - RF-06: Today loads all remaining non-past events (not capped by [maxEventsPerDay]).
 *   Future days (tomorrow and subsequent days) cap their events at [maxEventsPerDay].
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

    val rawTodayEvents = eventsByDay[day0].orEmpty()
    val todayKept = rawTodayEvents.filterNot { it.isPast }

    // Dynamic 2-day window:
    // If today has non-past events: [Today, Tomorrow]
    // If today has NO non-past events: [Tomorrow, Day+2]
    val targetDays = if (todayKept.isNotEmpty()) {
        listOf(day0, day1)
    } else {
        listOf(day1, day2)
    }

    for (dayCode in targetDays) {
        val isToday = dayCode == day0
        val dayKept = if (isToday) {
            todayKept
        } else {
            eventsByDay[dayCode].orEmpty().filterNot { it.isPast }
        }

        if (dayKept.isEmpty()) {
            continue
        }

        val allDayEvents = dayKept.filter { it.isAllDay && !it.isTask }
        val allDayTasks = dayKept.filter { it.isAllDay && it.isTask }
        val timedItems = dayKept.filterNot { it.isAllDay }

        val limitedTimedItems = if (isToday || maxEventsPerDay <= 0) {
            timedItems
        } else {
            timedItems.take(maxEventsPerDay)
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
                eventCount = allDayEvents.size + allDayTasks.size + limitedTimedItems.size
            )
        )

        // 1. All-day calendar events (1st item + stack pill if > 1)
        if (allDayEvents.isNotEmpty()) {
            items.add(SplitAgendaItem.EventRow(dayCode = dayCode, event = allDayEvents.first()))
            if (allDayEvents.size > 1) {
                items.add(
                    SplitAgendaItem.AllDayEventStack(
                        dayCode = dayCode,
                        count = allDayEvents.size - 1
                    )
                )
            }
        }

        // 2. All-day tasks (1st item + stack pill if > 1)
        if (allDayTasks.isNotEmpty()) {
            items.add(SplitAgendaItem.EventRow(dayCode = dayCode, event = allDayTasks.first()))
            if (allDayTasks.size > 1) {
                items.add(
                    SplitAgendaItem.AllDayTaskStack(
                        dayCode = dayCode,
                        count = allDayTasks.size - 1
                    )
                )
            }
        }

        // 3. Timed events and tasks rendered below stacks
        limitedTimedItems.forEach { event ->
            items.add(SplitAgendaItem.EventRow(dayCode = dayCode, event = event))
        }
    }

    return items
}
