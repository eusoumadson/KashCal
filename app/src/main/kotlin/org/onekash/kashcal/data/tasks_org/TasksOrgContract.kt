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
        /** What a repeating task counts the next occurrence from: [REPEAT_FROM_DUE_DATE] or [REPEAT_FROM_COMPLETION]. */
        const val REPEAT_FROM = "repeat_from"
        /** `high`, `medium`, `low`, `none`. */
        const val PRIORITY = "priority"
        const val LIST_ID = "list_id"
        const val PARENT_ID = "parent_id"
        const val IS_READ_ONLY = "is_read_only"
        /** Comma-joined tag ids on the task; "" when it has none. Read-only. */
        const val TAG_IDS = "tag_ids"
        /** The task's saved place, 0 when it has none. Writing 0 removes the place and its reminders. */
        const val PLACE_ID = "place_id"
        /** Direct subtasks. Read-only. */
        const val CHILD_COUNT = "child_count"
        /** Of the direct subtasks, the ones not completed. Read-only. */
        const val UNCOMPLETED_CHILD_COUNT = "uncompleted_child_count"

        const val REPEAT_FROM_DUE_DATE = "due_date"
        const val REPEAT_FROM_COMPLETION = "completion_date"

        const val PRIORITY_HIGH = "high"
        const val PRIORITY_MEDIUM = "medium"
        const val PRIORITY_LOW = "low"
        const val PRIORITY_NONE = "none"

        /** Tasks due after this instant (exclusive). */
        const val PARAM_DUE_AFTER = "due_after"
        /** Tasks due before this instant (exclusive). */
        const val PARAM_DUE_BEFORE = "due_before"
        /** Free text matched against title and notes. */
        const val PARAM_SEARCH = "search"

        fun itemUri(id: Long): Uri = Uri.withAppendedPath(CONTENT_URI, id.toString())

        /**
         * An `ACTION_VIEW` intent on this item URI opens the task's edit screen in Tasks.org.
         * A deep-link fallback for editing beyond what KashCal's own editor covers.
         */
        fun viewUri(id: Long): Uri = itemUri(id)
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

        /** The account a list belongs to; required, and writable only on insert. */
        const val ACCOUNT_ID = "account_id"

        fun itemUri(id: Long): Uri = Uri.withAppendedPath(CONTENT_URI, id.toString())
    }

    /** Saved locations that a task can be filed under and location reminders trigger on. */
    object Places {
        val CONTENT_URI: Uri = Uri.withAppendedPath(BASE_URI, "places")

        const val NAME = "name"
        /** Name, falling back to the address, then the coordinates. Read-only. */
        const val DISPLAY_NAME = "display_name"
        const val ADDRESS = "address"
        /** Decimal degrees; required on insert and fixed afterwards. */
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        /** Trigger radius in meters for location reminders; must be positive. */
        const val RADIUS = "radius"

        fun itemUri(id: Long): Uri = Uri.withAppendedPath(CONTENT_URI, id.toString())
    }

    /** Sync accounts of the Tasks.org app. Read-only. */
    object Accounts {
        val CONTENT_URI: Uri = Uri.withAppendedPath(BASE_URI, "accounts")

        const val NAME = "name"
        /** `caldav`, `tasks_org`, `google_tasks`, `microsoft`, `etebase`, `opentasks` or `local`. */
        const val TYPE = "type"
        /** Why the last sync failed; empty when healthy. */
        const val ERROR = "error"
        /** 1 when the server advances recurring tasks itself. */
        const val REPEATS_ON_SERVER = "repeats_on_server"

        const val TYPE_LOCAL = "local"
    }

    /** Tags: named, colored labels a task can carry ([TaskTags] joins them to tasks). */
    object Tags {
        val CONTENT_URI: Uri = Uri.withAppendedPath(BASE_URI, "tags")

        const val NAME = "name"
        /** ARGB color, 0 when unset. */
        const val COLOR = "color"

        fun itemUri(id: Long): Uri = Uri.withAppendedPath(CONTENT_URI, id.toString())
    }

    /**
     * The join between tasks and tags. A task's current tags are read off its `tag_ids`
     * column; this collection is for adding and removing them.
     */
    object TaskTags {
        val CONTENT_URI: Uri = Uri.withAppendedPath(BASE_URI, "task_tags")

        const val TASK_ID = "task_id"
        const val TAG_ID = "tag_id"

        /** The URI that removes one (task, tag) pair by its natural key. */
        fun deleteUri(taskId: Long, tagId: Long): Uri = CONTENT_URI.buildUpon()
            .appendQueryParameter(TASK_ID, taskId.toString())
            .appendQueryParameter(TAG_ID, tagId.toString())
            .build()
    }

    /**
     * Reminders on a task: time types ([TYPE_DATE_TIME], [TYPE_RELATIVE_DUE],
     * [TYPE_RELATIVE_START], [TYPE_RANDOM], [TYPE_SNOOZE]) and location types
     * ([TYPE_LOCATION_ARRIVAL], [TYPE_LOCATION_DEPARTURE], which need a place). Sending a column
     * that doesn't apply to the type throws IllegalArgumentException.
     */
    object Reminders {
        val CONTENT_URI: Uri = Uri.withAppendedPath(BASE_URI, "reminders")

        const val TASK_ID = "task_id"
        const val TYPE = "type"
        /** Absolute trigger time (epoch millis); `date_time` and `snooze` only. */
        const val TRIGGER_AT = "trigger_at"
        /** Signed offset from the due/start date; negative is *before*. Relative/random only. */
        const val OFFSET_MS = "offset_ms"
        const val REPEAT_COUNT = "repeat_count"
        const val INTERVAL_MS = "interval_ms"
        const val PLACE_ID = "place_id"

        const val TYPE_DATE_TIME = "date_time"
        const val TYPE_RELATIVE_DUE = "relative_due"
        const val TYPE_RELATIVE_START = "relative_start"
        const val TYPE_SNOOZE = "snooze"
        const val TYPE_RANDOM = "random"
        const val TYPE_LOCATION_ARRIVAL = "location_arrival"
        const val TYPE_LOCATION_DEPARTURE = "location_departure"

        /** Only query key supported here: the task whose reminders to read. Repeatable. */
        fun forTask(taskId: Long): Uri = CONTENT_URI.buildUpon()
            .appendQueryParameter(TASK_ID, taskId.toString())
            .build()

        fun itemUri(id: Long): Uri = Uri.withAppendedPath(CONTENT_URI, id.toString())
    }
}
