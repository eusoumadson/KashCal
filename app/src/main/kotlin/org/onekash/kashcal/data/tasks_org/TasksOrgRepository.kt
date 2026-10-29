package org.onekash.kashcal.data.tasks_org

/**
 * Reads tasks and lists from the Tasks.org app and completes tasks there, through its public
 * ContentProvider ([TasksOrgContract]).
 *
 * Reads return empty, and [setCompleted] returns false, when Tasks.org isn't installed, the
 * permission is missing or revoked, or the provider rejects the query; nothing throws. Tasks
 * stay in Tasks.org: KashCal stores none, and Tasks.org syncs a change on its own.
 */
interface TasksOrgRepository {

    /** True when the Tasks.org app is installed. */
    fun isInstalled(): Boolean

    /** True when KashCal holds [TasksOrgContract.PERMISSION_READ]. */
    fun hasReadPermission(): Boolean

    /** True when KashCal holds [TasksOrgContract.PERMISSION_WRITE]. */
    fun hasWritePermission(): Boolean

    /** Returns every Tasks.org list, in Tasks.org's order. */
    suspend fun getLists(): List<TasksOrgList>

    /**
     * Returns the tasks due in [startMs]..[endMs] (inclusive), open and completed, that are in
     * none of [hiddenListIds], sorted by due time. Tasks without a due date are never returned.
     *
     * @param search when not null, only tasks whose title or notes contain it
     */
    suspend fun getTasksDueInRange(
        startMs: Long,
        endMs: Long,
        hiddenListIds: Set<Long>,
        search: String? = null
    ): List<TasksOrgTask>

    /**
     * Completes the task [taskId] at [completedAt], or reopens it when [completedAt] is null.
     * Tasks.org moves a repeating task to its next occurrence instead of completing it.
     *
     * @return false when the write was refused or the task no longer exists
     */
    suspend fun setCompleted(taskId: Long, completedAt: Long?): Boolean

    /** Reads the scheduling columns of the task [taskId]; null when it can't be read. */
    suspend fun getSchedule(taskId: Long): TasksOrgSchedule?

    /**
     * Writes [schedule] back to the task [taskId], dates, all-day flags and rule together.
     *
     * @return false when the write was refused or the task no longer exists
     */
    suspend fun restoreSchedule(taskId: Long, schedule: TasksOrgSchedule): Boolean
}
