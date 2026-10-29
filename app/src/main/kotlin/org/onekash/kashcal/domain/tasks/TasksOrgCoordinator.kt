package org.onekash.kashcal.domain.tasks

import kotlinx.coroutines.flow.Flow
import org.onekash.kashcal.data.preferences.KashCalDataStore
import org.onekash.kashcal.data.tasks_org.TasksOrgList
import org.onekash.kashcal.data.tasks_org.TasksOrgManager
import org.onekash.kashcal.data.tasks_org.TasksOrgRepository
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
     * Completes the task [taskId] now, or reopens it. Tasks.org moves a repeating task to its
     * next occurrence instead.
     *
     * @return false when Tasks.org refused the write (no write permission, the task is gone)
     */
    suspend fun setCompleted(taskId: Long, completed: Boolean): Boolean {
        val written = repository.setCompleted(taskId, if (completed) System.currentTimeMillis() else null)
        // Re-query at once instead of waiting for the debounced observer.
        manager.notifyChanged()
        return written
    }
}
