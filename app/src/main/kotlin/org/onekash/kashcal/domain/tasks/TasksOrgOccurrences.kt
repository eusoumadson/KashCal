package org.onekash.kashcal.domain.tasks

import org.onekash.kashcal.data.tasks_org.TasksOrgRepeatFrom
import org.onekash.kashcal.data.tasks_org.TasksOrgTask
import org.onekash.kashcal.data.tasks_org.placementOf
import org.onekash.kashcal.domain.generator.IcalDavRRuleEngine
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Most later occurrences one task contributes to a view, so a daily rule on a year view stays bounded. */
internal const val MAX_FUTURE_OCCURRENCES = 400

/** How far before a view a repeating task may be due and still show the occurrences that reach it. */
internal const val FUTURE_OCCURRENCE_LOOKBACK_MS = 366L * 24 * 60 * 60 * 1000

/**
 * Returns the occurrences of the repeating [task] after its current one that fall in
 * [rangeStartMs]..[rangeEndMs] (inclusive), as copies flagged [TasksOrgTask.isFutureOccurrence].
 *
 * Tasks.org keeps one row per repeating task, due on the current occurrence, and moves it
 * forward when the task is completed. The later dates exist only in the rule, so they are
 * computed here from the current due date. Empty when:
 * - the task doesn't repeat, or is already completed (an account that repeats on the server
 *   leaves it completed with its dates unchanged, so there is no current occurrence to extend);
 * - it repeats from the completion date: the next date then depends on when it is completed, so
 *   a computed one would be wrong;
 * - the rule doesn't parse.
 *
 * A rule with COUNT is expanded as stored: Tasks.org lowers the count at each completion, so what
 * is left is what remains, current occurrence included. At most one occurrence per day and
 * [MAX_FUTURE_OCCURRENCES] per task are returned, so a sub-daily rule can't flood a day.
 */
// FORK:tasks-org — expansion of a repeating task's rule into display-only later occurrences.
internal fun futureOccurrences(
    task: TasksOrgTask,
    rangeStartMs: Long,
    rangeEndMs: Long,
    zone: ZoneId = ZoneId.systemDefault()
): List<TasksOrgTask> {
    if (!task.isRecurring || task.isCompleted || task.isFutureOccurrence) return emptyList()
    if (task.repeatFrom == TasksOrgRepeatFrom.COMPLETION_DATE) return emptyList()
    val rule = normalizeRule(task.recurrence).ifEmpty { return emptyList() }

    // An all-day task expands in UTC from its UTC-midnight start; a timed one in the device zone.
    val timestamps = IcalDavRRuleEngine.expandToTimestamps(
        rrule = rule,
        dtstartMs = task.startTs,
        rangeStartMs = maxOf(rangeStartMs, task.startTs + 1),
        rangeEndMs = rangeEndMs + 1,
        timezone = if (task.isAllDay) null else zone.id,
        isAllDay = task.isAllDay,
        rdateStrings = null,
        exdateStrings = null
    )
    return timestamps
        .asSequence()
        .filter { it > task.startTs }
        .map { occurrenceOf(task, it, zone) }
        .distinctBy { it.startDay }
        .take(MAX_FUTURE_OCCURRENCES)
        .toList()
}

/** Copies [task] to the occurrence at [ts], in the time conventions of [TasksOrgTask]. */
private fun occurrenceOf(task: TasksOrgTask, ts: Long, zone: ZoneId): TasksOrgTask {
    // An expanded all-day date is UTC midnight; the due date Tasks.org stores is local midnight.
    val dueTs = if (task.isAllDay) {
        Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
    } else {
        ts
    }
    val placement = placementOf(dueTs, task.isAllDay, zone)
    return task.copy(
        dueTs = dueTs,
        startTs = placement.startTs,
        endTs = placement.endTs,
        startDay = placement.startDay,
        endDay = placement.endDay,
        completedAt = null,
        hideUntil = null,
        isFutureOccurrence = true
    )
}
