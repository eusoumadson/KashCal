package org.onekash.kashcal.domain.tasks

import kotlinx.coroutines.flow.Flow
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.tasks_org.TasksOrgList
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

    val hiddenListIds: Flow<Set<Long>> get() = dataStore.hiddenTasksOrgListIds

    /** Emits when Tasks.org tasks change ([TasksOrgManager.changeSignal]). */
    val changeSignal: Flow<Int> get() = manager.changeSignal

    fun isInstalled(): Boolean = repository.isInstalled()

    fun hasReadPermission(): Boolean = repository.hasReadPermission()

    fun hasWritePermission(): Boolean = repository.hasWritePermission()

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
     * Creates a task from [draft] and re-queries the views. Nothing is queued: Tasks.org owns
     * the task and syncs the change on its own.
     */
    suspend fun createTask(draft: TasksOrgTaskDraft): TasksOrgWriteResult {
        val result = repository.createTask(draft)
        manager.notifyChanged()
        return result
    }

    /**
     * Applies [patch] to the task [taskId]. A no-op patch is a success with no write. See the
     * report (§4.3) for the provider side effects of changing `due_date`, `list_id` and
     * `recurrence`; the caller should re-read the row afterwards if it needs the result.
     */
    suspend fun updateTask(taskId: Long, patch: TasksOrgTaskPatch): TasksOrgWriteResult {
        val result = repository.updateTask(taskId, patch)
        manager.notifyChanged()
        return result
    }

    /**
     * Deletes the task [taskId] and its subtasks. Irreversible in Tasks.org, so the caller
     * confirms with the user first; there is no undo.
     */
    suspend fun deleteTask(taskId: Long): TasksOrgWriteResult {
        val result = repository.deleteTask(taskId)
        manager.notifyChanged()
        return result
    }

    /** Every Tasks.org tag, for the editor's tag picker. */
    suspend fun getTags(): List<TasksOrgTag> = repository.getTags()

    /** The ids of the tags currently on the task [taskId]. */
    suspend fun getTaskTagIds(taskId: Long): List<Long> = repository.getTagIdsForTask(taskId)

    /**
     * Makes the task [taskId]'s tags exactly [desiredTagIds] and [newTagNames]: existing tag
     * ids are added, each name in [newTagNames] is created (idempotent) then added, and any
     * current tag not in the desired set is removed. Re-queries the views once at the end.
     *
     * @return false when any write was refused
     */
    suspend fun setTaskTags(
        taskId: Long,
        desiredTagIds: Set<Long>,
        newTagNames: List<String> = emptyList()
    ): Boolean {
        var ok = true
        val resolvedNew = newTagNames.mapNotNull { repository.ensureTag(it) }
        ok = ok && resolvedNew.size == newTagNames.size
        val desired = desiredTagIds + resolvedNew
        val current = repository.getTagIdsForTask(taskId).toSet()

        (desired - current).forEach { ok = repository.addTagToTask(taskId, it) && ok }
        (current - desired).forEach { ok = repository.removeTagFromTask(taskId, it) && ok }
        manager.notifyChanged()
        return ok
    }

    /** The time-based reminders currently on the task [taskId], for the editor. */
    suspend fun getReminders(taskId: Long): List<TasksOrgReminder> = repository.getReminders(taskId)

    /**
     * Reconciles the task [taskId]'s reminders to [desired]: reminders already present (same id)
     * are kept, reminders in [desired] with id 0 are added, and current reminders missing from
     * [desired] are deleted. Reminders have no editable identity in the API, so editing one is
     * delete-plus-add; this method handles that by id set difference. Re-queries once.
     *
     * @return false when any write was refused
     */
    suspend fun setReminders(taskId: Long, desired: List<TasksOrgReminder>): Boolean {
        var ok = true
        val current = repository.getReminders(taskId)
        val keepIds = desired.mapNotNull { it.id.takeIf { id -> id != 0L } }.toSet()

        // Delete the ones the user removed.
        current.filter { it.id !in keepIds }.forEach {
            ok = repository.deleteReminder(it.id) && ok
        }
        // Add the new ones (id == 0).
        desired.filter { it.id == 0L }.forEach {
            ok = (repository.addReminder(taskId, it) != null) && ok
        }
        manager.notifyChanged()
        return ok
    }
}

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
