package org.onekash.kashcal.domain.tasks

import kotlinx.coroutines.flow.Flow
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.tasks_org.TasksOrgList
import org.onekash.kashcal.data.tasks_org.TasksOrgManager
import org.onekash.kashcal.data.tasks_org.TasksOrgRepository
import org.onekash.kashcal.data.tasks_org.TasksOrgSchedule
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
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
