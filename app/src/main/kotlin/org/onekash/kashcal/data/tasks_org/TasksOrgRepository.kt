package org.onekash.kashcal.data.tasks_org

/**
 * Reads tasks and lists from the Tasks.org app, and creates, edits, completes and deletes
 * tasks there, through its public ContentProvider ([TasksOrgContract]).
 *
 * Reads return empty and writes return [TasksOrgWriteResult.Refused] (or
 * [TasksOrgWriteResult.ReadOnly]) when Tasks.org isn't installed, the permission is missing or
 * revoked, or the provider rejects the call; nothing throws. Tasks stay in Tasks.org: KashCal
 * stores none, and Tasks.org syncs a change on its own.
 *
 * Every column, URI and quirk of the unstable `v0` API is confined to [TasksOrgV0Mapper]; this
 * interface and its callers speak only the domain models.
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

    /**
     * Creates a task from [draft] in Tasks.org and returns its new id. Only
     * [TasksOrgTaskDraft.title] is required. All-day dates are written at the device's local
     * midnight.
     *
     * @return the new task id on success, [TasksOrgWriteResult.ReadOnly] for a read-only list,
     *   or [TasksOrgWriteResult.Refused] when there is no write permission or the value is bad
     */
    suspend fun createTask(draft: TasksOrgTaskDraft): TasksOrgWriteResult

    /**
     * Applies [patch] to the task [taskId] as a partial update: only the set fields change.
     * A no-op [patch] returns [TasksOrgWriteResult.Ok] without a write. See the report (§4.3)
     * for the provider's side effects of changing `due_date`, `list_id` and `recurrence`.
     *
     * @return [TasksOrgWriteResult.Ok] on success, [TasksOrgWriteResult.ReadOnly] for a
     *   read-only target, or [TasksOrgWriteResult.Refused] otherwise
     */
    suspend fun updateTask(taskId: Long, patch: TasksOrgTaskPatch): TasksOrgWriteResult

    /**
     * Deletes the task [taskId] and all of its subtasks; there is no undo in Tasks.org. The
     * caller confirms with the user first.
     *
     * @return [TasksOrgWriteResult.Ok], [TasksOrgWriteResult.ReadOnly] for a read-only target,
     *   or [TasksOrgWriteResult.Refused] otherwise
     */
    suspend fun deleteTask(taskId: Long): TasksOrgWriteResult

    /** Returns every Tasks.org tag, in Tasks.org's order; empty without the read permission. */
    suspend fun getTags(): List<TasksOrgTag>

    /** Returns the ids of the tags on the task [taskId]; empty when it has none or can't be read. */
    suspend fun getTagIdsForTask(taskId: Long): List<Long>

    /**
     * Creates the tag [name] (idempotent: an existing name returns its id) and returns its id,
     * or null when the write was refused.
     */
    suspend fun ensureTag(name: String): Long?

    /** Adds the tag [tagId] to the task [taskId]; a no-op when it's already there. */
    suspend fun addTagToTask(taskId: Long, tagId: Long): Boolean

    /** Removes the tag [tagId] from the task [taskId]. */
    suspend fun removeTagFromTask(taskId: Long, tagId: Long): Boolean

    /**
     * Returns the time-based reminders on the task [taskId] (fork, Phase 4); location/snooze/
     * random reminders are skipped. Empty when none or unreadable.
     */
    suspend fun getReminders(taskId: Long): List<TasksOrgReminder>

    /**
     * Adds [reminder] to the task [taskId]. Idempotent in Tasks.org: an insert matching an
     * existing reminder in every field returns that row.
     *
     * @return the reminder's id on success, or null when the write was refused
     */
    suspend fun addReminder(taskId: Long, reminder: TasksOrgReminder): Long?

    /** Deletes the reminder [reminderId]; returns false when refused or already gone. */
    suspend fun deleteReminder(reminderId: Long): Boolean
}
