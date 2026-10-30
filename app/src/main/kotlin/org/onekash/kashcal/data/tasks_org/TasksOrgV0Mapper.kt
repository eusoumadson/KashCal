package org.onekash.kashcal.data.tasks_org

import android.content.ContentValues
import android.database.Cursor
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single translation layer between KashCal's domain types and the Tasks.org public
 * ContentProvider API ([TasksOrgContract], `org.tasks.api` version `v0`).
 *
 * **Why this class exists (report §6):** `v0` is explicitly unstable — its columns and
 * parameters may change between Tasks.org releases. Every place that reads a cursor column or
 * builds a [ContentValues] for the provider goes through here, so a future `v0` (or `v1`)
 * contract change is absorbed in this one file: the repository, coordinator, UI and tests
 * speak only in the domain models ([TasksOrgTask], [TasksOrgTaskDraft], [TasksOrgTaskPatch],
 * [TasksOrgDate], [TasksOrgPriority]).
 *
 * All-day handling is the subtle part. Tasks.org stores an all-day date at the device's
 * **local** midnight and expects reads and writes in that zone (report §4.1); the mapper owns
 * both directions and keeps them apart: [readTask] hands the raw provider instant to the
 * reader (which normalizes to UTC midnight for grid alignment), while [putDate] converts a
 * domain all-day date back to local midnight before writing.
 */
@Singleton
class TasksOrgV0Mapper @Inject constructor() {

    /** The device zone used for all-day date conversions; a seam for tests. */
    var zone: ZoneId = ZoneId.systemDefault()

    // ---------------------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------------------

    /** Maps a `/v0/lists` row to a [TasksOrgList]. */
    fun readList(cursor: Cursor): TasksOrgList = TasksOrgList(
        id = cursor.long(TasksOrgContract.ID),
        title = cursor.string(TasksOrgContract.Lists.TITLE),
        color = cursor.long(TasksOrgContract.Lists.COLOR).toInt(),
        isReadOnly = cursor.string(TasksOrgContract.Lists.ACCESS) ==
            TasksOrgContract.Lists.ACCESS_READ_ONLY,
        accountId = cursor.long(TasksOrgContract.Lists.ACCOUNT_ID)
    )

    /** Maps a `/v0/places` row to a [TasksOrgPlace]. */
    fun readPlace(cursor: Cursor): TasksOrgPlace = TasksOrgPlace(
        id = cursor.long(TasksOrgContract.ID),
        name = cursor.string(TasksOrgContract.Places.NAME),
        displayName = cursor.string(TasksOrgContract.Places.DISPLAY_NAME),
        address = cursor.string(TasksOrgContract.Places.ADDRESS),
        latitude = cursor.double(TasksOrgContract.Places.LATITUDE),
        longitude = cursor.double(TasksOrgContract.Places.LONGITUDE),
        radius = cursor.long(TasksOrgContract.Places.RADIUS).toInt()
    )

    /** Maps a `/v0/accounts` row to a [TasksOrgAccount]. */
    fun readAccount(cursor: Cursor): TasksOrgAccount = TasksOrgAccount(
        id = cursor.long(TasksOrgContract.ID),
        name = cursor.string(TasksOrgContract.Accounts.NAME),
        type = cursor.string(TasksOrgContract.Accounts.TYPE),
        error = cursor.string(TasksOrgContract.Accounts.ERROR),
        repeatsOnServer = cursor.long(TasksOrgContract.Accounts.REPEATS_ON_SERVER) != 0L
    )

    // ---- Lists, tags and places: management writes ----

    /** Insert values for a new list in [accountId]. */
    fun listInsertValues(accountId: Long, title: String, color: Int): ContentValues = ContentValues().apply {
        put(TasksOrgContract.Lists.ACCOUNT_ID, accountId)
        put(TasksOrgContract.Lists.TITLE, title)
        put(TasksOrgContract.Lists.COLOR, color)
    }

    /** Update values for a list; only the non-null arguments are written. */
    fun listUpdateValues(title: String?, color: Int?): ContentValues = ContentValues().apply {
        title?.let { put(TasksOrgContract.Lists.TITLE, it) }
        color?.let { put(TasksOrgContract.Lists.COLOR, it) }
    }

    /** Update values for a tag; only the non-null arguments are written. */
    fun tagUpdateValues(name: String?, color: Int?): ContentValues = ContentValues().apply {
        name?.let { put(TasksOrgContract.Tags.NAME, it) }
        color?.let { put(TasksOrgContract.Tags.COLOR, it) }
    }

    /** Insert values for a new place. */
    fun placeInsertValues(draft: TasksOrgPlaceDraft): ContentValues = ContentValues().apply {
        put(TasksOrgContract.Places.NAME, draft.name)
        put(TasksOrgContract.Places.ADDRESS, draft.address)
        put(TasksOrgContract.Places.LATITUDE, draft.latitude)
        put(TasksOrgContract.Places.LONGITUDE, draft.longitude)
        put(TasksOrgContract.Places.RADIUS, draft.radius)
    }

    /** Update values for a place: its name, address and radius (the coordinates are fixed). */
    fun placeUpdateValues(name: String?, address: String?, radius: Int?): ContentValues = ContentValues().apply {
        name?.let { put(TasksOrgContract.Places.NAME, it) }
        address?.let { put(TasksOrgContract.Places.ADDRESS, it) }
        radius?.let { put(TasksOrgContract.Places.RADIUS, it) }
    }

    /** Maps a `/v0/tags` row to a [TasksOrgTag]. */
    fun readTag(cursor: Cursor): TasksOrgTag = TasksOrgTag(
        id = cursor.long(TasksOrgContract.ID),
        name = cursor.string(TasksOrgContract.Tags.NAME),
        color = cursor.long(TasksOrgContract.Tags.COLOR).toInt()
    )

    /** Parses the comma-joined `tag_ids` column of a task row into tag ids. */
    fun readTagIds(cursor: Cursor): List<Long> =
        cursor.string(TasksOrgContract.Tasks.TAG_IDS)
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.trim().toLongOrNull() }

    /** Builds the insert values for a new tag named [name]. */
    fun tagValues(name: String): ContentValues = ContentValues().apply {
        put(TasksOrgContract.Tags.NAME, name)
    }

    /** Builds the insert values that join tag [tagId] to task [taskId]. */
    fun taskTagValues(taskId: Long, tagId: Long): ContentValues = ContentValues().apply {
        put(TasksOrgContract.TaskTags.TASK_ID, taskId)
        put(TasksOrgContract.TaskTags.TAG_ID, tagId)
    }

    /**
     * Maps a `/v0/reminders` row to a [TasksOrgReminder], or null for a type this version of the
     * fork doesn't know, so a new reminder type in a later Tasks.org leaves the row alone
     * instead of breaking the editor.
     */
    fun readReminder(cursor: Cursor): TasksOrgReminder? {
        val id = cursor.long(TasksOrgContract.ID)
        return when (cursor.string(TasksOrgContract.Reminders.TYPE)) {
            TasksOrgContract.Reminders.TYPE_DATE_TIME ->
                TasksOrgReminder.AtTime(id = id, triggerAtMs = cursor.long(TasksOrgContract.Reminders.TRIGGER_AT))
            TasksOrgContract.Reminders.TYPE_SNOOZE ->
                TasksOrgReminder.Snooze(id = id, triggerAtMs = cursor.long(TasksOrgContract.Reminders.TRIGGER_AT))
            TasksOrgContract.Reminders.TYPE_RELATIVE_DUE -> relative(cursor, id, TasksOrgReminder.Relative.Anchor.DUE)
            TasksOrgContract.Reminders.TYPE_RELATIVE_START -> relative(cursor, id, TasksOrgReminder.Relative.Anchor.START)
            TasksOrgContract.Reminders.TYPE_RANDOM ->
                TasksOrgReminder.Random(id = id, periodMs = cursor.long(TasksOrgContract.Reminders.OFFSET_MS))
            TasksOrgContract.Reminders.TYPE_LOCATION_ARRIVAL -> TasksOrgReminder.Location(
                id, cursor.long(TasksOrgContract.Reminders.PLACE_ID), TasksOrgReminder.Location.Kind.ARRIVAL
            )
            TasksOrgContract.Reminders.TYPE_LOCATION_DEPARTURE -> TasksOrgReminder.Location(
                id, cursor.long(TasksOrgContract.Reminders.PLACE_ID), TasksOrgReminder.Location.Kind.DEPARTURE
            )
            else -> null
        }
    }

    private fun relative(cursor: Cursor, id: Long, anchor: TasksOrgReminder.Relative.Anchor) =
        TasksOrgReminder.Relative(
            id = id,
            anchor = anchor,
            offsetMs = cursor.long(TasksOrgContract.Reminders.OFFSET_MS),
            repeatCount = cursor.long(TasksOrgContract.Reminders.REPEAT_COUNT).toInt(),
            intervalMs = cursor.long(TasksOrgContract.Reminders.INTERVAL_MS)
        )

    /**
     * Builds the insert values for [reminder] on task [taskId]. Each type sends only its own
     * timing columns, since an inapplicable column throws (report §4, Reminders).
     */
    fun reminderInsertValues(taskId: Long, reminder: TasksOrgReminder): ContentValues =
        ContentValues().apply {
            put(TasksOrgContract.Reminders.TASK_ID, taskId)
            when (reminder) {
                is TasksOrgReminder.AtTime -> {
                    put(TasksOrgContract.Reminders.TYPE, TasksOrgContract.Reminders.TYPE_DATE_TIME)
                    put(TasksOrgContract.Reminders.TRIGGER_AT, reminder.triggerAtMs)
                }
                is TasksOrgReminder.Relative -> {
                    put(
                        TasksOrgContract.Reminders.TYPE,
                        when (reminder.anchor) {
                            TasksOrgReminder.Relative.Anchor.DUE -> TasksOrgContract.Reminders.TYPE_RELATIVE_DUE
                            TasksOrgReminder.Relative.Anchor.START -> TasksOrgContract.Reminders.TYPE_RELATIVE_START
                        }
                    )
                    put(TasksOrgContract.Reminders.OFFSET_MS, reminder.offsetMs)
                    if (reminder.repeatCount > 0) {
                        put(TasksOrgContract.Reminders.REPEAT_COUNT, reminder.repeatCount)
                        put(TasksOrgContract.Reminders.INTERVAL_MS, reminder.intervalMs)
                    }
                }
                is TasksOrgReminder.Random -> {
                    put(TasksOrgContract.Reminders.TYPE, TasksOrgContract.Reminders.TYPE_RANDOM)
                    put(TasksOrgContract.Reminders.OFFSET_MS, reminder.periodMs)
                }
                is TasksOrgReminder.Snooze -> {
                    put(TasksOrgContract.Reminders.TYPE, TasksOrgContract.Reminders.TYPE_SNOOZE)
                    put(TasksOrgContract.Reminders.TRIGGER_AT, reminder.triggerAtMs)
                }
                is TasksOrgReminder.Location -> {
                    put(
                        TasksOrgContract.Reminders.TYPE,
                        when (reminder.kind) {
                            TasksOrgReminder.Location.Kind.ARRIVAL -> TasksOrgContract.Reminders.TYPE_LOCATION_ARRIVAL
                            TasksOrgReminder.Location.Kind.DEPARTURE -> TasksOrgContract.Reminders.TYPE_LOCATION_DEPARTURE
                        }
                    )
                    put(TasksOrgContract.Reminders.PLACE_ID, reminder.placeId)
                }
            }
        }

    /**
     * Maps a `/v0/tasks` row to a [TasksOrgTask] in [lists]' and [tags]' context; null for a task
     * with no due date, which never lands on the calendar.
     */
    fun readTask(
        cursor: Cursor,
        lists: Map<Long, TasksOrgList>,
        tags: Map<Long, TasksOrgTag> = emptyMap()
    ): TasksOrgTask? {
        val due = cursor.long(TasksOrgContract.Tasks.DUE_DATE)
        if (due <= 0L) return null
        val listId = cursor.long(TasksOrgContract.Tasks.LIST_ID)
        val list = lists[listId]
        val start = cursor.long(TasksOrgContract.Tasks.START_DATE)
        return taskAt(
            id = cursor.long(TasksOrgContract.ID),
            title = cursor.string(TasksOrgContract.Tasks.TITLE),
            notes = cursor.string(TasksOrgContract.Tasks.NOTES),
            dueTs = due,
            isAllDay = cursor.long(TasksOrgContract.Tasks.DUE_ALL_DAY) != 0L,
            completedAt = cursor.long(TasksOrgContract.Tasks.COMPLETED_AT).takeIf { it > 0L },
            recurrence = cursor.string(TasksOrgContract.Tasks.RECURRENCE).ifBlank { null },
            listId = listId,
            listTitle = list?.title.orEmpty(),
            listColor = list?.color ?: 0,
            isReadOnly = cursor.long(TasksOrgContract.Tasks.IS_READ_ONLY) != 0L ||
                list?.isReadOnly == true,
            priority = priorityFromApi(cursor.string(TasksOrgContract.Tasks.PRIORITY)),
            tags = readTagIds(cursor).mapNotNull { tags[it]?.name },
            childCount = cursor.long(TasksOrgContract.Tasks.CHILD_COUNT).toInt(),
            uncompletedChildCount = cursor.long(TasksOrgContract.Tasks.UNCOMPLETED_CHILD_COUNT).toInt(),
            placeId = cursor.long(TasksOrgContract.Tasks.PLACE_ID),
            hideUntil = if (start > 0L) {
                TasksOrgDate(start, cursor.long(TasksOrgContract.Tasks.START_ALL_DAY) != 0L)
            } else null,
            repeatFrom = repeatFromApi(cursor.string(TasksOrgContract.Tasks.REPEAT_FROM)),
            zone = zone
        )
    }

    /** Maps a single-task row to its [TasksOrgSchedule]; null when the cursor has no row. */
    fun readSchedule(cursor: Cursor): TasksOrgSchedule? {
        if (!cursor.moveToFirst()) return null
        return TasksOrgSchedule(
            dueDate = cursor.long(TasksOrgContract.Tasks.DUE_DATE),
            dueAllDay = cursor.long(TasksOrgContract.Tasks.DUE_ALL_DAY) != 0L,
            startDate = cursor.long(TasksOrgContract.Tasks.START_DATE),
            startAllDay = cursor.long(TasksOrgContract.Tasks.START_ALL_DAY) != 0L,
            recurrence = cursor.string(TasksOrgContract.Tasks.RECURRENCE)
        )
    }

    // ---------------------------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------------------------

    /** Builds the [ContentValues] to complete a task at [completedAt], or reopen it when null. */
    fun completionValues(completedAt: Long?): ContentValues = ContentValues().apply {
        put(TasksOrgContract.Tasks.COMPLETED_AT, completedAt ?: 0L)
    }

    /** Builds the [ContentValues] that write [schedule] back, dates and flags together. */
    fun scheduleValues(schedule: TasksOrgSchedule): ContentValues = ContentValues().apply {
        put(TasksOrgContract.Tasks.DUE_DATE, schedule.dueDate)
        put(TasksOrgContract.Tasks.DUE_ALL_DAY, schedule.dueAllDay.toInt())
        put(TasksOrgContract.Tasks.START_DATE, schedule.startDate)
        put(TasksOrgContract.Tasks.START_ALL_DAY, schedule.startAllDay.toInt())
        put(TasksOrgContract.Tasks.RECURRENCE, schedule.recurrence)
    }

    /** Builds the insert [ContentValues] for a new task from [draft]. */
    fun insertValues(draft: TasksOrgTaskDraft): ContentValues = ContentValues().apply {
        put(TasksOrgContract.Tasks.TITLE, draft.title)
        put(TasksOrgContract.Tasks.NOTES, draft.notes)
        draft.listId?.let { put(TasksOrgContract.Tasks.LIST_ID, it) }
        draft.parentId?.let { put(TasksOrgContract.Tasks.PARENT_ID, it) }
        put(TasksOrgContract.Tasks.PRIORITY, draft.priority.toApi())
        putDate(TasksOrgContract.Tasks.DUE_DATE, TasksOrgContract.Tasks.DUE_ALL_DAY, draft.due)
        putDate(TasksOrgContract.Tasks.START_DATE, TasksOrgContract.Tasks.START_ALL_DAY, draft.start)
        draft.recurrence?.let {
            put(TasksOrgContract.Tasks.RECURRENCE, it)
            put(TasksOrgContract.Tasks.REPEAT_FROM, draft.repeatFrom.toApi())
        }
    }

    /**
     * Builds the update [ContentValues] for [patch]. Only the set fields are put, so the
     * provider's patch semantics leave the rest untouched. An all-day date always writes its
     * flag alongside the date, so switching a task between timed and all-day takes (report
     * §4.1).
     */
    fun updateValues(patch: TasksOrgTaskPatch): ContentValues = ContentValues().apply {
        patch.title.ifSet { put(TasksOrgContract.Tasks.TITLE, it) }
        patch.notes.ifSet { put(TasksOrgContract.Tasks.NOTES, it) }
        patch.listId.ifSet { put(TasksOrgContract.Tasks.LIST_ID, it) }
        patch.parentId.ifSet { put(TasksOrgContract.Tasks.PARENT_ID, it) }
        patch.priority.ifSet { put(TasksOrgContract.Tasks.PRIORITY, it.toApi()) }
        patch.due.ifSet {
            putDate(TasksOrgContract.Tasks.DUE_DATE, TasksOrgContract.Tasks.DUE_ALL_DAY, it)
        }
        patch.start.ifSet {
            putDate(TasksOrgContract.Tasks.START_DATE, TasksOrgContract.Tasks.START_ALL_DAY, it)
        }
        patch.recurrence.ifSet { put(TasksOrgContract.Tasks.RECURRENCE, it ?: "") }
        patch.repeatFrom.ifSet { put(TasksOrgContract.Tasks.REPEAT_FROM, it.toApi()) }
    }

    /**
     * Puts a [TasksOrgDate] under [dateColumn]/[allDayColumn], or clears both when [date] is
     * null. An all-day date is converted to the device's **local** midnight, Tasks.org's
     * storage convention; a timed date is written as-is.
     */
    private fun ContentValues.putDate(dateColumn: String, allDayColumn: String, date: TasksOrgDate?) {
        if (date == null) {
            put(dateColumn, 0L)
            put(allDayColumn, 0)
            return
        }
        if (date.allDay) {
            val localMidnight = Instant.ofEpochMilli(date.ms).atZone(zone)
                .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            put(dateColumn, localMidnight)
            put(allDayColumn, 1)
        } else {
            put(dateColumn, date.ms)
            put(allDayColumn, 0)
        }
    }

    private fun TasksOrgPriority.toApi(): String = when (this) {
        TasksOrgPriority.HIGH -> TasksOrgContract.Tasks.PRIORITY_HIGH
        TasksOrgPriority.MEDIUM -> TasksOrgContract.Tasks.PRIORITY_MEDIUM
        TasksOrgPriority.LOW -> TasksOrgContract.Tasks.PRIORITY_LOW
        TasksOrgPriority.NONE -> TasksOrgContract.Tasks.PRIORITY_NONE
    }

    private fun TasksOrgRepeatFrom.toApi(): String = when (this) {
        TasksOrgRepeatFrom.DUE_DATE -> TasksOrgContract.Tasks.REPEAT_FROM_DUE_DATE
        TasksOrgRepeatFrom.COMPLETION_DATE -> TasksOrgContract.Tasks.REPEAT_FROM_COMPLETION
    }

    private fun repeatFromApi(raw: String): TasksOrgRepeatFrom =
        if (raw == TasksOrgContract.Tasks.REPEAT_FROM_COMPLETION) TasksOrgRepeatFrom.COMPLETION_DATE
        else TasksOrgRepeatFrom.DUE_DATE

    private fun priorityFromApi(raw: String): TasksOrgPriority = when (raw) {
        TasksOrgContract.Tasks.PRIORITY_HIGH -> TasksOrgPriority.HIGH
        TasksOrgContract.Tasks.PRIORITY_MEDIUM -> TasksOrgPriority.MEDIUM
        TasksOrgContract.Tasks.PRIORITY_LOW -> TasksOrgPriority.LOW
        else -> TasksOrgPriority.NONE
    }

    private inline fun <T> TasksOrgTaskPatch.Field<T>.ifSet(block: (T) -> Unit) {
        if (this is TasksOrgTaskPatch.Field.Value) block(value)
    }

    private fun Boolean.toInt() = if (this) 1 else 0

    private fun Cursor.long(column: String): Long {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0L else getLong(index)
    }

    private fun Cursor.double(column: String): Double {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0.0 else getDouble(index)
    }

    private fun Cursor.string(column: String): String {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) "" else getString(index).orEmpty()
    }
}
