package org.onekash.kashcal.ui.components

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import org.onekash.kashcal.data.tasks_org.TasksOrgContract
import org.onekash.kashcal.data.tasks_org.TasksOrgLocation
import org.onekash.kashcal.data.tasks_org.TasksOrgPlace
import org.onekash.kashcal.data.tasks_org.TasksOrgPlaceDraft
import org.onekash.kashcal.data.tasks_org.TasksOrgPriority
import org.onekash.kashcal.data.tasks_org.TasksOrgReminder
import org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom
import org.onekash.kashcal.domain.tasks.RecurrenceChoice
import org.onekash.kashcal.util.DateTimeUtils
import java.time.LocalDate
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
 *
 * Subtasks are not edited here: Tasks.org manages them, so an edited task offers a button that
 * opens it there.
 *
 * @param onCreatePlace saves a new place and reports it (null when Tasks.org refused it)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    initial: TaskEditorState,
    onSave: (TaskEditorState) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    timeFormat: String = "system",
    zone: ZoneId = ZoneId.systemDefault(),
    onCreatePlace: (TasksOrgPlaceDraft, (TasksOrgPlace?) -> Unit) -> Unit = { _, done -> done(null) }
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
    var startMs by rememberSaveable { mutableStateOf(initial.startMs) }
    var startAllDay by rememberSaveable { mutableStateOf(initial.startAllDay) }
    var priority by rememberSaveable { mutableStateOf(initial.priority) }
    var selectedTagIds by rememberSaveable { mutableStateOf(initial.selectedTagIds.toList()) }
    var newTagNames by rememberSaveable { mutableStateOf(initial.newTagNames) }
    var newTagText by rememberSaveable { mutableStateOf("") }
    // Not Parcelable-trivial; plain remember. The host closes the editor on recreation anyway.
    var reminders by remember { mutableStateOf(initial.reminders) }
    var recurrence by remember { mutableStateOf(initial.recurrence) }
    var repeatFrom by remember { mutableStateOf(initial.repeatFrom) }
    var places by remember { mutableStateOf(initial.allPlaces) }
    var location by remember { mutableStateOf(initial.location) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTitleError by remember { mutableStateOf(false) }
    var showNewPlace by remember { mutableStateOf(false) }
    var showRepeatDueNote by remember { mutableStateOf(false) }

    fun current(): TaskEditorState = initial.copy(
        title = title, notes = notes, listId = listId, dueMs = dueMs, isAllDay = isAllDay,
        startMs = startMs, startAllDay = startAllDay, priority = priority,
        selectedTagIds = selectedTagIds.toSet(), newTagNames = newTagNames, reminders = reminders,
        recurrence = recurrence, repeatFrom = repeatFrom, allPlaces = places, location = location
    )

    // Drives the side-effect notice as the user edits, not only the initial state.
    val liveState = current()

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
                Text(stringResource(R.string.task_editor_list_label), style = MaterialTheme.typography.labelLarge)
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
            TaskDateField(
                label = stringResource(R.string.task_editor_due_label),
                emptyLabel = stringResource(R.string.task_editor_set_due),
                ms = dueMs,
                allDay = isAllDay,
                timeFormat = timeFormat,
                is24Hour = is24Hour,
                zone = zone,
                onChange = { ms, allDay ->
                    dueMs = ms
                    isAllDay = allDay
                    // A repeat rule needs a due date.
                    if (ms == null) recurrence = RecurrenceChoice.None
                }
            )

            // ----- Start date (hidden until) -----
            TaskDateField(
                label = stringResource(R.string.task_editor_start_label),
                emptyLabel = stringResource(R.string.task_editor_set_start),
                ms = startMs,
                allDay = startAllDay,
                timeFormat = timeFormat,
                is24Hour = is24Hour,
                zone = zone,
                onChange = { ms, allDay -> startMs = ms; startAllDay = allDay }
            )

            // ----- Repeat -----
            TaskRecurrenceSection(
                choice = recurrence,
                onChoice = { choice ->
                    recurrence = choice
                    if (choice != RecurrenceChoice.None && dueMs == null) {
                        // Without a due date Tasks.org would pick today; say so and do it here.
                        dueMs = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                        isAllDay = true
                        showRepeatDueNote = true
                    }
                },
                repeatFrom = repeatFrom,
                onRepeatFrom = { repeatFrom = it }
            )
            if (showRepeatDueNote && recurrence != RecurrenceChoice.None) {
                NoteText(stringResource(R.string.task_repeat_needs_due))
            }
            if (liveState.dueChangeHasSideEffects) {
                NoteText(stringResource(R.string.task_editor_side_effects_note))
            }

            // ----- Priority -----
            Text(stringResource(R.string.task_editor_priority_label), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TasksOrgPriority.entries.forEach { p ->
                    FilterChip(
                        selected = p == priority,
                        onClick = { priority = p },
                        label = { Text(stringResource(priorityLabel(p))) }
                    )
                }
            }

            // ----- Tags: toggle existing ones, or type a new one. -----
            Text(stringResource(R.string.task_editor_tags_label), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                initial.allTags.forEach { tag ->
                    val selected = tag.id in selectedTagIds
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedTagIds = if (selected) selectedTagIds - tag.id else selectedTagIds + tag.id
                        },
                        label = { Text(tag.name) }
                    )
                }
                newTagNames.forEach { name ->
                    FilterChip(
                        selected = true,
                        onClick = { newTagNames = newTagNames - name },
                        label = { Text("$name  \u2715") }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    label = { Text(stringResource(R.string.task_editor_new_tag_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = newTagText.isNotBlank(),
                    onClick = {
                        val name = newTagText.trim()
                        val existing = initial.allTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                        when {
                            // Tag names are unique case-insensitively: an existing name selects it.
                            existing != null -> if (existing.id !in selectedTagIds) selectedTagIds = selectedTagIds + existing.id
                            newTagNames.none { it.equals(name, ignoreCase = true) } -> newTagNames = newTagNames + name
                        }
                        newTagText = ""
                    }
                ) { Text(stringResource(R.string.task_editor_add_tag)) }
            }

            // ----- Place and location reminders -----
            TaskPlaceSection(
                places = places,
                location = location,
                onLocation = { location = it },
                onNewPlace = { showNewPlace = true }
            )

            // ----- Reminders -----
            Text(stringResource(R.string.task_editor_reminders_label), style = MaterialTheme.typography.labelLarge)
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

            // ----- Subtasks live in Tasks.org -----
            initial.original?.let { task ->
                if (task.hasSubtasks) {
                    NoteText(stringResource(R.string.task_subtasks_summary, task.childCount, task.uncompletedChildCount))
                }
                TextButton(onClick = { openTaskInTasksOrg(context, task.id) }) {
                    Text(stringResource(R.string.task_subtasks_manage))
                }
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
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.task_editor_cancel)) }
                Button(onClick = {
                    if (title.isBlank()) showTitleError = true else onSave(current())
                }) { Text(stringResource(R.string.task_editor_save)) }
            }
        }
    }

    if (showNewPlace) {
        NewPlaceDialog(
            onDismiss = { showNewPlace = false },
            onSave = { draft ->
                showNewPlace = false
                onCreatePlace(draft) { created ->
                    if (created != null) {
                        places = places.filter { it.id != created.id } + created
                        location = TasksOrgLocation(created.id, arrival = true, departure = false)
                    }
                }
            }
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

@Composable
private fun NoteText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Opens the task [taskId] in Tasks.org, where subtasks are managed. */
internal fun openTaskInTasksOrg(context: android.content.Context, taskId: Long) {
    // Pinned to Tasks.org's package so another app matching the URI can't turn this into a chooser.
    val intent = Intent(Intent.ACTION_VIEW, TasksOrgContract.Tasks.viewUri(taskId))
        .setPackage(TasksOrgContract.PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Tasks.org uninstalled since the editor opened, or no activity handles the URI.
    }
}

internal fun priorityLabel(p: TasksOrgPriority): Int = when (p) {
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
    add(ReminderPreset(R.string.task_reminder_add_random) { TasksOrgReminder.Random(periodMs = 24 * 60 * 60_000L) })
}

/** A reminder that fires [beforeMs] before the due date. */
private fun relativeBefore(beforeMs: Long): TasksOrgReminder =
    TasksOrgReminder.Relative(anchor = TasksOrgReminder.Relative.Anchor.DUE, offsetMs = -beforeMs)

/** Two reminders are "the same" for de-duping by type, anchor and timing (ignoring id). */
internal fun sameReminder(a: TasksOrgReminder, b: TasksOrgReminder): Boolean = when {
    a is TasksOrgReminder.AtTime && b is TasksOrgReminder.AtTime -> a.triggerAtMs == b.triggerAtMs
    a is TasksOrgReminder.Relative && b is TasksOrgReminder.Relative ->
        a.anchor == b.anchor && a.offsetMs == b.offsetMs
    a is TasksOrgReminder.Random && b is TasksOrgReminder.Random -> a.periodMs == b.periodMs
    else -> false
}

/** A short human label for a reminder chip. */
@Composable
private fun reminderLabel(reminder: TasksOrgReminder, is24Hour: Boolean, timeFormat: String): String =
    when (reminder) {
        is TasksOrgReminder.AtTime -> formatInstant(reminder.triggerAtMs, is24Hour, timeFormat)
        is TasksOrgReminder.Snooze ->
            stringResource(R.string.task_reminder_snoozed_until, formatInstant(reminder.triggerAtMs, is24Hour, timeFormat))
        is TasksOrgReminder.Random ->
            stringResource(R.string.task_reminder_random, durationLabel(reminder.periodMs))
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
        // Location reminders are edited through the place section, never as chips here.
        is TasksOrgReminder.Location -> ""
    }

@Composable
private fun formatInstant(ms: Long, is24Hour: Boolean, timeFormat: String): String =
    DateTimeUtils.formatEventDateShort(ms, false) + " " +
        DateTimeUtils.formatEventTime(ms, false, DateTimeUtils.getTimePattern(timeFormat, is24Hour))

@Composable
private fun durationLabel(ms: Long): String {
    val mins = ms / 60_000L
    return when {
        mins % (24 * 60) == 0L -> stringResource(R.string.task_duration_days, mins / (24 * 60))
        mins % 60 == 0L -> stringResource(R.string.task_duration_hours, mins / 60)
        else -> stringResource(R.string.task_duration_minutes, mins)
    }
}

@Composable
private fun quantityLikeLabel(value: Long, res: Int, before: Boolean): String {
    val base = stringResource(res, value)
    return if (before) base else "+$base"
}
