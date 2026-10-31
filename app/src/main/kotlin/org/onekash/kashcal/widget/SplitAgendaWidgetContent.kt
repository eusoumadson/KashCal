package org.onekash.kashcal.widget

import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.lazy.items
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
import org.onekash.kashcal.ui.util.DayPagerUtils
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
fun SplitAgendaWidgetContent(
    events: List<WidgetDataRepository.WidgetEvent>,
    timePattern: String = "h:mm a",
    isRefreshing: Boolean = false,
    detailedRows: Boolean = false
) {
    val size = LocalSize.current
    val isWide = size.width >= 260.dp
    val today = LocalDate.now()
    val todayCode = DayPagerUtils.msToDayCode(System.currentTimeMillis())

    if (isWide) {
        WideSplitLayout(
            events = events,
            today = today,
            todayCode = todayCode,
            timePattern = timePattern,
            isRefreshing = isRefreshing,
            detailedRows = detailedRows
        )
    } else {
        CompactSplitLayout(
            events = events,
            today = today,
            todayCode = todayCode,
            timePattern = timePattern,
            isRefreshing = isRefreshing,
            detailedRows = detailedRows
        )
    }
}

@Composable
private fun WideSplitLayout(
    events: List<WidgetDataRepository.WidgetEvent>,
    today: LocalDate,
    todayCode: Int,
    timePattern: String,
    isRefreshing: Boolean,
    detailedRows: Boolean
) {
    val locale = Locale.getDefault()
    val dayOfWeek = today.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale).uppercase()
    val dayNumber = today.dayOfMonth.toString()
    val monthName = today.month.getDisplayName(JavaTextStyle.FULL, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val yearStr = today.year.toString()

    val splitThreshold = 3
    val firstBatch = events.take(splitThreshold)
    val secondBatch = events.drop(splitThreshold)

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.contentBackground)
            .cornerRadius(16.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Big Date + initial events
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(start = 4.dp, top = 4.dp, end = 6.dp, bottom = 4.dp)
        ) {
            // Tapping header goes to today in KashCal
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(
                        actionStartActivity<MainActivity>(
                            parameters = actionParametersOf(
                                ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_TODAY
                            )
                        )
                    )
            ) {
                Text(
                    text = dayOfWeek,
                    style = TextStyle(
                        color = WidgetTheme.accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = dayNumber,
                    style = TextStyle(
                        color = WidgetTheme.primaryText,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = "$monthName $yearStr",
                    style = TextStyle(
                        color = WidgetTheme.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))
            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(WidgetTheme.dimmedOnHeaderBackground)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))

            if (events.isEmpty()) {
                val context = LocalContext.current
                Text(
                    text = context.getString(R.string.widget_no_events_today),
                    style = TextStyle(
                        color = WidgetTheme.secondaryText,
                        fontSize = 12.sp
                    ),
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
            } else {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    firstBatch.forEach { event ->
                        SplitEventRow(event, timePattern, detailedRows, todayCode)
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

        // Right Column: Header with controls + remaining events with LazyColumn scroll
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(start = 6.dp, top = 2.dp, end = 4.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                val moreTitle = if (secondBatch.isNotEmpty()) {
                    "${secondBatch.size} ${context.getString(R.string.upcoming_widget_name).lowercase()}"
                } else {
                    context.getString(R.string.widget_name)
                }
                Text(
                    text = moreTitle,
                    style = TextStyle(
                        color = WidgetTheme.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                WidgetRefreshButton(kind = WidgetKind.SPLIT_AGENDA, isRefreshing = isRefreshing)
                WidgetAddButton()
            }

            if (secondBatch.isEmpty()) {
                if (events.isNotEmpty()) {
                    val context = LocalContext.current
                    Box(
                        modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.widget_no_upcoming_events),
                            style = TextStyle(
                                color = WidgetTheme.secondaryText,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(vertical = 2.dp)
                ) {
                    items(secondBatch) { event ->
                        SplitEventRow(event, timePattern, detailedRows, todayCode)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSplitLayout(
    events: List<WidgetDataRepository.WidgetEvent>,
    today: LocalDate,
    todayCode: Int,
    timePattern: String,
    isRefreshing: Boolean,
    detailedRows: Boolean
) {
    val locale = Locale.getDefault()
    val dayOfWeekShort = today.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)
    val headerDate = "${today.dayOfMonth} $dayOfWeekShort"

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.contentBackground)
            .cornerRadius(16.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(WidgetTheme.headerBackground)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(
                        actionStartActivity<MainActivity>(
                            parameters = actionParametersOf(
                                ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_GO_TO_TODAY
                            )
                        )
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_calendar),
                    contentDescription = LocalContext.current.getString(R.string.cd_widget_calendar),
                    modifier = GlanceModifier.size(20.dp)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = headerDate,
                    style = TextStyle(
                        color = WidgetTheme.onHeaderBackground,
                        fontSize = WidgetTypography.headerTitle,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
            WidgetRefreshButton(kind = WidgetKind.SPLIT_AGENDA, isRefreshing = isRefreshing)
            WidgetAddButton()
        }

        if (events.isEmpty()) {
            val context = LocalContext.current
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(
                        actionStartActivity<MainActivity>(
                            parameters = actionParametersOf(
                                ActionParameters.Key<String>(EXTRA_ACTION) to ACTION_CREATE_EVENT
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.widget_no_events_today),
                    style = TextStyle(
                        color = WidgetTheme.secondaryText,
                        fontSize = WidgetTypography.contentTitle
                    )
                )
            }
        } else {
            LazyColumn(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(events) { event ->
                    SplitEventRow(event, timePattern, detailedRows, todayCode)
                }
            }
        }
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
