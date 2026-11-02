package org.onekash.kashcal.ui.components

import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.onekash.kashcal.R
import org.onekash.kashcal.data.tasks_org.TasksOrgContract
import org.onekash.kashcal.data.tasks_org.TasksOrgLocation
import org.onekash.kashcal.data.tasks_org.TasksOrgPlace
import org.onekash.kashcal.data.tasks_org.TasksOrgPlaceDraft
import org.onekash.kashcal.data.tasks_org.TasksOrgPriority
import org.onekash.kashcal.data.tasks_org.TasksOrgReminder
import org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom
import org.onekash.kashcal.domain.tasks.RecurrenceChoice
import org.onekash.kashcal.ui.shared.contrastForegroundOn
import java.time.LocalDate
import java.time.ZoneId

/**
 * The create / edit sheet for a Tasks.org task (fork). A self-contained editor that speaks only
 * domain types and reports the user's choices through [onSave] and [onDelete]; it holds no
 * reference to the provider, so the `v0` seam stays in [org.onekash.kashcal.data.tasks_org].
 *
 * Visually refactored to match [EventFormSheet]'s full-screen presentation, top bar, buttons,
 * and component spacing.
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
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val hasUnsavedChanges = remember(
        title, notes, listId, dueMs, isAllDay, startMs, startAllDay, priority,
        selectedTagIds, newTagNames, reminders, recurrence, repeatFrom, location
    ) {
        title != initial.title ||
            notes != initial.notes ||
            listId != initial.listId ||
            dueMs != initial.dueMs ||
            isAllDay != initial.isAllDay ||
            startMs != initial.startMs ||
            startAllDay != initial.startAllDay ||
            priority != initial.priority ||
            selectedTagIds.toSet() != initial.selectedTagIds ||
            newTagNames != initial.newTagNames ||
            reminders != initial.reminders ||
            recurrence != initial.recurrence ||
            repeatFrom != initial.repeatFrom ||
            location != initial.location
    }

    val attemptDismiss: () -> Unit = {
        if (hasUnsavedChanges && !showDiscardConfirm) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    fun current(): TaskEditorState = initial.copy(
        title = title, notes = notes, listId = listId, dueMs = dueMs, isAllDay = isAllDay,
        startMs = startMs, startAllDay = startAllDay, priority = priority,
        selectedTagIds = selectedTagIds.toSet(), newTagNames = newTagNames, reminders = reminders,
        recurrence = recurrence, repeatFrom = repeatFrom, allPlaces = places, location = location
    )

    // Drives the side-effect notice as the user edits, not only the initial state.
    val liveState = current()

    val paneTitleText = if (initial.isEditing) {
        stringResource(R.string.task_editor_edit_title)
    } else {
        stringResource(R.string.task_editor_new_title)
    }

    Dialog(
        onDismissRequest = attemptDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false
        )
    ) {
        BackHandler(onBack = attemptDismiss)

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .semantics { paneTitle = paneTitleText }
            ) {
                // TopBar with Cancel, Centered Title, and Save button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showDiscardConfirm) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_discard_confirm),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        TextButton(onClick = attemptDismiss) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Text(
                        text = paneTitleText,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val selectedList = initial.writableLists.firstOrNull { it.id == listId }
                    val selectedColor = selectedList?.color?.takeIf { it != 0 }
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val listBgColor = selectedColor?.let { Color(it) } ?: primaryColor
                    val contrastColor = remember(listBgColor) { contrastForegroundOn(listBgColor) }

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                showTitleError = true
                            } else {
                                onSave(current())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = listBgColor,
                            contentColor = contrastColor
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.task_editor_save),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title field
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; showTitleError = false },
                        label = { Text(stringResource(R.string.task_editor_title_label)) },
                        singleLine = true,
                        isError = showTitleError,
                        supportingText = if (showTitleError) {
                            { Text(stringResource(R.string.task_editor_title_required)) }
                        } else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Target List Selector
                    if (initial.writableLists.size > 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.task_editor_list_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                initial.writableLists.forEach { list ->
                                    val isSelected = list.id == listId
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { listId = list.id },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (list.color != 0) Color(list.color)
                                                        else MaterialTheme.colorScheme.primary
                                                    )
                                            )
                                        },
                                        label = { Text(list.title) }
                                    )
                                }
                            }
                        }
                    }

                    // Notes field
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.task_editor_notes_label)) },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Due date field
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

                    // Start date (hidden until)
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

                    // Recurrence
                    TaskRecurrenceSection(
                        choice = recurrence,
                        onChoice = { choice ->
                            recurrence = choice
                            if (choice != RecurrenceChoice.None && dueMs == null) {
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Priority
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.task_editor_priority_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    }

                    // Tags
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.task_editor_tags_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                        existing != null -> if (existing.id !in selectedTagIds) selectedTagIds = selectedTagIds + existing.id
                                        newTagNames.none { it.equals(name, ignoreCase = true) } -> newTagNames = newTagNames + name
                                    }
                                    newTagText = ""
                                }
                            ) { Text(stringResource(R.string.task_editor_add_tag)) }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Place and location reminders
                    TaskPlaceSection(
                        places = places,
                        location = location,
                        onLocation = { location = it },
                        onNewPlace = { showNewPlace = true }
                    )

                    // Reminders
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.task_editor_reminders_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    }

                    // Subtasks in Tasks.org
                    initial.original?.let { task ->
                        if (task.hasSubtasks) {
                            NoteText(stringResource(R.string.task_subtasks_summary, task.childCount, task.uncompletedChildCount))
                        }
                        TextButton(onClick = { openTaskInTasksOrg(context, task.id) }) {
                            Text(stringResource(R.string.task_subtasks_manage))
                        }
                    }

                    // Delete Task Button (edit mode)
                    if (initial.isEditing) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.task_editor_delete))
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
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
    val intent = Intent(Intent.ACTION_VIEW, TasksOrgContract.Tasks.viewUri(taskId))
        .setPackage(TasksOrgContract.PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

internal fun priorityLabel(p: TasksOrgPriority): Int = when (p) {
    TasksOrgPriority.HIGH -> R.string.task_priority_high
    TasksOrgPriority.MEDIUM -> R.string.task_priority_medium
    TasksOrgPriority.LOW -> R.string.task_priority_low
    TasksOrgPriority.NONE -> R.string.task_priority_none
}

private data class ReminderPreset(
    val labelRes: Int,
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

private fun relativeBefore(beforeMs: Long): TasksOrgReminder =
    TasksOrgReminder.Relative(anchor = TasksOrgReminder.Relative.Anchor.DUE, offsetMs = -beforeMs)

internal fun sameReminder(a: TasksOrgReminder, b: TasksOrgReminder): Boolean = when {
    a is TasksOrgReminder.AtTime && b is TasksOrgReminder.AtTime -> a.triggerAtMs == b.triggerAtMs
    a is TasksOrgReminder.Relative && b is TasksOrgReminder.Relative ->
        a.anchor == b.anchor && a.offsetMs == b.offsetMs
    a is TasksOrgReminder.Random && b is TasksOrgReminder.Random -> a.periodMs == b.periodMs
    else -> false
}

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
        is TasksOrgReminder.Location -> ""
    }

@Composable
private fun formatInstant(ms: Long, is24Hour: Boolean, timeFormat: String): String =
    org.onekash.kashcal.util.DateTimeUtils.formatEventDateShort(ms, false) + " " +
        org.onekash.kashcal.util.DateTimeUtils.formatEventTime(ms, false, org.onekash.kashcal.util.DateTimeUtils.getTimePattern(timeFormat, is24Hour))

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
