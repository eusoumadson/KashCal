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

/** A Tasks.org tag: a named, colored label a task can carry. [color] is ARGB, 0 when unset. */
@Immutable
data class TasksOrgTag(
    val id: Long,
    val name: String,
    val color: Int
)

/**
 * A time-based reminder on a Tasks.org task (fork, Phase 4). Only the time types are modelled;
 * location reminders are out of scope.
 *
 * - [AtTime] fires at an absolute instant ([triggerAtMs]).
 * - [Relative] fires at a signed [offsetMs] from the task's due or start date ([anchor]);
 *   negative is before. A repeating reminder also carries [repeatCount] and [intervalMs].
 *
 * [id] is 0 for a reminder not yet written to Tasks.org.
 */
@Immutable
sealed interface TasksOrgReminder {
    val id: Long

    @Immutable
    data class AtTime(override val id: Long = 0L, val triggerAtMs: Long) : TasksOrgReminder

    @Immutable
    data class Relative(
        override val id: Long = 0L,
        val anchor: Anchor,
        val offsetMs: Long,
        val repeatCount: Int = 0,
        val intervalMs: Long = 0L
    ) : TasksOrgReminder {
        enum class Anchor { DUE, START }
    }
}

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
    val isReadOnly: Boolean,
    /** Task priority; defaults to [TasksOrgPriority.NONE] when the provider omits it. */
    val priority: TasksOrgPriority = TasksOrgPriority.NONE
) {
    val isCompleted: Boolean get() = completedAt != null
    val isRecurring: Boolean get() = recurrence != null
}

/**
 * The scheduling columns of a Tasks.org task, as Tasks.org stores them. Completing a repeating
 * task moves its dates (and decrements a `COUNT` rule), so undoing that writes these back.
 * Times are epoch millis, 0 when unset; [recurrence] is empty when the task doesn't repeat.
 */
data class TasksOrgSchedule(
    val dueDate: Long,
    val dueAllDay: Boolean,
    val startDate: Long,
    val startAllDay: Boolean,
    val recurrence: String
)

/** Task priority as the Tasks.org API models it. */
enum class TasksOrgPriority { HIGH, MEDIUM, LOW, NONE }

/** What a repeating task measures its next occurrence from. */
enum class TasksOrgRepeatFrom { DUE_DATE, COMPLETION_DATE }

/**
 * A date to write to Tasks.org. [ms] is epoch millis; [allDay] decides whether it carries a
 * time of day.
 *
 * An all-day date is the calendar day at the device's **local** midnight (Tasks.org's own
 * convention), unlike [TasksOrgTask.startTs], which the reader normalizes to UTC midnight so
 * a task lines up with a Room event in the grid. The write path converts between the two; keep
 * the two conversions apart (report §4.1).
 */
data class TasksOrgDate(val ms: Long, val allDay: Boolean)

/**
 * The fields of a new task. Only [title] is required; Tasks.org fills the rest with its empty
 * values and files the task in the user's default list when [listId] is null. A new task
 * inherits nothing but the list.
 */
data class TasksOrgTaskDraft(
    val title: String,
    val notes: String = "",
    val listId: Long? = null,
    val due: TasksOrgDate? = null,
    val start: TasksOrgDate? = null,
    val priority: TasksOrgPriority = TasksOrgPriority.NONE,
    val recurrence: String? = null,
    val repeatFrom: TasksOrgRepeatFrom = TasksOrgRepeatFrom.DUE_DATE,
    val parentId: Long? = null
)

/**
 * A sparse edit of an existing task, mapping the provider's patch semantics: every property
 * left as [Unset] is not written, so the row keeps it. A present value is written; a present
 * null clears that field (except [title], which Tasks.org refuses to clear).
 *
 * Build one with the `set*` helpers so only the touched fields carry a [Field.Value].
 */
data class TasksOrgTaskPatch(
    val title: Field<String> = Field.Unset,
    val notes: Field<String> = Field.Unset,
    val listId: Field<Long> = Field.Unset,
    /** A null value clears the due date. */
    val due: Field<TasksOrgDate?> = Field.Unset,
    /** A null value clears the start date. */
    val start: Field<TasksOrgDate?> = Field.Unset,
    val priority: Field<TasksOrgPriority> = Field.Unset,
    /** A null value clears the recurrence. */
    val recurrence: Field<String?> = Field.Unset,
    val repeatFrom: Field<TasksOrgRepeatFrom> = Field.Unset,
    val parentId: Field<Long> = Field.Unset
) {
    /** True when nothing is set, so there is nothing to write. */
    val isEmpty: Boolean
        get() = listOf(title, notes, listId, due, start, priority, recurrence, repeatFrom, parentId)
            .all { it is Field.Unset }

    /** A patch field: [Unset] is not written; [Value] is, even when its value is null. */
    sealed interface Field<out T> {
        data object Unset : Field<Nothing>
        data class Value<out T>(val value: T) : Field<T>
    }
}

/** Outcome of a Tasks.org write, so callers can tell a refusal apart from a read-only target. */
sealed interface TasksOrgWriteResult {
    /** The write landed; [taskId] is the affected (or newly created) task. */
    data class Ok(val taskId: Long) : TasksOrgWriteResult

    /** The list, or the task on it, is read-only. */
    data object ReadOnly : TasksOrgWriteResult

    /** No write permission, the task is gone, an unsupported API shape, or an invalid value. */
    data object Refused : TasksOrgWriteResult
}
