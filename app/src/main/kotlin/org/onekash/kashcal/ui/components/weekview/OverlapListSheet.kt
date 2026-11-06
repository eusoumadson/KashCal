package org.onekash.kashcal.ui.components.weekview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.onekash.kashcal.R
import org.onekash.kashcal.domain.model.DisplayEvent
import org.onekash.kashcal.ui.components.OnTaskCheckedChange
import org.onekash.kashcal.ui.components.TaskCard
import org.onekash.kashcal.util.DateTimeUtils

/**
 * Shows [events] as a list of [CompactEventBlock]s or [TaskCard]s in a bottom sheet.
 *
 * [WeekViewContent] opens it from a "+N" badge with the events that badge hides: the hidden part
 * of a time-grid overlap group, or an all-day column's overflow.
 *
 * @param nowMs the grid's current time in epoch ms; events that have ended by then fade
 * @param onEventClick called when an event is tapped, followed by [onDismiss]
 * @param onTaskCheckedChange called when a task's checkbox is toggled // FORK:tasks-org
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlapListSheet(
    events: List<DisplayEvent>,
    showEventEmojis: Boolean = true,
    timePattern: String = "h:mma",
    nowMs: Long,
    onDismiss: () -> Unit,
    onEventClick: (DisplayEvent) -> Unit,
    onTaskCheckedChange: OnTaskCheckedChange? = null, // FORK:tasks-org
) {
    val sheetState = rememberModalBottomSheetState()
    val todayDayCode = DateTimeUtils.eventTsToDayCode(nowMs, isAllDay = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            val allTasks = events.isNotEmpty() && events.all { it is DisplayEvent.Task }
            val headerText = if (allTasks) {
                stringResource(R.string.label_more_tasks_compact, events.size)
            } else {
                stringResource(R.string.status_events_count, events.size)
            }
            Text(
                text = headerText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events) { displayEvent ->
                    // FORK:tasks-org — render interactive TaskCard for tasks
                    if (displayEvent is DisplayEvent.Task && onTaskCheckedChange != null) {
                        TaskCard(
                            displayEvent = displayEvent,
                            showEventEmojis = showEventEmojis,
                            timePattern = timePattern,
                            onCheckedChange = onTaskCheckedChange,
                            onClick = {
                                onEventClick(displayEvent)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    } else {
                        CompactEventBlock(
                            displayEvent = displayEvent,
                            onClick = {
                                onEventClick(displayEvent)
                                onDismiss()
                            },
                            showEventEmojis = showEventEmojis,
                            timePattern = timePattern,
                            isPast = DateTimeUtils.isEventPast(
                                displayEvent.endTs, displayEvent.endDay, displayEvent.isAllDay, nowMs, todayDayCode,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
