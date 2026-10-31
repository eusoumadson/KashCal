package org.onekash.kashcal.data.tasks_org

import androidx.compose.runtime.Immutable

/** A task list of the Tasks.org app. [color] is ARGB, 0 when the list has none. */
@Immutable
data class TasksOrgList(
    val id: Long,
    val title: String,
    val color: Int,
    val isReadOnly: Boolean,
    /** The Tasks.org account the list belongs to; 0 when the provider omits it. */
    val accountId: Long = 0L
)

/** A Tasks.org tag: a named, colored label a task can carry. [color] is ARGB, 0 when unset. */
@Immutable
data class TasksOrgTag(
    val id: Long,
    val name: String,
    val color: Int
)

/**
 * A reminder on a Tasks.org task.
 *
 * Time reminders:
 * - [AtTime] fires at an absolute instant ([triggerAtMs]).
 * - [Relative] fires at a signed [offsetMs] from the task's due or start date ([anchor]);
 *   negative is before. A repeating reminder also carries [repeatCount] and [intervalMs].
 * - [Random] fires about every [periodMs] (at least a minute) after the last one.
 * - [Snooze] quiets the task's other reminders until [triggerAtMs]. Tasks.org creates it when
 *   the user snoozes; the editor can show it and remove it, never add one.
 *
 * [Location] fires on arriving at or leaving a saved place ([placeId]).
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

    @Immutable
    data class Random(override val id: Long = 0L, val periodMs: Long) : TasksOrgReminder

    @Immutable
    data class Snooze(override val id: Long = 0L, val triggerAtMs: Long) : TasksOrgReminder

    @Immutable
    data class Location(override val id: Long = 0L, val placeId: Long, val kind: Kind) : TasksOrgReminder {
        enum class Kind { ARRIVAL, DEPARTURE }
    }
}

/** A saved location of Tasks.org. [displayName] falls back to the address, then coordinates. */
@Immutable
data class TasksOrgPlace(
    val id: Long,
    val name: String,
    val displayName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    /** Trigger radius in meters for location reminders. */
    val radius: Int
)

/** A new place. Tasks.org does no geocoding, so the coordinates are required. */
data class TasksOrgPlaceDraft(
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val radius: Int = DEFAULT_RADIUS_M
) {
    companion object {
        /** Tasks.org's default trigger radius. */
        const val DEFAULT_RADIUS_M = 250
    }
}

/** A Tasks.org sync account. [error] is empty while healthy, else a coarse code. */
@Immutable
data class TasksOrgAccount(
    val id: Long,
    val name: String,
    val type: String,
    val error: String,
    /** True when the server advances recurring tasks itself. */
    val repeatsOnServer: Boolean
) {
    val needsAttention: Boolean get() = error.isNotEmpty()
    val isLocal: Boolean get() = type == TasksOrgContract.Accounts.TYPE_LOCAL
}

/**
 * The installed Tasks.org version. The `v0` API exists from 15.12 and is explicitly unstable, so
 * the app tells the user when the installed version is older than that.
 */
@Immutable
data class TasksOrgVersion(val name: String, val code: Long) {
    /** True when the installed version has the public API ([MIN_SUPPORTED]). */
    val isSupported: Boolean
        get() = parseMajorMinor(name)?.let { compare(it, MIN_SUPPORTED) >= 0 } ?: true

    companion object {
        /** The release that introduced `v0`: 15.12. */
        val MIN_SUPPORTED: Pair<Int, Int> = 15 to 12
        const val MIN_SUPPORTED_NAME = "15.12"

        /** Reads `major.minor` from names like `15.13`, `15.13.0` or `15.13-beta03`; null if unparseable. */
        fun parseMajorMinor(name: String): Pair<Int, Int>? {
            val m = Regex("""^(\d+)\.(\d+)""").find(name.trim()) ?: return null
            return m.groupValues[1].toInt() to m.groupValues[2].toInt()
        }

        private fun compare(a: Pair<Int, Int>, b: Pair<Int, Int>): Int =
            if (a.first != b.first) a.first.compareTo(b.first) else a.second.compareTo(b.second)
    }
}

/** The place and triggers a task's location reminders use; at most one place per task. */
data class TasksOrgLocation(val placeId: Long, val arrival: Boolean, val departure: Boolean) {
    val hasTrigger: Boolean get() = arrival || departure
}

/**
 * What a task carries besides its own columns, saved together with it in one atomic batch.
 *
 * - [tagIds] and [newTagNames] are the task's complete tag set; names are created first.
 * - [reminders] are the complete non-location reminders. A reminder with an id is kept; id 0 is
 *   added; a current reminder missing here is deleted.
 * - [location] is the task's place and triggers; null means no place.
 */
data class TasksOrgTaskExtras(
    val tagIds: Set<Long> = emptySet(),
    val newTagNames: List<String> = emptyList(),
    val reminders: List<TasksOrgReminder> = emptyList(),
    val location: TasksOrgLocation? = null
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
 * Tasks.org move it to the next one. The current occurrence is the task itself; the later ones the
 * rule produces are copies with [isFutureOccurrence] set ([futureOccurrences]), shown dimmed and
 * not completable, since Tasks.org only knows the current one.
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
    val priority: TasksOrgPriority = TasksOrgPriority.NONE,
    /** Names of the task's tags, in Tasks.org's order. */
    val tags: List<String> = emptyList(),
    /** Direct subtasks and, of those, the open ones. Subtasks are managed in Tasks.org. */
    val childCount: Int = 0,
    val uncompletedChildCount: Int = 0,
    /** The task's saved place, 0 when it has none. */
    val placeId: Long = 0L,
    /** When the task is hidden until, or null. */
    val hideUntil: TasksOrgDate? = null,
    val repeatFrom: TasksOrgRepeatFrom = TasksOrgRepeatFrom.DUE_DATE,
    /**
     * True for a later occurrence of a repeating task, computed from its rule. It has the task's
     * [id] but is not a row in Tasks.org: nothing can be written through it.
     */
    val isFutureOccurrence: Boolean = false
) {
    val isCompleted: Boolean get() = completedAt != null
    val isRecurring: Boolean get() = recurrence != null
    val hasSubtasks: Boolean get() = childCount > 0
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
