package org.onekash.kashcal.data.tasks_org

import android.content.ContentProviderOperation
import android.content.ContentValues

/**
 * Plans the atomic `applyBatch` that saves a task together with its tags, reminders and place
 * (fork). Tasks.org runs a batch in one transaction, so a failure part-way leaves nothing
 * written, unlike the separate calls this replaces, which could leave a task without its tags.
 *
 * These are pure functions over [TasksOrgV0Mapper]: they build the operation list and touch no
 * provider, so the plan is unit-testable. Provider specifics stay in the mapper and contract
 * (report §6); only the batch shape, which depends on the provider's back-reference rules, lives
 * here.
 *
 * Batch rules this relies on (CONTENT_PROVIDER.md, Batch operations):
 * - a back-reference resolves into a column and can only point at an **insert**;
 * - `task_id`, `tag_id`, `place_id` and `parent_id` are valid back-reference targets;
 * - an update matching no row returns 0 and does not abort, so the task update carries
 *   `withExpectedCount(1)` to roll the batch back when the task is gone.
 */

/** A planned batch and the index of the operation that inserts the task (create only). */
internal data class PlannedBatch(val operations: List<ContentProviderOperation>, val taskIndex: Int)

/** What a task has in Tasks.org right now, read before an update so the batch can diff. */
internal data class CurrentTaskState(
    val tagIds: Set<Long>,
    val reminders: List<TasksOrgReminder>,
    val placeId: Long
)

/** Plans the batch that creates [draft] with [extras]. */
internal fun TasksOrgV0Mapper.planCreate(draft: TasksOrgTaskDraft, extras: TasksOrgTaskExtras): PlannedBatch {
    val ops = ArrayList<ContentProviderOperation>()

    // New tags first: a tag insert is idempotent (an existing name returns that tag), and the
    // task_tags rows below point back at it.
    val newTagOps = linkedMapOf<String, Int>()
    extras.newTagNames.distinctBy { it.lowercase() }.forEach { name ->
        newTagOps[name] = ops.size
        ops += ContentProviderOperation.newInsert(TasksOrgContract.Tags.CONTENT_URI)
            .withValues(tagValues(name)).build()
    }

    // A place with no trigger files the task under it directly; with a trigger, the location
    // reminder insert sets the place itself.
    val location = extras.location
    val taskValues = insertValues(draft).apply {
        if (location != null && !location.hasTrigger) put(TasksOrgContract.Tasks.PLACE_ID, location.placeId)
    }
    val taskIndex = ops.size
    ops += ContentProviderOperation.newInsert(TasksOrgContract.Tasks.CONTENT_URI).withValues(taskValues).build()

    extras.tagIds.forEach { tagId ->
        ops += ContentProviderOperation.newInsert(TasksOrgContract.TaskTags.CONTENT_URI)
            .withValueBackReference(TasksOrgContract.TaskTags.TASK_ID, taskIndex)
            .withValue(TasksOrgContract.TaskTags.TAG_ID, tagId)
            .build()
    }
    newTagOps.values.forEach { tagOp ->
        ops += ContentProviderOperation.newInsert(TasksOrgContract.TaskTags.CONTENT_URI)
            .withValueBackReference(TasksOrgContract.TaskTags.TASK_ID, taskIndex)
            .withValueBackReference(TasksOrgContract.TaskTags.TAG_ID, tagOp)
            .build()
    }

    extras.reminders.filter { it.id == 0L }.forEach { ops += reminderInsert(it, taskIndex = taskIndex) }
    if (location != null) locationReminders(location).forEach { ops += reminderInsert(it, taskIndex = taskIndex) }

    return PlannedBatch(ops, taskIndex)
}

/**
 * Plans the batch that applies [patch] to [taskId] and reconciles its tags, reminders and place
 * with [extras], given what the task has now ([current]). Empty when there is nothing to write.
 */
internal fun TasksOrgV0Mapper.planUpdate(
    taskId: Long,
    patch: TasksOrgTaskPatch,
    extras: TasksOrgTaskExtras,
    current: CurrentTaskState
): List<ContentProviderOperation> {
    val ops = ArrayList<ContentProviderOperation>()

    val newTagOps = linkedMapOf<String, Int>()
    extras.newTagNames.distinctBy { it.lowercase() }.forEach { name ->
        newTagOps[name] = ops.size
        ops += ContentProviderOperation.newInsert(TasksOrgContract.Tags.CONTENT_URI)
            .withValues(tagValues(name)).build()
    }

    if (!patch.isEmpty) {
        ops += ContentProviderOperation.newUpdate(TasksOrgContract.Tasks.itemUri(taskId))
            .withValues(updateValues(patch))
            .withExpectedCount(1)
            .build()
    }

    // Tags: add what is new, remove what is gone. New names join through their insert's id.
    (extras.tagIds - current.tagIds).forEach { tagId ->
        ops += ContentProviderOperation.newInsert(TasksOrgContract.TaskTags.CONTENT_URI)
            .withValues(taskTagValues(taskId, tagId)).build()
    }
    newTagOps.values.forEach { tagOp ->
        ops += ContentProviderOperation.newInsert(TasksOrgContract.TaskTags.CONTENT_URI)
            .withValue(TasksOrgContract.TaskTags.TASK_ID, taskId)
            .withValueBackReference(TasksOrgContract.TaskTags.TAG_ID, tagOp)
            .build()
    }
    (current.tagIds - extras.tagIds).forEach { tagId ->
        ops += ContentProviderOperation.newDelete(TasksOrgContract.TaskTags.deleteUri(taskId, tagId)).build()
    }

    // Time reminders: a kept one has its id; id 0 is new; a current one missing is deleted.
    // Location reminders are reconciled below, through the place.
    val keptIds = extras.reminders.mapNotNull { it.id.takeIf { id -> id != 0L } }.toSet()
    current.reminders.filter { it !is TasksOrgReminder.Location && it.id !in keptIds }.forEach {
        ops += ContentProviderOperation.newDelete(TasksOrgContract.Reminders.itemUri(it.id)).build()
    }
    extras.reminders.filter { it.id == 0L }.forEach { ops += reminderInsert(it, taskId = taskId) }

    ops += placeOperations(taskId, extras.location, current)
    return ops
}

/**
 * Place changes. A task has at most one place, and a different place on an insert throws, so a
 * move first writes `place_id = 0` (which also drops the old reminders) and then files the
 * task under the new place; the location reminder insert sets the place by itself.
 */
private fun TasksOrgV0Mapper.placeOperations(
    taskId: Long,
    desired: TasksOrgLocation?,
    current: CurrentTaskState
): List<ContentProviderOperation> {
    val ops = ArrayList<ContentProviderOperation>()
    val taskUri = TasksOrgContract.Tasks.itemUri(taskId)
    fun setPlace(placeId: Long) {
        ops += ContentProviderOperation.newUpdate(taskUri)
            .withValue(TasksOrgContract.Tasks.PLACE_ID, placeId).build()
    }

    if (desired == null) {
        if (current.placeId != 0L) setPlace(0L)
        return ops
    }
    if (desired.placeId != current.placeId) {
        if (current.placeId != 0L) setPlace(0L)
        if (desired.hasTrigger) {
            locationReminders(desired).forEach { ops += reminderInsert(it, taskId = taskId) }
        } else {
            setPlace(desired.placeId)
        }
        return ops
    }
    // Same place: add or remove the arrival and departure triggers individually.
    val existing = current.reminders.filterIsInstance<TasksOrgReminder.Location>()
    TasksOrgReminder.Location.Kind.entries.forEach { kind ->
        val wanted = if (kind == TasksOrgReminder.Location.Kind.ARRIVAL) desired.arrival else desired.departure
        val have = existing.firstOrNull { it.kind == kind }
        when {
            wanted && have == null ->
                ops += reminderInsert(TasksOrgReminder.Location(placeId = desired.placeId, kind = kind), taskId = taskId)
            !wanted && have != null ->
                ops += ContentProviderOperation.newDelete(TasksOrgContract.Reminders.itemUri(have.id)).build()
        }
    }
    return ops
}

private fun locationReminders(location: TasksOrgLocation): List<TasksOrgReminder> = buildList {
    if (location.arrival) add(TasksOrgReminder.Location(placeId = location.placeId, kind = TasksOrgReminder.Location.Kind.ARRIVAL))
    if (location.departure) add(TasksOrgReminder.Location(placeId = location.placeId, kind = TasksOrgReminder.Location.Kind.DEPARTURE))
}

/**
 * A reminder insert for a task known by id ([taskId]) or inserted earlier in the same batch
 * ([taskIndex], resolved through a back-reference).
 */
private fun TasksOrgV0Mapper.reminderInsert(
    reminder: TasksOrgReminder,
    taskId: Long? = null,
    taskIndex: Int? = null
): ContentProviderOperation {
    val values: ContentValues = reminderInsertValues(taskId ?: 0L, reminder)
    values.remove(TasksOrgContract.Reminders.TASK_ID)
    val builder = ContentProviderOperation.newInsert(TasksOrgContract.Reminders.CONTENT_URI).withValues(values)
    if (taskIndex != null) {
        builder.withValueBackReference(TasksOrgContract.Reminders.TASK_ID, taskIndex)
    } else {
        builder.withValue(TasksOrgContract.Reminders.TASK_ID, taskId)
    }
    return builder.build()
}
