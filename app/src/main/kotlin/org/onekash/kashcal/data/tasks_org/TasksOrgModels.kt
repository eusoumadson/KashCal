package org.onekash.kashcal.data.tasks_org

import androidx.compose.runtime.Immutable

/** A task list of the Tasks.org app. [color] is ARGB, 0 when the list has none. */
@Immutable
data class TasksOrgList(
    val id: Long,
    val title: String,
    val color: Int,
    val isReadOnly: Boolean
)

/**
 * A Tasks.org task with a due date, placed on the calendar at that date.
 *
 * The times follow the Room [org.onekash.kashcal.data.db.entity.Event] conventions so every view
 * places a task the way it places an event:
 * - A timed task is a point: [startTs] and [endTs] are both its due time.
 * - An all-day task starts at UTC midnight of its due date and ends at the last millisecond of
 *   that day (UTC). Tasks.org stores the date in the device zone; the mapping
 *   ([AndroidTasksOrgRepository]) reads it there.
 *
 * A repeating task is one task in Tasks.org, due on its current occurrence. Completing it makes
 * Tasks.org move it to the next one, so only that occurrence is shown.
 */
@Immutable
data class TasksOrgTask(
    val id: Long,
    val title: String,
    val notes: String,
    val dueTs: Long,
    val isAllDay: Boolean,
    val startTs: Long,
    val endTs: Long,
    val startDay: Int,
    val endDay: Int,
    /** Epoch millis it was completed, or null while it's open. */
    val completedAt: Long?,
    /** RFC 5545 RRULE, or null for a task that doesn't repeat. */
    val recurrence: String?,
    val listId: Long,
    val listTitle: String,
    /** ARGB color of the list, 0 when it has none. */
    val listColor: Int,
    val isReadOnly: Boolean
) {
    val isCompleted: Boolean get() = completedAt != null
    val isRecurring: Boolean get() = recurrence != null
}
