package org.onekash.kashcal.data.tasks_org

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onekash.kashcal.util.DateTimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** [TasksOrgRepository] over the Tasks.org ContentProvider. */
@Singleton
class AndroidTasksOrgRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : TasksOrgRepository {

    companion object {
        private const val TAG = "TasksOrgRepo"
    }

    private val contentResolver get() = context.contentResolver

    // resolveContentProvider rather than the package: an old Tasks.org without the API has
    // nothing to read. Needs the <queries> entry for org.tasks on Android 11+.
    override fun isInstalled(): Boolean =
        context.packageManager.resolveContentProvider(TasksOrgContract.AUTHORITY, 0) != null

    override fun hasReadPermission(): Boolean = granted(TasksOrgContract.PERMISSION_READ)

    override fun hasWritePermission(): Boolean = granted(TasksOrgContract.PERMISSION_WRITE)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun getLists(): List<TasksOrgList> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        readPages(TasksOrgContract.Lists.CONTENT_URI) { cursor ->
            TasksOrgList(
                id = cursor.long(TasksOrgContract.ID),
                title = cursor.string(TasksOrgContract.Lists.TITLE),
                color = cursor.long(TasksOrgContract.Lists.COLOR).toInt(),
                isReadOnly = cursor.string(TasksOrgContract.Lists.ACCESS) ==
                    TasksOrgContract.Lists.ACCESS_READ_ONLY
            )
        }
    }

    override suspend fun getTasksDueInRange(
        startMs: Long,
        endMs: Long,
        hiddenListIds: Set<Long>,
        search: String?
    ): List<TasksOrgTask> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        val lists = getLists().associateBy { it.id }
        // The provider's bounds are exclusive.
        val uri = TasksOrgContract.Tasks.CONTENT_URI.buildUpon()
            .appendQueryParameter(TasksOrgContract.Tasks.PARAM_DUE_AFTER, (startMs - 1).toString())
            .appendQueryParameter(TasksOrgContract.Tasks.PARAM_DUE_BEFORE, (endMs + 1).toString())
            .apply { search?.let { appendQueryParameter(TasksOrgContract.Tasks.PARAM_SEARCH, it) } }
            .build()
        readPages(uri) { cursor -> cursor.toTask(lists) }
            // Offset pages aren't a snapshot: a row that moves during the read can come twice.
            .distinctBy { it.id }
            .filter { it.listId !in hiddenListIds }
            .sortedBy { it.startTs }
    }

    override suspend fun setCompleted(taskId: Long, completedAt: Long?): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission()) return@withContext false
            val values = ContentValues().apply {
                put(TasksOrgContract.Tasks.COMPLETED_AT, completedAt ?: 0L)
            }
            guarded("update task $taskId", false) {
                contentResolver.update(TasksOrgContract.Tasks.itemUri(taskId), values, null, null) > 0
            }
        }

    override suspend fun getSchedule(taskId: Long): TasksOrgSchedule? = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext null
        guarded("read task $taskId", null) {
            contentResolver.query(TasksOrgContract.Tasks.itemUri(taskId), null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                TasksOrgSchedule(
                    dueDate = cursor.long(TasksOrgContract.Tasks.DUE_DATE),
                    dueAllDay = cursor.long(TasksOrgContract.Tasks.DUE_ALL_DAY) != 0L,
                    startDate = cursor.long(TasksOrgContract.Tasks.START_DATE),
                    startAllDay = cursor.long(TasksOrgContract.Tasks.START_ALL_DAY) != 0L,
                    recurrence = cursor.string(TasksOrgContract.Tasks.RECURRENCE)
                )
            }
        }
    }

    override suspend fun restoreSchedule(taskId: Long, schedule: TasksOrgSchedule): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission()) return@withContext false
            // The flags go with their dates: an omitted flag keeps the row's, and an explicit
            // start date stops Tasks.org from moving it along with the due date.
            val values = ContentValues().apply {
                put(TasksOrgContract.Tasks.DUE_DATE, schedule.dueDate)
                put(TasksOrgContract.Tasks.DUE_ALL_DAY, if (schedule.dueAllDay) 1 else 0)
                put(TasksOrgContract.Tasks.START_DATE, schedule.startDate)
                put(TasksOrgContract.Tasks.START_ALL_DAY, if (schedule.startAllDay) 1 else 0)
                put(TasksOrgContract.Tasks.RECURRENCE, schedule.recurrence)
            }
            guarded("restore task $taskId", false) {
                contentResolver.update(TasksOrgContract.Tasks.itemUri(taskId), values, null, null) > 0
            }
        }

    /**
     * Reads every row of the collection [uri], paging by [TasksOrgContract.MAX_LIMIT]; empty
     * when the provider refuses the query.
     */
    private fun <T> readPages(uri: Uri, map: (Cursor) -> T?): List<T> =
        guarded("query $uri", emptyList()) {
            val results = mutableListOf<T>()
            var offset = 0
            while (true) {
                val page = uri.buildUpon()
                    .appendQueryParameter(TasksOrgContract.PARAM_LIMIT, TasksOrgContract.MAX_LIMIT.toString())
                    .appendQueryParameter(TasksOrgContract.PARAM_OFFSET, offset.toString())
                    .build()
                val count = contentResolver.query(page, null, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) map(cursor)?.let(results::add)
                    cursor.count
                } ?: 0
                if (count < TasksOrgContract.MAX_LIMIT) break
                offset += count
            }
            results
        }

    /** Maps a Tasks row to a [TasksOrgTask]; null for a task without a due date. */
    private fun Cursor.toTask(lists: Map<Long, TasksOrgList>): TasksOrgTask? {
        val due = long(TasksOrgContract.Tasks.DUE_DATE)
        if (due <= 0L) return null
        val listId = long(TasksOrgContract.Tasks.LIST_ID)
        val list = lists[listId]
        return taskAt(
            id = long(TasksOrgContract.ID),
            title = string(TasksOrgContract.Tasks.TITLE),
            notes = string(TasksOrgContract.Tasks.NOTES),
            dueTs = due,
            isAllDay = long(TasksOrgContract.Tasks.DUE_ALL_DAY) != 0L,
            completedAt = long(TasksOrgContract.Tasks.COMPLETED_AT).takeIf { it > 0L },
            recurrence = string(TasksOrgContract.Tasks.RECURRENCE).ifBlank { null },
            listId = listId,
            listTitle = list?.title.orEmpty(),
            listColor = list?.color ?: 0,
            isReadOnly = long(TasksOrgContract.Tasks.IS_READ_ONLY) != 0L || list?.isReadOnly == true
        )
    }

    private fun Cursor.long(column: String): Long {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0L else getLong(index)
    }

    private fun Cursor.string(column: String): String {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) "" else getString(index).orEmpty()
    }

    /**
     * Runs [block], returning [fallback] when Tasks.org refuses the call: a revoked permission
     * (SecurityException), a query it doesn't support (IllegalArgumentException), or the app
     * going away mid-call.
     */
    private inline fun <T> guarded(what: String, fallback: T, block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Tasks.org refused $what: ${e.javaClass.simpleName}: ${e.message}")
            fallback
        }
}

/**
 * Builds a [TasksOrgTask] placed at [dueTs] with the Room event time conventions (see
 * [TasksOrgTask]). An all-day due date is read in [zone], where Tasks.org stores it.
 */
internal fun taskAt(
    id: Long,
    title: String,
    notes: String,
    dueTs: Long,
    isAllDay: Boolean,
    completedAt: Long?,
    recurrence: String?,
    listId: Long,
    listTitle: String,
    listColor: Int,
    isReadOnly: Boolean,
    zone: ZoneId = ZoneId.systemDefault()
): TasksOrgTask {
    val (startTs, endTs) = if (isAllDay) {
        val dayStart = Instant.ofEpochMilli(dueTs).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        dayStart to dayStart + 24L * 60 * 60 * 1000 - 1
    } else {
        dueTs to dueTs
    }
    return TasksOrgTask(
        id = id,
        title = title,
        notes = notes,
        dueTs = dueTs,
        isAllDay = isAllDay,
        startTs = startTs,
        endTs = endTs,
        startDay = DateTimeUtils.eventTsToDayCode(startTs, isAllDay, zone),
        endDay = DateTimeUtils.eventTsToEndDayCode(endTs = endTs, startTs = startTs, isAllDay = isAllDay, localZone = zone),
        completedAt = completedAt,
        recurrence = recurrence,
        listId = listId,
        listTitle = listTitle,
        listColor = listColor,
        isReadOnly = isReadOnly
    )
}
