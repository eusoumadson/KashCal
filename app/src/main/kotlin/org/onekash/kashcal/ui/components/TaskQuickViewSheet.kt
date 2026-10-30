package org.onekash.kashcal.ui.components

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.onekash.kashcal.R
import org.onekash.kashcal.data.tasks_org.TasksOrgContract
import org.onekash.kashcal.data.tasks_org.TasksOrgPriority
import org.onekash.kashcal.domain.EmojiMatcher
import org.onekash.kashcal.domain.model.DisplayEvent
import org.onekash.kashcal.util.DateTimeUtils

/**
 * Shows a Tasks.org task: its title, due date, list and notes, a checkbox that completes or
 * reopens it ([onSetCompleted]), and a button that opens Tasks.org, where the task is edited.
 *
 * The checkbox is disabled for a task in a read-only list or when KashCal lacks the write
 * permission ([canComplete]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskQuickViewSheet(
    displayEvent: DisplayEvent.Task,
    canComplete: Boolean,
    onSetCompleted: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    showEventEmojis: Boolean = true,
    timeFormat: String = "system",
    /** When non-null and the task is writable, an Edit button that opens the task editor. */
    onEdit: (() -> Unit)? = null,
) {
    val task = displayEvent.task
    val context = LocalContext.current
    val resources = LocalResources.current
    val is24HourDevice = DateFormat.is24HourFormat(context)
    val timePattern = remember(timeFormat, is24HourDevice) {
        DateTimeUtils.getTimePattern(timeFormat, is24HourDevice)
    }
    val displayTitle = remember(displayEvent.title, showEventEmojis) {
        EmojiMatcher.formatWithEmoji(displayEvent.title, showEventEmojis)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = task.notes.isNotBlank())

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            color = Color(displayEvent.calendarColor),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(16.dp))
                SelectionContainer {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                R.string.task_due,
                                // The real due date: an overdue task is placed on today.
                                formatTaskDue(task.dueCalendarTs(), task.dueAllDay, resources, timePattern)
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (task.isOverdue) {
                            Text(
                                text = stringResource(
                                    R.string.task_overdue_since,
                                    DateTimeUtils.formatEventDateShort(task.dueCalendarTs(), task.dueAllDay)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (task.priority != TasksOrgPriority.NONE) {
                            Text(
                                text = stringResource(R.string.task_editor_priority_label) + ": " +
                                    stringResource(priorityLabel(task.priority)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = priorityColor(task.priority) ?: MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (task.tags.isNotEmpty()) {
                            Text(
                                text = task.tags.joinToString(" ") { "#$it" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (task.hasSubtasks) {
                            Text(
                                text = stringResource(R.string.task_subtasks_summary, task.childCount, task.uncompletedChildCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (displayEvent.calendarName.isNotEmpty()) {
                            Text(
                                text = displayEvent.calendarName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (task.isRecurring) {
                            Text(
                                text = "\uD83D\uDD01 " + stringResource(R.string.task_repeats_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            TaskCompletionRow(
                isCompleted = task.isCompleted,
                enabled = canComplete,
                onSetCompleted = onSetCompleted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )

            if (task.notes.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            TextButton(
                onClick = { openTasksOrg(context, task.id) },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(stringResource(R.string.task_open_in_tasks_org))
            }

            // Subtasks are managed in Tasks.org: open the task there.
            if (task.hasSubtasks) {
                TextButton(
                    onClick = { openTasksOrg(context, task.id) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(stringResource(R.string.task_subtasks_manage))
                }
            }

            // Edit in KashCal (fork): only when the task is writable and the host wires it.
            if (onEdit != null && canComplete) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(stringResource(R.string.task_editor_edit))
                }
            }
        }
    }
}

/**
 * Checkbox row that marks a task completed or open. The whole row is the toggle target, and it
 * reads as one checkbox to accessibility services.
 */
@Composable
internal fun TaskCompletionRow(
    isCompleted: Boolean,
    enabled: Boolean,
    onSetCompleted: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = isCompleted,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onSetCompleted,
            )
    ) {
        Checkbox(checked = isCompleted, onCheckedChange = null, enabled = enabled)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(if (isCompleted) R.string.task_completed_label else R.string.task_mark_completed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Formats a task's due date: the date alone for an all-day task, else date and time. */
internal fun formatTaskDue(startTs: Long, isAllDay: Boolean, resources: Resources, timePattern: String): String {
    val date = DateTimeUtils.formatEventDateShort(startTs, isAllDay)
    return if (isAllDay) {
        resources.getString(R.string.event_date_all_day, date)
    } else {
        "$date \u00b7 ${DateTimeUtils.formatEventTime(startTs, isAllDay, timePattern)}"
    }
}

/** Opens the Tasks.org app; does nothing when it can't be launched. */
private fun openTasksOrg(context: Context, taskId: Long) {
    // Opens the task itself; falls back to the app when Tasks.org has no screen for the URI.
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, TasksOrgContract.Tasks.viewUri(taskId)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    } catch (_: Exception) {
        // Fall through to the launcher.
    }
    val intent = context.packageManager.getLaunchIntentForPackage(TasksOrgContract.PACKAGE) ?: return
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Uninstalled since the sheet opened.
    }
}
