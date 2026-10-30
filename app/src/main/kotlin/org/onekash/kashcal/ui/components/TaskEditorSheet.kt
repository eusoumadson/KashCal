package org.onekash.kashcal.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onekash.kashcal.R
import org.onekash.kashcal.data.tasks_org.TasksOrgPriority
import org.onekash.kashcal.data.tasks_org.TasksOrgReminder
import org.onekash.kashcal.util.DateTimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The create / edit sheet for a Tasks.org task (fork). A self-contained editor that speaks only
 * domain types and reports the user's choices through [onSave] and [onDelete]; it holds no
 * reference to the provider, so the `v0` seam stays in [org.onekash.kashcal.data.tasks_org].
 *
 * The caller provides the initial [TaskEditorState] (via [TaskEditorState.forNew] or
 * [TaskEditorState.forEdit]) and persists the result. The sheet enforces a non-blank title and
 * writes the all-day flag alongside the date by construction ([TaskEditorState.toDraft] /
 * [TaskEditorState.buildPatch]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    initial: TaskEditorState,
    onSave: (TaskEditorState) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    timeFormat: String = "system",
    zone: ZoneId = ZoneId.systemDefault()
) {
    val context = LocalContext.current
    val is24Hour = remember(timeFormat) {
        when (timeFormat) {
            "12h" -> false
            "24h" -> true
            else -> DateFormat.is24HourFormat(context)
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by rememberSaveable { mutableStateOf(initial.title) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var listId by rememberSaveable { mutableStateOf(initial.listId) }
    var dueMs by rememberSaveable { mutableStateOf(initial.dueMs) }
    var isAllDay by rememberSaveable { mutableStateOf(initial.isAllDay) }
    var priority by rememberSaveable { mutableStateOf(initial.priority) }
    var selectedTagIds by rememberSaveable {
        mutableStateOf(initial.selectedTagIds.toList())
    }
    // Reminders aren't Parcelable-trivial; kept in a plain remember keyed to the editor.
    var reminders by remember { mutableStateOf(initial.reminders) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTitleError by remember { mutableStateOf(false) }

    fun current(): TaskEditorState = initial.copy(
        title = title, notes = notes, listId = listId, dueMs = dueMs,
        isAllDay = isAllDay, priority = priority, selectedTagIds = selectedTagIds.toSet(),
        reminders = reminders
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    if (initial.isEditing) R.string.task_editor_edit_title else R.string.task_editor_new_title
                ),
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; showTitleError = false },
                label = { Text(stringResource(R.string.task_editor_title_label)) },
                singleLine = true,
                isError = showTitleError,
                supportingText = if (showTitleError) {
                    { Text(stringResource(R.string.task_editor_title_required)) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.task_editor_notes_label)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            if (initial.writableLists.size > 1) {
                Text(
                    stringResource(R.string.task_editor_list_label),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    initial.writableLists.forEach { list ->
                        FilterChip(
                            selected = list.id == listId,
                            onClick = { listId = list.id },
                            label = { Text(list.title) }
                        )
                    }
                }
            }

            // ----- Due date -----
            Text(
                stringResource(R.string.task_editor_due_label),
                style = MaterialTheme.typography.labelLarge
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = {
                        Text(
                            dueMs?.let { DateTimeUtils.formatEventDateShort(it, isAllDay) }
                                ?: stringResource(R.string.task_editor_set_due)
                        )
                    }
                )
                if (dueMs != null && !isAllDay) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = { showTimePicker = true },
                        label = {
                            Text(
                                DateTimeUtils.formatEventTime(
                                    dueMs!!, false, DateTimeUtils.getTimePattern(timeFormat, is24Hour)
                                )
                            )
                        }
                    )
                }
                if (dueMs != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { dueMs = null }) { Text("\u2715") }
                }
            }
            if (dueMs != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.task_editor_all_day),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = isAllDay,
                        onCheckedChange = { allDay ->
                            isAllDay = allDay
                            // Snap to local midnight / noon so the flag and value agree.
                            dueMs = dueMs?.let { adjustForAllDay(it, allDay, zone) }
                        }
                    )
                }
            }

            // ----- Priority -----
            Text(
                stringResource(R.string.task_editor_priority_label),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TasksOrgPriority.entries.forEach { p ->
                    FilterChip(
                        selected = p == priority,
                        onClick = { priority = p },
                        label = { Text(stringResource(priorityLabel(p))) }
                    )
                }
            }

            // ----- Tags (fork, Phase 4): toggle existing Tasks.org tags on the task. -----
            if (initial.allTags.isNotEmpty()) {
                Text(
                    stringResource(R.string.task_editor_tags_label),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    initial.allTags.forEach { tag ->
                        val selected = tag.id in selectedTagIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedTagIds = if (selected) {
                                    selectedTagIds - tag.id
                                } else {
                                    selectedTagIds + tag.id
                                }
                            },
                            label = { Text(tag.name) }
                        )
                    }
                }
            }

            // ----- Reminders (fork, Phase 4): time-based reminders on the task. -----
            Text(
                stringResource(R.string.task_editor_reminders_label),
                style = MaterialTheme.typography.labelLarge
            )
            if (reminders.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminders.forEach { reminder ->
                        AssistChip(
                            onClick = { reminders = reminders - reminder },
                            label = { Text(reminderLabel(reminder, is24Hour, timeFormat) + "  \u2715") }
                        )
                    }
                }
            }
            // Quick-add presets. "At due time" needs a due date; the relative ones anchor on due.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reminderPresets(dueMs != null).forEach { preset ->
                    AssistChip(
                        onClick = {
                            val r = preset.build(dueMs)
                            if (r != null && reminders.none { sameReminder(it, r) }) {
                                reminders = reminders + r
                            }
                        },
                        label = { Text("+ " + stringResource(preset.labelRes)) }
                    )
                }
            }

            if (initial.isRecurring) {
                Text(
                    "\uD83D\uDD01 " + stringResource(R.string.task_editor_recurring_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ----- Actions -----
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (initial.isEditing) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text(stringResource(R.string.task_editor_delete))
                    }
                }
                Spacer(Modifier.width(0.dp).weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.task_editor_cancel))
                }
                Button(onClick = {
                    if (title.isBlank()) {
                        showTitleError = true
                    } else {
                        onSave(current())
                    }
                }) {
                    Text(stringResource(R.string.task_editor_save))
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueMs ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { picked ->
                        // DatePicker returns UTC midnight; keep the existing time of day for a
                        // timed task, else snap to all-day midnight/noon in the local zone.
                        dueMs = combineDateWithExistingTime(picked, dueMs, isAllDay, zone)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.task_editor_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.task_editor_cancel))
                }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker && dueMs != null) {
        val initialTime = remember { Instant.ofEpochMilli(dueMs!!).atZone(zone).toLocalTime() }
        val timeState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = is24Hour
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueMs = dueMs?.let { setTimeOfDay(it, timeState.hour, timeState.minute, zone) }
                    showTimePicker = false
                }) { Text(stringResource(R.string.task_editor_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.task_editor_cancel))
                }
            },
            text = { TimePicker(state = timeState) }
        )
    }

    if (showDeleteConfirm && initial.original != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.task_delete_confirm_title)) },
            text = { Text(stringResource(R.string.task_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(initial.original.id)
                }) { Text(stringResource(R.string.task_delete_confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.task_delete_confirm_cancel))
                }
            }
        )
    }
}

private fun priorityLabel(p: TasksOrgPriority): Int = when (p) {
    TasksOrgPriority.HIGH -> R.string.task_priority_high
    TasksOrgPriority.MEDIUM -> R.string.task_priority_medium
    TasksOrgPriority.LOW -> R.string.task_priority_low
    TasksOrgPriority.NONE -> R.string.task_priority_none
}

/** A quick-add reminder choice shown as a chip in the editor. */
private data class ReminderPreset(
    val labelRes: Int,
    /** Builds the reminder from the current due date; null when it can't apply (no due date). */
    val build: (dueMs: Long?) -> TasksOrgReminder?
)

private fun reminderPresets(hasDue: Boolean): List<ReminderPreset> = buildList {
    if (hasDue) {
        add(ReminderPreset(R.string.task_reminder_at_due) { TasksOrgReminder.Relative(anchor = TasksOrgReminder.Relative.Anchor.DUE, offsetMs = 0L) })
    }
    add(ReminderPreset(R.string.task_reminder_10_min) { relativeBefore(10 * 60_000L) })
    add(ReminderPreset(R.string.task_reminder_1_hour) { relativeBefore(60 * 60_000L) })
    add(ReminderPreset(R.string.task_reminder_1_day) { relativeBefore(24 * 60 * 60_000L) })
}

/** A reminder that fires [beforeMs] before the due date. */
private fun relativeBefore(beforeMs: Long): TasksOrgReminder =
    TasksOrgReminder.Relative(anchor = TasksOrgReminder.Relative.Anchor.DUE, offsetMs = -beforeMs)

/** Two reminders are "the same" for de-duping by type, anchor and timing (ignoring id). */
private fun sameReminder(a: TasksOrgReminder, b: TasksOrgReminder): Boolean = when {
    a is TasksOrgReminder.AtTime && b is TasksOrgReminder.AtTime -> a.triggerAtMs == b.triggerAtMs
    a is TasksOrgReminder.Relative && b is TasksOrgReminder.Relative ->
        a.anchor == b.anchor && a.offsetMs == b.offsetMs
    else -> false
}

/** A short human label for a reminder chip. */
@Composable
private fun reminderLabel(reminder: TasksOrgReminder, is24Hour: Boolean, timeFormat: String): String =
    when (reminder) {
        is TasksOrgReminder.AtTime -> DateTimeUtils.formatEventDateShort(reminder.triggerAtMs, false) +
            " " + DateTimeUtils.formatEventTime(reminder.triggerAtMs, false, DateTimeUtils.getTimePattern(timeFormat, is24Hour))
        is TasksOrgReminder.Relative -> {
            val before = reminder.offsetMs < 0
            val mins = kotlin.math.abs(reminder.offsetMs) / 60_000L
            when {
                reminder.offsetMs == 0L -> stringResource(R.string.task_reminder_at_due)
                mins % (24 * 60) == 0L -> quantityLikeLabel(mins / (24 * 60), R.string.task_reminder_days_before, before)
                mins % 60 == 0L -> quantityLikeLabel(mins / 60, R.string.task_reminder_hours_before, before)
                else -> quantityLikeLabel(mins, R.string.task_reminder_mins_before, before)
            }
        }
    }

@Composable
private fun quantityLikeLabel(value: Long, res: Int, before: Boolean): String {
    val base = stringResource(res, value)
    return if (before) base else "+$base"
}

/**
 * Keeps the calendar day of [utcMidnightMs] (as a [DatePicker] returns it) while preserving the
 * time of day of [existing] for a timed task, or snapping to local midnight for an all-day one.
 */
private fun combineDateWithExistingTime(
    utcMidnightMs: Long,
    existing: Long?,
    allDay: Boolean,
    zone: ZoneId
): Long {
    val date = Instant.ofEpochMilli(utcMidnightMs).atZone(ZoneId.of("UTC")).toLocalDate()
    val time = if (allDay || existing == null) {
        LocalTime.MIDNIGHT
    } else {
        Instant.ofEpochMilli(existing).atZone(zone).toLocalTime()
    }
    return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

/** Replaces the time of day of [ms] with [hour]:[minute] in [zone]. */
private fun setTimeOfDay(ms: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}

/** Snaps [ms] to local midnight when turning all-day on; leaves it otherwise. */
private fun adjustForAllDay(ms: Long, allDay: Boolean, zone: ZoneId): Long {
    if (!allDay) return ms
    val date: LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return date.atStartOfDay(zone).toInstant().toEpochMilli()
}
