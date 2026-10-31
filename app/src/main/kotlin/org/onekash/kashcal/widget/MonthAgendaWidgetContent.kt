package org.onekash.kashcal.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.onekash.kashcal.MainActivity
import org.onekash.kashcal.R
import org.onekash.kashcal.ui.model.MonthGrid
import org.onekash.kashcal.util.DateTimeUtils
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Month + Agenda Dual Column Widget Content (FORK:experimental-widgets)
 *
 * Left column: Full month calendar grid (30/31 days with today highlighted and dots for days with events).
 * Right column: Today's agenda with events and tasks (with interactive checkboxes).
 */
@Composable
fun MonthAgendaWidgetContent(
    today: LocalDate,
    todayCode: Int,
    todayEvents: List<WidgetDataRepository.WidgetEvent>,
    monthGrid: MonthGrid,
    monthEvents: Map<Int, List<WidgetDataRepository.WidgetEvent>>,
    firstDayOfWeek: Int,
    timePattern: String = "h:mm a",
    isRefreshing: Boolean = false
) {
    val locale = Locale.getDefault()
    val monthTitle = today.month.getDisplayName(JavaTextStyle.FULL, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() } + " ${today.year}"
    val orderedDays = DateTimeUtils.getOrderedDaysOfWeek(firstDayOfWeek)

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.contentBackground)
            .cornerRadius(16.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Month Calendar Grid
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(end = 4.dp)
        ) {
            // Month Header: clickable to open app at today
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
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
                    text = monthTitle,
                    style = TextStyle(
                        color = WidgetTheme.accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }

            // Day-of-week header letters row
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
                monthGrid.weeks.forEach { week ->
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
                                    val dayCode = today.year * 10000 + today.monthValue * 100 + cell.dayOfMonth
                                    val hasEvents = monthEvents[dayCode]?.isNotEmpty() == true

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

        // Vertical separator
        Spacer(
            modifier = GlanceModifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 6.dp)
                .background(WidgetTheme.dimmedOnHeaderBackground)
        )

        // Right Column: Today's Agenda List
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(start = 6.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                val todayLabel = "${context.getString(R.string.date_widget_name)}, ${today.dayOfMonth}"
                Text(
                    text = todayLabel,
                    style = TextStyle(
                        color = WidgetTheme.primaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                WidgetRefreshButton(kind = WidgetKind.MONTH_AGENDA, isRefreshing = isRefreshing)
                WidgetAddButton()
            }

            if (todayEvents.isEmpty()) {
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
                            fontSize = 11.sp
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(vertical = 2.dp)
                ) {
                    items(todayEvents) { event ->
                        SplitEventRow(event, timePattern, detailedRows = false, dayCode = todayCode)
                    }
                }
            }
        }
    }
}
