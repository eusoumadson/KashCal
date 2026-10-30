package org.onekash.kashcal.ui.components

import androidx.compose.runtime.Immutable
import org.onekash.kashcal.data.tasks_org.TasksOrgDate
import org.onekash.kashcal.data.tasks_org.TasksOrgList
import org.onekash.kashcal.data.tasks_org.TasksOrgLocation
import org.onekash.kashcal.data.tasks_org.TasksOrgPlace
import org.onekash.kashcal.data.tasks_org.TasksOrgPriority
import org.onekash.kashcal.data.tasks_org.TasksOrgReminder
import org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom
import org.onekash.kashcal.data.tasks_org.TasksOrgTag
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.onekash.kashcal.data.tasks_org.TasksOrgTaskDraft
import org.onekash.kashcal.data.tasks_org.TasksOrgTaskExtras
import org.onekash.kashcal.data.tasks_org.TasksOrgTaskPatch
import org.onekash.kashcal.domain.tasks.RecurrenceChoice
import org.onekash.kashcal.domain.tasks.normalizeRule

/**
 * The form state of the Tasks.org task editor (fork: create and edit). Holds only domain
 * types, never provider columns, so the editor is insulated from the unstable `v0` API like
 * the rest of the feature (report §6).
 *
 * [original] is null when creating and the edited task when editing; [buildPatch] diffs the
 * current fields against it so an edit sends only what changed, matching the provider's patch
 * semantics (report §4.3). [toExtras] carries what lives beside the task row: tags,
 * reminders and the place, saved with the task in one atomic batch.
 */
@Immutable
data class TaskEditorState(
    val original: TasksOrgTask?,
    val title: String,
    val notes: String,
    val listId: Long?,
    /** Due date in epoch millis, or null for a task with no due date in the editor. */
    val dueMs: Long?,
    val isAllDay: Boolean,
    val priority: TasksOrgPriority,
    /** Lists the user can write to, for the list picker. */
    val writableLists: List<TasksOrgList>,
    /** All Tasks.org tags, for the tag picker. */
    val allTags: List<TasksOrgTag> = emptyList(),
    /** Ids of the tags currently selected in the editor. */
    val selectedTagIds: Set<Long> = emptySet(),
    /** Names typed that aren't existing tags yet; created on save. */
    val newTagNames: List<String> = emptyList(),
    /** The task's non-location reminders as the editor currently has them. */
    val reminders: List<TasksOrgReminder> = emptyList(),
    /** When the task is hidden until, in epoch millis, or null. */
    val startMs: Long? = null,
    val startAllDay: Boolean = false,
    val recurrence: RecurrenceChoice = RecurrenceChoice.None,
    val repeatFrom: TasksOrgRepeatFrom = TasksOrgRepeatFrom.DUE_DATE,
    /** All saved places, for the place picker. */
    val allPlaces: List<TasksOrgPlace> = emptyList(),
    /** The task's place and its triggers; null when it has none. */
    val location: TasksOrgLocation? = null
) {
    val isEditing: Boolean get() = original != null

    val isTitleValid: Boolean get() = title.isBlank().not()

    /** True when the edited task repeats, so the editor can warn about shifting the series. */
    val isRecurring: Boolean get() = original?.isRecurring == true

    /**
     * True when saving may make Tasks.org adjust something beyond the field edited: moving the
     * due date of a task that repeats (a monthly `BYDAY` rule is re-anchored to the new weekday)
     * or that has a start date (a start relative to the due date moves with it). The editor shows
     * a notice; the real outcome is read back after the save.
     */
    val dueChangeHasSideEffects: Boolean
        get() {
            val o = original ?: return false
            val dueChanged = dueMs != o.dueTs.takeIf { it > 0L } || isAllDay != o.isAllDay
            return dueChanged && (o.isRecurring || o.hideUntil != null)
        }

    /** A repeat rule needs a due date; without one Tasks.org gives the task today's. */
    val recurrenceNeedsDue: Boolean get() = recurrence != RecurrenceChoice.None && dueMs == null

    private fun dueDate(): TasksOrgDate? = dueMs?.let { TasksOrgDate(it, isAllDay) }
    private fun startDate(): TasksOrgDate? = startMs?.let { TasksOrgDate(it, startAllDay) }

    /** Builds the draft for a new task from the current fields. */
    fun toDraft(): TasksOrgTaskDraft = TasksOrgTaskDraft(
        title = title.trim(),
        notes = notes.trim(),
        listId = listId,
        due = dueDate(),
        start = startDate(),
        priority = priority,
        recurrence = recurrence.toRrule(),
        repeatFrom = repeatFrom
    )

    /** What is saved beside the task row, in the same atomic batch. */
    fun toExtras(): TasksOrgTaskExtras = TasksOrgTaskExtras(
        tagIds = selectedTagIds,
        newTagNames = newTagNames,
        reminders = reminders,
        location = location
    )

    /**
     * Builds a sparse patch of the fields that changed from [original]. A field equal to the
     * original is left [TasksOrgTaskPatch.Field.Unset], so the update doesn't touch it.
     */
    fun buildPatch(): TasksOrgTaskPatch {
        val o = original ?: return TasksOrgTaskPatch()
        val newRule = recurrence.toRrule()
        val ruleChanged = normalizeRule(newRule) != normalizeRule(o.recurrence)
        return TasksOrgTaskPatch(
            title = fieldIfChanged(title.trim(), o.title),
            notes = fieldIfChanged(notes.trim(), o.notes),
            listId = listFieldIfChanged(o.listId),
            due = dateFieldIfChanged(dueDate(), if (o.dueTs > 0L) TasksOrgDate(o.dueTs, o.isAllDay) else null),
            start = dateFieldIfChanged(startDate(), o.hideUntil),
            priority = fieldIfChanged(priority, o.priority),
            recurrence = if (ruleChanged) TasksOrgTaskPatch.Field.Value(newRule) else TasksOrgTaskPatch.Field.Unset,
            // Only meaningful while the task repeats, and only when it changed.
            repeatFrom = if (newRule != null && repeatFrom != o.repeatFrom) {
                TasksOrgTaskPatch.Field.Value(repeatFrom)
            } else TasksOrgTaskPatch.Field.Unset
        )
    }

    private fun listFieldIfChanged(originalListId: Long): TasksOrgTaskPatch.Field<Long> {
        val current = listId ?: return TasksOrgTaskPatch.Field.Unset
        return if (current != originalListId) TasksOrgTaskPatch.Field.Value(current)
        else TasksOrgTaskPatch.Field.Unset
    }

    private fun dateFieldIfChanged(
        current: TasksOrgDate?,
        original: TasksOrgDate?
    ): TasksOrgTaskPatch.Field<TasksOrgDate?> =
        if (current == original) TasksOrgTaskPatch.Field.Unset else TasksOrgTaskPatch.Field.Value(current)

    companion object {
        /** Editor state for a brand-new task; defaults the due date to [defaultDueMs]. */
        fun forNew(
            writableLists: List<TasksOrgList>,
            defaultListId: Long?,
            defaultDueMs: Long?,
            defaultAllDay: Boolean,
            allTags: List<TasksOrgTag> = emptyList(),
            allPlaces: List<TasksOrgPlace> = emptyList()
        ): TaskEditorState = TaskEditorState(
            original = null,
            title = "",
            notes = "",
            listId = defaultListId ?: writableLists.firstOrNull()?.id,
            dueMs = defaultDueMs,
            isAllDay = defaultAllDay,
            priority = TasksOrgPriority.NONE,
            writableLists = writableLists,
            allTags = allTags,
            selectedTagIds = emptySet(),
            allPlaces = allPlaces
        )

        /**
         * Editor state seeded from an existing [task]. [reminders] are every reminder Tasks.org
         * has on it: the location ones become the task's [location], the rest stay as reminders.
         */
        fun forEdit(
            task: TasksOrgTask,
            writableLists: List<TasksOrgList>,
            allTags: List<TasksOrgTag> = emptyList(),
            selectedTagIds: Set<Long> = emptySet(),
            reminders: List<TasksOrgReminder> = emptyList(),
            allPlaces: List<TasksOrgPlace> = emptyList()
        ): TaskEditorState {
            val locationReminders = reminders.filterIsInstance<TasksOrgReminder.Location>()
            val placeId = locationReminders.firstOrNull()?.placeId ?: task.placeId
            val location = placeId.takeIf { it != 0L }?.let { id ->
                TasksOrgLocation(
                    placeId = id,
                    arrival = locationReminders.any { it.kind == TasksOrgReminder.Location.Kind.ARRIVAL },
                    departure = locationReminders.any { it.kind == TasksOrgReminder.Location.Kind.DEPARTURE }
                )
            }
            return TaskEditorState(
                original = task,
                title = task.title,
                notes = task.notes,
                listId = task.listId,
                dueMs = task.dueTs.takeIf { it > 0L },
                isAllDay = task.isAllDay,
                priority = task.priority,
                writableLists = writableLists,
                allTags = allTags,
                selectedTagIds = selectedTagIds,
                reminders = reminders.filter { it !is TasksOrgReminder.Location },
                startMs = task.hideUntil?.ms,
                startAllDay = task.hideUntil?.allDay ?: false,
                recurrence = RecurrenceChoice.of(task.recurrence),
                repeatFrom = task.repeatFrom,
                allPlaces = allPlaces,
                location = location
            )
        }

        private fun <T> fieldIfChanged(current: T, original: T): TasksOrgTaskPatch.Field<T> =
            if (current != original) TasksOrgTaskPatch.Field.Value(current) else TasksOrgTaskPatch.Field.Unset
    }
}
