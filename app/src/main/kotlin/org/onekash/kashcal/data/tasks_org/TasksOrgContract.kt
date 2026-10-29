package org.onekash.kashcal.data.tasks_org

import android.net.Uri

/**
 * The parts of the Tasks.org app's public ContentProvider API (`org.tasks.api`, version v0)
 * that KashCal uses. Tasks.org defines it in `org.tasks.api.TasksContract`; the names here
 * match it.
 *
 * The API filters only through query parameters: a selection, selectionArgs or sortOrder
 * makes the provider throw IllegalArgumentException. Dates are epoch milliseconds. A
 * collection returns 100 rows unless [PARAM_LIMIT] asks for more; KashCal reads pages of
 * [MAX_LIMIT] rows, moving on with [PARAM_OFFSET].
 */
object TasksOrgContract {
    /** Package of the Tasks.org app, Play and F-Droid builds alike. */
    const val PACKAGE = "org.tasks"

    const val AUTHORITY = "org.tasks.api"
    private const val VERSION = "v0"

    /** Runtime permission (dangerous, defined by Tasks.org) to read tasks and lists. */
    const val PERMISSION_READ = "org.tasks.permission.READ_TASKS"

    /** Runtime permission (dangerous, defined by Tasks.org) to update a task. */
    const val PERMISSION_WRITE = "org.tasks.permission.WRITE_TASKS"

    const val MAX_LIMIT = 1000

    const val ID = "_id"
    const val PARAM_LIMIT = "limit"
    const val PARAM_OFFSET = "offset"

    val BASE_URI: Uri = Uri.parse("content://$AUTHORITY/$VERSION")

    object Tasks {
        val CONTENT_URI: Uri = Uri.withAppendedPath(BASE_URI, "tasks")

        const val TITLE = "title"
        const val NOTES = "notes"
        /** Epoch millis; 0 when the task has no due date. */
        const val DUE_DATE = "due_date"
        /** 1 when [DUE_DATE] is a date with no time of day. */
        const val DUE_ALL_DAY = "due_all_day"
        /** Epoch millis the task is hidden until; 0 when unset. */
        const val START_DATE = "start_date"
        /** 1 when [START_DATE] is a date with no time of day. */
        const val START_ALL_DAY = "start_all_day"
        /** Epoch millis; 0 while the task is open. Writing a value above 0 completes it. */
        const val COMPLETED_AT = "completed_at"
        /** RFC 5545 RRULE of a repeating task, or empty. */
        const val RECURRENCE = "recurrence"
        const val LIST_ID = "list_id"
        const val IS_READ_ONLY = "is_read_only"

        /** Tasks due after this instant (exclusive). */
        const val PARAM_DUE_AFTER = "due_after"
        /** Tasks due before this instant (exclusive). */
        const val PARAM_DUE_BEFORE = "due_before"
        /** Free text matched against title and notes. */
        const val PARAM_SEARCH = "search"

        fun itemUri(id: Long): Uri = Uri.withAppendedPath(CONTENT_URI, id.toString())
    }

    object Lists {
        val CONTENT_URI: Uri = Uri.withAppendedPath(BASE_URI, "lists")

        const val TITLE = "title"
        /** ARGB color, 0 when the list has none. */
        const val COLOR = "color"
        const val ORDER = "order"
        /** "owner", "read_write" or "read_only". */
        const val ACCESS = "access"

        const val ACCESS_READ_ONLY = "read_only"
    }
}
