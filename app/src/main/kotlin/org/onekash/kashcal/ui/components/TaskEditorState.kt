package org.onekash.kashcal.ui.components

import androidx.compose.runtime.Immutable
import org.onekash.kashcal.data.tasks_org.TasksOrgDate
import org.onekash.kashcal.data.tasks_org.TasksOrgList
import org.onekash.kashcal.data.tasks_org.TasksOrgPriority
import org.onekash.kashcal.data.tasks_org.TasksOrgReminder
import org.onekash.kashcal.data.tasks_org.TasksOrgTag
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.onekash.kashcal.data.tasks_org.TasksOrgTaskDraft
import org.onekash.kashcal.data.tasks_org.TasksOrgTaskPatch

/**
 * The form state of the Tasks.org task editor (fork: create and edit). Holds only domain
 * types, never provider columns, so the editor is insulated from the unstable `v0` API like
 * the rest of the feature (report §6).
 *
 * [original] is null when creating and the edited task when editing; [buildPatch] diffs the
 * current fields against it so an edit sends only what changed, matching the provider's patch
 * semantics (report §4.3).
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
    /** The task's reminders as the editor currently has them (fork, Phase 4). */
    val reminders: List<TasksOrgReminder> = emptyList()
) {
    val isEditing: Boolean get() = original != null

    val isTitleValid: Boolean get() = title.isBlank().not()

    /** True when the edited task repeats, so the editor can warn about shifting the series. */
    val isRecurring: Boolean get() = original?.isRecurring == true

    /** The due date as the write model, or null when the editor has no due date set. */
    private fun dueDate(): TasksOrgDate? = dueMs?.let { TasksOrgDate(it, isAllDay) }

    /** Builds the draft for a new task from the current fields. */
    fun toDraft(): TasksOrgTaskDraft = TasksOrgTaskDraft(
        title = title.trim(),
        notes = notes.trim(),
        listId = listId,
        due = dueDate(),
        priority = priority
    )

    /**
     * Builds a sparse patch of the fields that changed from [original]. A field equal to the
     * original is left [TasksOrgTaskPatch.Field.Unset], so the update doesn't touch it.
     */
    fun buildPatch(): TasksOrgTaskPatch {
        val o = original ?: return TasksOrgTaskPatch()
        return TasksOrgTaskPatch(
            title = fieldIfChanged(title.trim(), o.title),
            notes = fieldIfChanged(notes.trim(), o.notes),
            listId = listFieldIfChanged(o.listId),
            due = dueFieldIfChanged(o),
            priority = fieldIfChanged(priority, o.priority)
        )
    }

    private fun listFieldIfChanged(originalListId: Long): TasksOrgTaskPatch.Field<Long> {
        val current = listId ?: return TasksOrgTaskPatch.Field.Unset
        return if (current != originalListId) TasksOrgTaskPatch.Field.Value(current)
        else TasksOrgTaskPatch.Field.Unset
    }

    private fun dueFieldIfChanged(o: TasksOrgTask): TasksOrgTaskPatch.Field<TasksOrgDate?> {
        val current = dueDate()
        val originalDate = if (o.dueTs > 0L) TasksOrgDate(o.dueTs, o.isAllDay) else null
        val changed = current?.allDay != originalDate?.allDay ||
            !sameInstantOrDay(current, originalDate)
        return if (changed) TasksOrgTaskPatch.Field.Value(current) else TasksOrgTaskPatch.Field.Unset
    }

    private fun sameInstantOrDay(a: TasksOrgDate?, b: TasksOrgDate?): Boolean {
        if (a == null || b == null) return a == b
        return a.ms == b.ms
    }

    companion object {
        /** Editor state for a brand-new task; defaults the due date to [defaultDueMs]. */
        fun forNew(
            writableLists: List<TasksOrgList>,
            defaultListId: Long?,
            defaultDueMs: Long?,
            defaultAllDay: Boolean,
            allTags: List<TasksOrgTag> = emptyList()
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
            selectedTagIds = emptySet()
        )

        /** Editor state seeded from an existing [task]. */
        fun forEdit(
            task: TasksOrgTask,
            writableLists: List<TasksOrgList>,
            allTags: List<TasksOrgTag> = emptyList(),
            selectedTagIds: Set<Long> = emptySet(),
            reminders: List<TasksOrgReminder> = emptyList()
        ): TaskEditorState =
            TaskEditorState(
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
                reminders = reminders
            )

        private fun <T> fieldIfChanged(current: T, original: T): TasksOrgTaskPatch.Field<T> =
            if (current != original) TasksOrgTaskPatch.Field.Value(current) else TasksOrgTaskPatch.Field.Unset
    }
}
