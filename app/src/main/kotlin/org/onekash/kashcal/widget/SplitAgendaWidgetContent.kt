package org.onekash.kashcal.widget

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.onekash.kashcal.MainActivity
import org.onekash.kashcal.R
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.ui.model.MonthGrid
import org.onekash.kashcal.ui.util.DayPagerUtils
import org.onekash.kashcal.util.DateTimeUtils
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Split Agenda Widget Content (FORK:experimental-widgets)
 *
 * When sized wide (e.g. 4x3, full widget width >= 260dp):
 * - Left column: large weekday and day number ("SÁBADO 31"), month, and first part of events/tasks.
 * - Right column: header with refresh/add buttons, and scrollable continuation of events/tasks.
 *
 * When compact (< 260dp):
 * - Single column with header and scrollable list.
 */
@Composable
internal fun SplitAgendaWidgetContent(
    data: SplitAgendaData,
    isRefreshing: Boolean = false
) {
    val size = LocalSize.current
    val isWide = size.width >= 260.dp

    if (isWide) {
        WideSplitLayout(
            data = data,
            isRefreshing = isRefreshing
        )
    } else {
        CompactSplitLayout(
            data = data,
            isRefreshing = isRefreshing
        )
    }
}

@Composable
internal fun SplitAgendaWidgetContent(
    events: List<WidgetDataRepository.WidgetEvent>,
    timePattern: String = "h:mm a",
    isRefreshing: Boolean = false,
    detailedRows: Boolean = false
) {
    val today = LocalDate.now()
    val todayCode = DayPagerUtils.msToDayCode(System.currentTimeMillis())
    val monthGrid = MonthGrid.compute(today.year, today.monthValue - 1, KashCalDataStore.FIRST_DAY_SYSTEM)
    val data = SplitAgendaData(
        eventsByDay = mapOf(todayCode to events),
        monthEvents = emptyMap(),
        monthGrid = monthGrid,
        today = today,
        todayDayCode = todayCode,
        maxEventsPerDay = 5,
        detailedRows = detailedRows,
        firstDayOfWeek = KashCalDataStore.FIRST_DAY_SYSTEM,
        timePattern = timePattern
    )
    SplitAgendaWidgetContent(data = data, isRefreshing = isRefreshing)
}

@SuppressLint("NonObservableLocale")
@Composable
private fun WideSplitLayout(
    data: SplitAgendaData,
    isRefreshing: Boolean
) {
    val today = data.today
    val locale = Locale.getDefault()

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.contentBackground)
            .cornerRadius(16.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Compact Date Header + 6-week Month Calendar Grid
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(start = 4.dp, top = 2.dp, end = 6.dp, bottom = 4.dp)
        ) {
            // Tapping header goes to today in KashCal
            SplitDateHeader(today = today, locale = locale)

            Spacer(modifier = GlanceModifier.height(4.dp))
            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(WidgetTheme.dimmedOnHeaderBackground)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))

            // Day-of-week header letters row
            val orderedDays = DateTimeUtils.getOrderedDaysOfWeek(data.firstDayOfWeek)
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            ) {
                orderedDays.forEach { dow ->
                    Box(
                        modifier = GlanceModifier.defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dow.getDisplayName(JavaTextStyle.NARROW, locale).uppercase(),
                            style = TextStyle(
                                color = WidgetTheme.secondaryText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // 6-week calendar grid
            Column(modifier = GlanceModifier.fillMaxSize()) {
                data.monthGrid.weeks.forEach { week ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        week.forEach { cell ->
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (cell.position == MonthGrid.DayPosition.MonthDate) {
                                    val isToday = cell.dayOfMonth == today.dayOfMonth
                                    val cellDayCode = today.year * 10000 + today.monthValue * 100 + cell.dayOfMonth
                                    val hasEvents = data.monthEvents[cellDayCode]?.isNotEmpty() == true

                                    if (isToday) {
                                        Box(
                                            modifier = GlanceModifier
                                                .size(17.dp)
                                                .background(WidgetTheme.accentColor)
                                                .cornerRadius(9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${cell.dayOfMonth}",
                                                style = TextStyle(
                                                    color = WidgetTheme.onHeaderBackground,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${cell.dayOfMonth}",
                                                style = TextStyle(
                                                    color = WidgetTheme.primaryText,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            )
                                            if (hasEvents) {
                                                Spacer(
                                                    modifier = GlanceModifier
                                                        .size(3.dp)
                                                        .background(WidgetTheme.accentColor)
                                                        .cornerRadius(2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subtle vertical separator
        Spacer(
            modifier = GlanceModifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .background(WidgetTheme.dimmedOnHeaderBackground)
        )

        // Right Column: Header with controls + LazyColumn with grouped items
        SplitAgendaListColumn(
            data = data,
            isRefreshing = isRefreshing,
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(start = 6.dp, top = 2.dp, end = 4.dp, bottom = 4.dp)
        )
    }
}

@Composable
internal fun SplitDateHeader(
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
    modifier: GlanceModifier = GlanceModifier
) {
    val dayOfWeekShort = today.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale).uppercase()
    val dayNumber = today.dayOfMonth.toString()
    val shortMonth = today.month.getDisplayName(JavaTextStyle.SHORT, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val shortYear = String.format(Locale.ROOT, "'%02d", today.year % 100)
    val monthAndYear = "$shortMonth $shortYear"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable(
                actionStartActivity<MainActivity>(
                    parameters = actionParametersOf(
                        ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_TODAY
                    )
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayNumber,
            style = TextStyle(
                color = WidgetTheme.primaryText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dayOfWeekShort,
                style = TextStyle(
                    color = WidgetTheme.accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                text = monthAndYear,
                style = TextStyle(
                    color = WidgetTheme.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun SplitAgendaListColumn(
    data: SplitAgendaData,
    isRefreshing: Boolean = false,
    modifier: GlanceModifier = GlanceModifier.fillMaxSize()
) {
    val context = LocalContext.current
    val upcomingItems = remember(data.eventsByDay, data.todayDayCode, data.maxEventsPerDay) {
        buildSplitUpcomingItems(
            eventsByDay = data.eventsByDay,
            todayDayCode = data.todayDayCode,
            nowMs = System.currentTimeMillis(),
            maxEventsPerDay = data.maxEventsPerDay,
            todayLabel = context.getString(R.string.label_today),
            tomorrowLabel = context.getString(R.string.label_tomorrow),
            emptyNoticeMessage = context.getString(R.string.split_agenda_no_events_today),
            locale = Locale.getDefault()
        )
    }

    LazyColumn(
        modifier = modifier
    ) {
        upcomingItems.forEach { item ->
            when (item) {
                is SplitAgendaItem.DayHeader -> item(itemId = item.itemId) {
                    SplitDayHeader(
                        dayCode = item.dayCode,
                        todayDayCode = data.todayDayCode,
                        label = item.label
                    )
                }
                is SplitAgendaItem.EventRow -> item(itemId = item.itemId) {
                    SplitEventRow(
                        event = item.event,
                        timePattern = data.timePattern,
                        detailedRows = data.detailedRows,
                        dayCode = item.dayCode
                    )
                }
                is SplitAgendaItem.EmptyTodayNotice -> item(itemId = item.itemId) {
                    SplitEmptyTodayNotice(
                        message = item.message
                    )
                }
                is SplitAgendaItem.AllDayEventStack -> item(itemId = item.itemId) {
                    SplitAllDayEventStack(
                        count = item.count,
                        dayCode = item.dayCode,
                        todayDayCode = data.todayDayCode
                    )
                }
                is SplitAgendaItem.AllDayTaskStack -> item(itemId = item.itemId) {
                    SplitAllDayTaskStack(
                        count = item.count,
                        dayCode = item.dayCode,
                        todayDayCode = data.todayDayCode
                    )
                }
            }
        }
    }
}

@Composable
internal fun SplitAllDayEventStack(
    count: Int,
    dayCode: Int,
    todayDayCode: Int
) {
    val context = LocalContext.current
    val isToday = dayCode == todayDayCode
    val text = context.getString(R.string.split_agenda_more_all_day_events, count)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(
                if (isToday) {
                    actionStartActivity<MainActivity>(
                        parameters = actionParametersOf(
                            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_TODAY
                        )
                    )
                } else {
                    actionStartActivity<MainActivity>(
                        parameters = actionParametersOf(
                            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_DATE,
                            ActionParameters.Key<Int>(EXTRA_DAY_CODE) to dayCode
                        )
                    )
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .background(WidgetTheme.dimmedOnHeaderBackground)
                .cornerRadius(12.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📅 $text",
                style = TextStyle(
                    color = WidgetTheme.primaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun SplitAllDayTaskStack(
    count: Int,
    dayCode: Int,
    todayDayCode: Int
) {
    val context = LocalContext.current
    val isToday = dayCode == todayDayCode
    val text = if (isToday) {
        context.getString(R.string.split_agenda_more_all_day_tasks_today, count)
    } else {
        context.getString(R.string.label_more_tasks_compact, count)
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(
                if (isToday) {
                    actionStartActivity<MainActivity>(
                        parameters = actionParametersOf(
                            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_TODAY
                        )
                    )
                } else {
                    actionStartActivity<MainActivity>(
                        parameters = actionParametersOf(
                            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_DATE,
                            ActionParameters.Key<Int>(EXTRA_DAY_CODE) to dayCode
                        )
                    )
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .background(WidgetTheme.dimmedOnHeaderBackground)
                .cornerRadius(12.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓ $text",
                style = TextStyle(
                    color = WidgetTheme.primaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SplitDayHeader(
    dayCode: Int,
    todayDayCode: Int,
    label: String
) {
    val isToday = dayCode == todayDayCode
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
            .clickable(
                if (isToday) {
                    actionStartActivity<MainActivity>(
                        parameters = actionParametersOf(
                            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_TODAY
                        )
                    )
                } else {
                    actionStartActivity<MainActivity>(
                        parameters = actionParametersOf(
                            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_DATE,
                            ActionParameters.Key<Int>(EXTRA_DAY_CODE) to dayCode
                        )
                    )
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (isToday) WidgetTheme.accentColor else WidgetTheme.primaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun SplitEmptyTodayNotice(
    message: String
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(
                actionStartActivity<MainActivity>(
                    parameters = actionParametersOf(
                        ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_CREATE_EVENT
                    )
                )
            )
    ) {
        Text(
            text = message,
            style = TextStyle(
                color = WidgetTheme.secondaryText,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun CompactSplitLayout(
    data: SplitAgendaData,
    isRefreshing: Boolean
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.contentBackground)
            .cornerRadius(16.dp)
            .padding(8.dp)
    ) {
        SplitAgendaListColumn(
            data = data,
            isRefreshing = isRefreshing,
            modifier = GlanceModifier.fillMaxSize()
        )
    }
}

@Composable
internal fun SplitEventRow(
    event: WidgetDataRepository.WidgetEvent,
    timePattern: String,
    detailedRows: Boolean,
    dayCode: Int
) {
    if (event.isTask) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(24.dp)
                    .clickable(
                        if (!event.isFutureOccurrence && !event.isReadOnly) {
                            actionRunCallback<ToggleTaskAction>(
                                actionParametersOf(
                                    ToggleTaskAction.EXTRA_TASK_ID_KEY to event.eventId,
                                    ToggleTaskAction.EXTRA_COMPLETED_KEY to event.isCompleted
                                )
                            )
                        } else actionStartActivity<MainActivity>(
                            parameters = actionParametersOf(
                                ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_SHOW_TASK,
                                ActionParameters.Key<Long>(EXTRA_TASK_ID) to event.eventId
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(
                        if (event.isCompleted) R.drawable.ic_widget_task_check
                        else R.drawable.ic_widget_task_uncheck
                    ),
                    contentDescription = if (event.isCompleted) "Tarefa concluída" else "Tarefa pendente",
                    modifier = GlanceModifier.size(18.dp)
                )
            }
            Spacer(modifier = GlanceModifier.width(6.dp))
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(
                        actionStartActivity<MainActivity>(
                            parameters = actionParametersOf(
                                ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_SHOW_TASK,
                                ActionParameters.Key<Long>(EXTRA_TASK_ID) to event.eventId
                            )
                        )
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EventRowInner(event, dayCode, showEventEmojis = true, timePattern = timePattern, detailedRows = detailedRows)
            }
        }
    } else {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clickable(
                    actionStartActivity<MainActivity>(
                        parameters = actionParametersOf(
                            ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_SHOW_EVENT,
                            ActionParameters.Key<Long>(EXTRA_EVENT_ID) to event.eventId,
                            ActionParameters.Key<Long>(EXTRA_OCCURRENCE_TS) to event.occurrenceStartTs,
                            ActionParameters.Key<Boolean>(EXTRA_IS_DEVICE_EVENT) to event.isDeviceEvent
                        )
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EventRowInner(event, dayCode, showEventEmojis = true, timePattern = timePattern, detailedRows = detailedRows)
        }
    }
}
