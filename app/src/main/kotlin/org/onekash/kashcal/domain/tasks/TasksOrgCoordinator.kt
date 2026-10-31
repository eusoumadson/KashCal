package org.onekash.kashcal.domain.tasks

import kotlinx.coroutines.flow.Flow
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.tasks_org.TasksOrgAccount
import org.onekash.kashcal.data.tasks_org.TasksOrgList
import org.onekash.kashcal.data.tasks_org.TasksOrgPlace
import org.onekash.kashcal.data.tasks_org.TasksOrgPlaceDraft
import org.onekash.kashcal.data.tasks_org.TasksOrgTaskExtras
import org.onekash.kashcal.data.tasks_org.TasksOrgVersion
import org.onekash.kashcal.data.tasks_org.TasksOrgManager
import org.onekash.kashcal.data.tasks_org.TasksOrgRepository
import org.onekash.kashcal.data.tasks_org.TasksOrgSchedule
import org.onekash.kashcal.data.tasks_org.TasksOrgReminder
import org.onekash.kashcal.data.tasks_org.TasksOrgTag
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.onekash.kashcal.data.tasks_org.TasksOrgTaskDraft
import org.onekash.kashcal.data.tasks_org.TasksOrgTaskPatch
import org.onekash.kashcal.data.tasks_org.TasksOrgWriteResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Tasks.org source for the ViewModels: its switch, which lists are shown, and task
 * completion. Reads of the tasks themselves go through
 * [org.onekash.kashcal.domain.reader.DisplayEventRepository].
 *
 * Nothing is queued: Tasks.org owns the tasks and syncs a change on its own, so a write here
 * goes straight to its provider.
 */
@Singleton
class TasksOrgCoordinator @Inject constructor(
    private val repository: TasksOrgRepository,
    private val manager: TasksOrgManager,
    private val dataStore: KashCalDataStore
) {
    val enabled: Flow<Boolean> get() = dataStore.tasksOrgEnabled

    // FORK:tasks-widget
    val tasksInWidgetsEnabled: Flow<Boolean> get() = dataStore.tasksInWidgetsEnabled

    suspend fun setTasksInWidgetsEnabled(enabled: Boolean) {
        dataStore.setTasksInWidgetsEnabled(enabled)
        manager.notifyChanged()
    }

    val hiddenListIds: Flow<Set<Long>> get() = dataStore.hiddenTasksOrgListIds

    /** Emits when Tasks.org tasks change ([TasksOrgManager.changeSignal]). */
    val changeSignal: Flow<Int> get() = manager.changeSignal

    fun isInstalled(): Boolean = repository.isInstalled()

    fun hasReadPermission(): Boolean = repository.hasReadPermission()

    fun hasWritePermission(): Boolean = repository.hasWritePermission()

    /** The installed Tasks.org version, or null when it isn't installed. */
    fun installedVersion(): TasksOrgVersion? = repository.installedVersion()

    /** Reads one task fresh from Tasks.org, e.g. to show what a write really did. */
    suspend fun getTask(taskId: Long): TasksOrgTask? = repository.getTask(taskId)

    suspend fun getLists(): List<TasksOrgList> = repository.getLists()

    /**
     * Switches the source on or off. Switching on without the read permission leaves it off
     * ([TasksOrgManager.onEnabled] turns it back off); the caller asks for the permission first.
     */
    suspend fun setEnabled(enabled: Boolean) {
        dataStore.setTasksOrgEnabled(enabled)
        if (enabled) manager.onEnabled() else manager.onDisabled()
    }

    /** Shows or hides the tasks of the Tasks.org list [listId]. */
    suspend fun setListShown(listId: Long, shown: Boolean) {
        val hidden = dataStore.getHiddenTasksOrgListIds()
        dataStore.setHiddenTasksOrgListIds(if (shown) hidden - listId else hidden + listId)
        manager.notifyChanged()
    }

    /**
     * Completes the task [task] now, or reopens it. Tasks.org moves a repeating task to its
     * next occurrence instead of completing it, so before completing one this reads its
     * schedule, which [undo] writes back.
     */
    suspend fun setCompleted(task: TasksOrgTask, completed: Boolean): TaskCompletionResult {
        // A later occurrence isn't a row in Tasks.org: completing it would complete the current one.
        if (task.isFutureOccurrence) return TaskCompletionResult.Refused
        val undo: TaskCompletionUndo? = if (completed && task.isRecurring) {
            // Unreadable schedule: reopening alone wouldn't bring the occurrence back, so no undo.
            repository.getSchedule(task.id)?.let { TaskCompletionUndo.RestoreSchedule(task.id, it) }
        } else {
            TaskCompletionUndo.SetCompletedAt(task.id, task.completedAt)
        }
        val written = repository.setCompleted(task.id, if (completed) System.currentTimeMillis() else null)
        // Re-query at once instead of waiting for the debounced observer.
        manager.notifyChanged()
        return if (written) TaskCompletionResult.Written(undo) else TaskCompletionResult.Refused
    }

    /**
     * Reverts a change made by [setCompleted].
     *
     * @return false when Tasks.org refused the write
     */
    suspend fun undo(undo: TaskCompletionUndo): Boolean {
        val written = when (undo) {
            is TaskCompletionUndo.SetCompletedAt -> repository.setCompleted(undo.taskId, undo.completedAt)
            // Back to the completed occurrence's dates, then open: an account that repeats on
            // the server leaves the task completed with its dates unchanged instead of moving them.
            is TaskCompletionUndo.RestoreSchedule ->
                repository.restoreSchedule(undo.taskId, undo.schedule) &&
                    repository.setCompleted(undo.taskId, null)
        }
        manager.notifyChanged()
        return written
    }

    /** Returns every Tasks.org list that accepts writes, for the create/edit list picker. */
    suspend fun getWritableLists(): List<TasksOrgList> = repository.getLists().filter { !it.isReadOnly }

    /**
     * Deletes the task [taskId] and its subtasks. Irreversible in Tasks.org, so the caller
     * confirms with the user first; there is no undo.
     */
    suspend fun deleteTask(taskId: Long): TasksOrgWriteResult {
        val result = repository.deleteTask(taskId)
        manager.notifyChanged()
        return result
    }

    /**
     * Creates a task with its tags, reminders and place in one atomic batch, so a failure
     * part-way leaves nothing behind (the separate writes could leave a task without its tags).
     */
    suspend fun createTask(draft: TasksOrgTaskDraft, extras: TasksOrgTaskExtras): TasksOrgWriteResult {
        val result = repository.createTaskWithExtras(draft, extras)
        manager.notifyChanged()
        return result
    }

    /**
     * Applies [patch] and reconciles tags, reminders and place for [original] in one atomic
     * batch. Changing the due date of a repeating task can make Tasks.org rewrite its rule (a
     * monthly `2TU` becomes `3WE`, for instance), so after such a save the task is read back
     * and [TasksOrgSaveOutcome.recurrenceAdjusted] says whether the rule is no longer the one
     * it had. Nothing is predicted: the check compares what Tasks.org stored.
     */
    suspend fun updateTask(
        original: TasksOrgTask,
        patch: TasksOrgTaskPatch,
        extras: TasksOrgTaskExtras
    ): TasksOrgSaveOutcome {
        val result = repository.updateTaskWithExtras(original.id, patch, extras)
        manager.notifyChanged()
        val dateMoved = patch.due !is TasksOrgTaskPatch.Field.Unset
        val ruleUntouched = patch.recurrence is TasksOrgTaskPatch.Field.Unset
        val adjusted = result is TasksOrgWriteResult.Ok && original.isRecurring && dateMoved && ruleUntouched &&
            repository.getTask(original.id)?.let { fresh ->
                normalizeRule(fresh.recurrence) != normalizeRule(original.recurrence)
            } == true
        return TasksOrgSaveOutcome(result, recurrenceAdjusted = adjusted)
    }

    // ---- Accounts, lists, tags and places (settings) ----

    /** The Tasks.org sync accounts, for list creation and the health notice. */
    suspend fun getAccounts(): List<TasksOrgAccount> = repository.getAccounts()

    suspend fun updateList(listId: Long, title: String? = null, color: Int? = null): TasksOrgWriteResult =
        repository.updateList(listId, title, color).also { manager.notifyChanged() }

    /** Deletes a list **and its tasks**; the caller confirms first. */
    suspend fun deleteList(listId: Long): TasksOrgWriteResult =
        repository.deleteList(listId).also { manager.notifyChanged() }

    suspend fun updateTag(tagId: Long, name: String? = null, color: Int? = null): TasksOrgWriteResult =
        repository.updateTag(tagId, name, color).also { manager.notifyChanged() }

    suspend fun deleteTag(tagId: Long): TasksOrgWriteResult =
        repository.deleteTag(tagId).also { manager.notifyChanged() }

    suspend fun getPlaces(): List<TasksOrgPlace> = repository.getPlaces()

    suspend fun createPlace(draft: TasksOrgPlaceDraft): TasksOrgWriteResult =
        repository.createPlace(draft)

    suspend fun updatePlace(placeId: Long, name: String? = null, address: String? = null, radius: Int? = null): TasksOrgWriteResult =
        repository.updatePlace(placeId, name, address, radius)

    suspend fun deletePlace(placeId: Long): TasksOrgWriteResult =
        repository.deletePlace(placeId).also { manager.notifyChanged() }

    /** Every Tasks.org tag, for the editor's tag picker. */
    suspend fun getTags(): List<TasksOrgTag> = repository.getTags()

    /** The ids of the tags currently on the task [taskId]. */
    suspend fun getTaskTagIds(taskId: Long): List<Long> = repository.getTagIdsForTask(taskId)

    /** Every reminder currently on the task [taskId], for the editor. */
    suspend fun getReminders(taskId: Long): List<TasksOrgReminder> = repository.getReminders(taskId)
}

/** Strips the optional `RRULE:` prefix and spaces, so two spellings of one rule compare equal. */
internal fun normalizeRule(rule: String?): String =
    rule.orEmpty().trim().removePrefix("RRULE:").trim()

/** Outcome of [TasksOrgCoordinator.updateTask]. */
data class TasksOrgSaveOutcome(
    val result: TasksOrgWriteResult,
    /** True when Tasks.org changed a repeating task's rule as a side effect of a date change. */
    val recurrenceAdjusted: Boolean = false
)

/** Outcome of [TasksOrgCoordinator.setCompleted]. */
sealed interface TaskCompletionResult {
    /** Tasks.org took the write; [undo] reverts it, null when it can't be reverted. */
    data class Written(val undo: TaskCompletionUndo?) : TaskCompletionResult

    /** Tasks.org refused the write: no write permission, or the task is gone. */
    data object Refused : TaskCompletionResult
}

/** How to revert a completion change made by [TasksOrgCoordinator.setCompleted]. */
sealed interface TaskCompletionUndo {
    val taskId: Long

    /** Writes the task's completion time back; null reopens it. */
    data class SetCompletedAt(override val taskId: Long, val completedAt: Long?) : TaskCompletionUndo

    /** Moves a repeating task back to the occurrence that was completed. */
    data class RestoreSchedule(override val taskId: Long, val schedule: TasksOrgSchedule) : TaskCompletionUndo
}
